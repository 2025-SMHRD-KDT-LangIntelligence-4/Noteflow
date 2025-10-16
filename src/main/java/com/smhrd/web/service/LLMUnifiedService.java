package com.smhrd.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM 통합 서비스
 *
 * 1) RAW 마크다운 실행: DB 프롬프트(제목) + 원문을 그대로 LLM에 먹여 마크다운 전문(String)을 반환 2) 요약 JSON
 * 모드: summary + keywords(5) + category(대/중/소) + tags JSON을 받아 자동 분류/태깅에 활용 3)
 * 챗봇 간단 응답: 컨텍스트 프롬프트를 그대로 전달해 짧은 답 생성 4) 로컬 카테고리 매칭/폴더 생성 보조 메서드 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LLMUnifiedService {

	// ====== Infra ======
	private final WebClient vllmWebClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	// ====== Domain Repositories ======
	private final PromptRepository promptRepository;
	private final UserRepository userRepository;
	private final NoteRepository noteRepository;
	private final NoteFolderRepository noteFolderRepository;
	private final TagRepository tagRepository;
	private final NoteTagRepository noteTagRepository;
	private final CategoryHierarchyRepository categoryHierarchyRepository;
	private final TestSummaryRepository testSummaryRepository;
	private final FileParseService fileParseService;

	// ====== LLM Settings ======
	@Value("${vllm.api.model}")
	private String modelName;

	@Value("${vllm.api.max-tokens}")
	private int maxTokens;

	@Value("${vllm.api.temperature}")
	private double temperature;

	@Value("${vllm.api.context-limit:30000}")
	private int contextLimit;

	// ====== 파싱 ======
	private static final int TOKENS_MAX = 20000; // 모델 컨텍스트 상한(대략)
	private static final int TOKENS_ECONOMY_START = 12000; // 경제 모드 시작
	private static final int TOKENS_BLOCK = 18000; // 차단 모드 상한

	private static final int NORMAL_MAX_BYTES = 50 * 1024; // 50KB
	private static final int ECONOMY_MAX_BYTES = 500 * 1024; // 500KB

	private String fixFences(String md) {
		if (md == null)
			return "";
		String s = md.trim();
		int count = 0;
		int idx = 0;
		while ((idx = s.indexOf("```", idx)) != -1) {
			count++;
			idx += 3;
		}
		if ((count % 2) != 0) {
			s += "\n```";
		}
		return s;
	}

	// =====================================================================
	// A. RAW 마크다운 실행
	// =====================================================================
	public String runPromptMarkdown(long userIdx, String promptTitle, String original) throws Exception {
		userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userIdx));

		String promptText = promptRepository.findByTitle(promptTitle)
				.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle)).getContent();

		String fullPrompt = promptText + "\n\n" + (original == null ? "" : original);
		int safeMax = computeSafeMaxTokens(fullPrompt);

		Map<String, Object> chatReq = new HashMap<>();
		chatReq.put("model", modelName);
		chatReq.put("max_tokens", safeMax);
		chatReq.put("temperature", temperature);
		chatReq.put("messages", List.of(Map.of("role", "user", "content", fullPrompt)));

		try {
			String resp = vllmWebClient.post().uri("/v1/chat/completions").bodyValue(chatReq).retrieve()
					.bodyToMono(String.class).block();
			if (resp != null && resp.trim().startsWith("#")) {
				log.warn("🔖 vLLM 로그 응답 감지, 원본 그대로 반환");
				return resp;
			}

			String md = extractAnyContent(resp);
			return fixFences(md);
		} catch (Exception chatFail) {
			log.warn("chat.completions 실패, text로 폴백: {}", chatFail.getMessage());

			Map<String, Object> textReq = new HashMap<>();
			textReq.put("model", modelName);
			textReq.put("max_tokens", safeMax);
			textReq.put("temperature", temperature);
			textReq.put("prompt", fullPrompt);

			String resp = vllmWebClient.post().uri("/v1/completions").bodyValue(textReq).retrieve()
					.bodyToMono(String.class).block();

			String md = extractAnyContent(resp);
			return fixFences(md);
		}
	}

	// =====================================================================
	// B. 요약 JSON 모드
	// =====================================================================
	public UnifiedResult summarizeText(long userIdx, String content, String promptTitle) {
		userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userIdx));

		String promptText = promptRepository.findByTitle(promptTitle)
				.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle)).getContent();

		return callUnifiedLLM(promptText, trimForTokens(content));
	}

	public UnifiedResult summarizeFile(long userIdx, MultipartFile file, String promptTitle) {
		userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userIdx));
		String promptText = promptRepository.findByTitle(promptTitle)
				.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle)).getContent();
		try {
			// ✅ 파일은 반드시 포맷별 파서를 통해 텍스트 추출
			String text = fileParseService.extractText(file);
			return callUnifiedLLM(promptText, trimForTokens(text));
		} catch (Exception e) {
			throw new RuntimeException("파일 텍스트 변환 실패: " + e.getMessage(), e);
		}
	}

	// =====================================================================
	// C. Chat 전용
	// =====================================================================
	public String generateResponse(String contextualPrompt) {
		try {
			Map<String, Object> req = new HashMap<>();
			req.put("model", modelName);
			req.put("max_tokens", Math.min(maxTokens, 800));
			req.put("temperature", 1.0);
			req.put("stream", false);

			String system = "You are a helpful assistant. Answer in Korean";
			List<Map<String, String>> messages = List.of(Map.of("role", "system", "content", system),
					Map.of("role", "user", "content", contextualPrompt));
			req.put("messages", messages);

			String response = vllmWebClient.post().uri("/v1/chat/completions").bodyValue(req).retrieve()
					.bodyToMono(String.class).block();

			return extractAnyContent(response);
		} catch (Exception e) {
			return "잠시 후 다시 시도해주세요. (" + e.getMessage() + ")";
		}
	}

	// =====================================================================
	// D. LLM 호출 (Unified JSON)
	// =====================================================================
	private UnifiedResult callUnifiedLLM(String promptText, String original) {
		try {
			String fullPrompt = promptText + "\n\n" + original + "\n\n" + "아래 JSON 스키마로만 출력하세요. 추가 텍스트/설명은 절대 금지.\n"
					+ "{\n" + "  \"summary\": string,\n" + "  \"keywords\": string[5],\n"
					+ "  \"category\": { \"large\": string, \"medium\": string, \"small\": string },\n"
					+ "  \"tags\": string[]\n" + "}\n";

			int safeMax = computeSafeMaxTokens(fullPrompt);

			Map<String, Object> chatReq = new HashMap<>();
			chatReq.put("model", modelName);
			chatReq.put("max_tokens", safeMax);
			chatReq.put("temperature", temperature);
			chatReq.put("messages", List.of(Map.of("role", "user", "content", fullPrompt)));

			String response;
			String jsonText;

			try {
				response = vllmWebClient.post().uri("/v1/chat/completions").bodyValue(chatReq).retrieve()
						.bodyToMono(String.class).block();
				jsonText = extractAnyContent(response);
			} catch (RuntimeException chatErr) {
				log.warn("chat.completions 실패: {}", chatErr.getMessage());

				Map<String, Object> textReq = new HashMap<>();
				textReq.put("model", modelName);
				textReq.put("max_tokens", safeMax);
				textReq.put("temperature", temperature);
				textReq.put("prompt", fullPrompt);

				response = vllmWebClient.post().uri("/v1/completions").bodyValue(textReq).retrieve()
						.bodyToMono(String.class).block();
				jsonText = extractAnyContent(response);
			}

			jsonText = stripFence(jsonText);

			JsonNode root = objectMapper.readTree(jsonText);
			UnifiedResult r = new UnifiedResult();
			r.setSummary(root.path("summary").asText(""));
			r.setKeywords(readArray(root.path("keywords")));

			JsonNode cat = root.path("category");
			r.setCategory(new CategoryPath(cat.path("large").asText("기타"), cat.path("medium").asText("미분류"),
					cat.path("small").asText("일반")));
			r.setTags(readArray(root.path("tags")));
			return r;

		} catch (Exception e) {
			throw new RuntimeException("LLM 통합 호출 실패: " + e.getMessage(), e);
		}
	}

	private String extractAnyContent(String response) throws Exception {
		JsonNode root = objectMapper.readTree(response);
		JsonNode choices = root.path("choices");
		if (choices.isArray() && choices.size() > 0) {
			String c = choices.get(0).path("message").path("content").asText(null);
			if (c != null && !c.isBlank())
				return c;

			c = choices.get(0).path("text").asText(null);
			if (c != null && !c.isBlank())
				return c;
		}
		String out = root.path("output_text").asText(null);
		if (out != null && !out.isBlank())
			return out;
		return response;
	}

	private String stripFence(String s) {
		if (s == null)
			return "";
		s = s.trim();
		if (s.startsWith("```")) {
			int first = s.indexOf('\n');
			int last = s.lastIndexOf("```");
			if (first > 0 && last > first) {
				return s.substring(first + 1, last).trim();
			}
		}
		return s;
	}

	private List<String> readArray(JsonNode arr) {
		List<String> list = new ArrayList<>();
		if (arr != null && arr.isArray()) {
			arr.forEach(n -> list.add(n.asText("")));
		}
		return list;
	}

	private int computeSafeMaxTokens(String fullPrompt) {
		int inputTok = estimateTokens(fullPrompt);
		int buffer = Math.max(256, (int) (contextLimit * 0.1));
		int avail = Math.max(0, contextLimit - inputTok - buffer);
		int safe = Math.max(256, Math.min(maxTokens, avail));
		log.info("[LLM] ctxLimit={}, input≈{}, buffer={}, safeMax={}", contextLimit, inputTok, buffer, safe);
		return safe;
	}

	private String trimForTokens(String content) {
		if (content == null)
			return "";
		int MAX_CHARS = 4000;
		return content.length() > MAX_CHARS ? content.substring(0, MAX_CHARS) + "\n\n...(truncated)" : content;
	}

	// =====================================================================
	// F. Category 매칭 & 폴더 경로
	// =====================================================================
	public CategoryPath matchCategory(List<String> keywords, CategoryPath llmCategory) {
		if (keywords == null)
			keywords = List.of();
		Set<String> keyset = keywords.stream().filter(k -> k != null && !k.isBlank()).map(String::toLowerCase)
				.collect(Collectors.toSet());

		List<CategoryHierarchy> all = categoryHierarchyRepository.findAll();
		int bestScore = -1;
		CategoryHierarchy best = null;

		for (CategoryHierarchy c : all) {
			int score = scoreCategory(keyset, c.getKeywords());
			if (score > bestScore) {
				bestScore = score;
				best = c;
			}
		}

		if (best != null && bestScore >= 20) {
			return new CategoryPath(best.getLargeCategory(), best.getMediumCategory(), best.getSmallCategory());
		}
		return llmCategory != null ? llmCategory : new CategoryPath("기타", "미분류", "일반");
	}

	public Long ensureNoteFolderPath(long userIdx, CategoryPath path) {
		if (path == null)
			return null;
		Long parentId = null;
		parentId = findOrCreate(userIdx, parentId, path.getLarge());
		parentId = findOrCreate(userIdx, parentId, path.getMedium());
		parentId = findOrCreate(userIdx, parentId, path.getSmall());
		return parentId;
	}

	private Long findOrCreate(long userIdx, Long parentId, String name) {
		if (name == null || name.isBlank())
			return parentId;

		List<NoteFolder> siblings = (parentId == null)
				? noteFolderRepository.findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(userIdx)
				: noteFolderRepository.findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userIdx, parentId);

		Optional<NoteFolder> found = siblings.stream().filter(f -> f.getFolderName().equals(name)).findFirst();
		if (found.isPresent())
			return found.get().getFolderId();

		NoteFolder folder = NoteFolder.builder().userIdx(userIdx).folderName(name).parentFolderId(parentId)
				.createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

		return noteFolderRepository.save(folder).getFolderId();
	}

	private int scoreCategory(Set<String> keys, String categoryKeywordsCsv) {
		if (categoryKeywordsCsv == null || categoryKeywordsCsv.isBlank())
			return 0;
		Set<String> cat = Arrays.stream(categoryKeywordsCsv.toLowerCase().split(",")).map(String::trim)
				.filter(s -> !s.isBlank()).collect(Collectors.toSet());
		int score = 0;
		for (String k : keys) {
			for (String ck : cat) {
				if (k.contains(ck) || ck.contains(k))
					score += 10;
				else if (isSimilar(k, ck))
					score += 5;
			}
		}
		return score;
	}

	private boolean isSimilar(String a, String b) {
		if (a.length() < 3 || b.length() < 3)
			return false;
		int d = editDistance(a, b);
		int m = Math.max(a.length(), b.length());
		return (double) d / m < 0.3;
	}

	private int editDistance(String a, String b) {
		int[][] dp = new int[a.length() + 1][b.length() + 1];
		for (int i = 0; i <= a.length(); i++) {
			for (int j = 0; j <= b.length(); j++) {
				if (i == 0)
					dp[i][j] = j;
				else if (j == 0)
					dp[i][j] = i;
				else if (a.charAt(i - 1) == b.charAt(j - 1))
					dp[i][j] = dp[i - 1][j - 1];
				else
					dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
			}
		}
		return dp[a.length()][b.length()];
	}

	// 정책 지정형 summarize (모드 힌트 normal/economy)
	public SummaryResult summarizeWithPolicy(long userIdx, String promptTitle, String original, String modeHint)
			throws Exception {
		String compact = compactText(original);
		int bytes = compact.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

		if (bytes > ECONOMY_MAX_BYTES) {
			return SummaryResult.blocked("[안내] 텍스트가 너무 길어 요약을 진행하지 않습니다. 파일을 나누거나 텍스트를 줄여 주세요.");
		}
		if ("normal".equalsIgnoreCase(modeHint) || bytes <= NORMAL_MAX_BYTES) {
			String md = runPromptMarkdown(userIdx, promptTitle, compact);
			return SummaryResult.normal(md);
		}
		// economy: 앞/중간/뒤 샘플 + 키워드 (기존 economy 로직 재사용)
		List<String> keywords = extractTopKeywords(compact, 80);
		String head = sliceChars(compact, 0, 8000);
		String mid = sliceChars(compact, Math.max(0, compact.length() / 2 - 4000), 8000);
		String tail = sliceChars(compact, Math.max(0, compact.length() - 8000), 8000);
		String economyPrompt = """
				아래 키워드와 샘플 텍스트(앞/중간/뒤 일부)에 기반해 문서의 핵심을 체계적으로 요약하세요.
				- 키워드: %s
				- 샘플(앞): %s
				- 샘플(중간): %s
				- 샘플(뒤): %s
				출력 규칙:
				1) 제목 1줄
				2) 핵심 요약(불릿) 8~12개
				3) 추가 참고 또는 누락 위험 요소 3~5개
				""".formatted(String.join(", ", keywords), head, mid, tail);
		String md = runPromptMarkdown(userIdx, promptTitle, economyPrompt);
		return SummaryResult.economy(md, keywords);
	}

	// =====================================================================
	// G. DTOs
	// =====================================================================
	@Data
	public static class UnifiedResult {
		private String summary;
		private List<String> keywords;
		private CategoryPath category;
		private List<String> tags;
	}

	@Data
	public static class CategoryPath {
		private final String large;
		private final String medium;
		private final String small;
	}

	// -----------------------------------------------------
	// ✅ NotionController 호환용: 텍스트 3인자 버전
	// -----------------------------------------------------
	public TestSummary processText(long userIdx, String content, String promptTitle) {
		long start = System.currentTimeMillis();
		try {
			String markdown = runPromptMarkdown(userIdx, promptTitle, content);
			return TestSummary.builder().testType("TEXT").promptTitle(promptTitle).originalContent(content)
					.aiSummary(markdown).status("SUCCESS").processingTimeMs(System.currentTimeMillis() - start).build();
		} catch (Exception e) {
			return TestSummary.builder().testType("TEXT").promptTitle(promptTitle).originalContent(content)
					.status("FAILED").errorMessage(e.getMessage()).processingTimeMs(System.currentTimeMillis() - start)
					.build();
		}
	}

	public TestSummary processText(long userIdx, String content) {
		return processText(userIdx, content, "심플버전");
	}

	// -----------------------------------------------------
	// ✅ NotionController 호환용: 파일 3인자 버전
	// -----------------------------------------------------

	public TestSummary processFile(long userIdx, MultipartFile file, String promptTitle) {
		long start = System.currentTimeMillis();
		try {
			// ✅ 포맷별 파서 사용
			String text = fileParseService.extractText(file);
			String markdown = runPromptMarkdown(userIdx, promptTitle, text);
			return TestSummary.builder().testType("FILE").promptTitle(promptTitle).fileName(file.getOriginalFilename())
					.fileSize(file.getSize()).aiSummary(markdown).status("SUCCESS")
					.processingTimeMs(System.currentTimeMillis() - start).build();
		} catch (Exception e) {
			return TestSummary.builder().testType("FILE").promptTitle(promptTitle)
					.fileName(file != null ? file.getOriginalFilename() : null).status("FAILED")
					.errorMessage(e.getMessage()).processingTimeMs(System.currentTimeMillis() - start).build();
		}
	}

	// ───── 유틸: 토큰 추정 ─────
	public int estimateTokens(String text) {
		if (text == null)
			return 0;
		// 보수적 근사: 문자수/3.5
		int chars = text.length();
		return (int) Math.ceil(chars / 3.5);
	}

	// ───── 유틸: 텍스트 압축(공백/중복/긴 줄 축약) ─────
	public String compactText(String s) {
		if (s == null)
			return "";
		// 공백/개행 정리
		s = s.replace("\r\n", "\n").replace("\r", "\n").replaceAll("[ \\t\\u00A0\\u200B]+", " ");
		// 중복 라인 제거(상위 50,000 라인까지)
		Set<String> seen = new LinkedHashSet<>();
		for (String line : s.split("\\n")) {
			String t = line.trim();
			if (!t.isEmpty())
				seen.add(t);
			if (seen.size() > 50000)
				break;
		}
		String compact = String.join("\n", seen);
		// 너무 긴 기호/코드 줄 축약
		compact = compact.replaceAll("([=\\-_*#]{8,})", "$1".substring(0, 8));
		// 과도한 빈 줄 축약
		compact = compact.replaceAll("\\n{3,}", "\n\n").trim();
		return compact;
	}

	// ───── 유틸: 키워드 추출(가벼운 TF) ─────
	public List<String> extractTopKeywords(String text, int topN) {
		if (text == null || text.isBlank())
			return List.of();
		Map<String, Integer> freq = new HashMap<>();
		// 간단 토큰화: 한글/영문/숫자만 추출, 2자 이상
		String[] tokens = text.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-zA-Z가-힣\\s]", " ").split("\\s+");
		for (String tok : tokens) {
			if (tok.length() < 2)
				continue;
			// 흔한 불용어 일부 제거(확장 가능)
			if (List.of("the", "and", "for", "with", "from", "that", "이것", "저것", "그리고", "그러나").contains(tok))
				continue;
			freq.merge(tok, 1, Integer::sum);
		}
		return freq.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(topN)
				.map(Map.Entry::getKey).toList();
	}

	// ───── 요약 엔드포인트(예: /notion/create-text)에서 분기 사용 ─────

	public SummaryResult summarizeWithPolicy(long userIdx, String promptTitle, String original) throws Exception {
		String compact = compactText(original);
		int bytes = compact.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
		if (bytes > ECONOMY_MAX_BYTES) {
			return SummaryResult.blocked("[안내] 텍스트가 너무 길어 요약을 진행하지 않습니다. 파일을 나누거나 텍스트를 줄여 주세요.");
		}
		if (bytes <= NORMAL_MAX_BYTES) {
			String md = runPromptMarkdown(userIdx, promptTitle, compact);
			return SummaryResult.normal(md);
		}
		// economy 흐름
		List<String> keywords = extractTopKeywords(compact, 80);
		String head = sliceChars(compact, 0, 8000);
		String mid = sliceChars(compact, Math.max(0, compact.length() / 2 - 4000), 8000);
		String tail = sliceChars(compact, Math.max(0, compact.length() - 8000), 8000);
		String economyPrompt = """
				아래 키워드와 샘플 텍스트(앞/중간/뒤 일부)에 기반해 문서의 핵심을 체계적으로 요약하세요.
				- 키워드: %s
				- 샘플(앞): %s
				- 샘플(중간): %s
				- 샘플(뒤): %s
				출력 규칙:
				1) 제목 1줄
				2) 핵심 요약(불릿) 8~12개
				3) 추가 참고 또는 누락 위험 요소 3~5개
				""".formatted(String.join(", ", keywords), head, mid, tail);
		String md = runPromptMarkdown(userIdx, promptTitle, economyPrompt);
		return SummaryResult.economy(md, keywords);
	}

	private String sliceChars(String s, int start, int maxLen) {
		int st = Math.max(0, Math.min(start, s.length()));
		int en = Math.min(s.length(), st + Math.max(0, maxLen));
		return s.substring(st, en);
	}

	// ───── 응답 모델(간단 DTO) ─────
	@Data
	public static class SummaryResult {
		private boolean success;
		private String mode; // "normal" | "economy" | "blocked"
		private String summaryMarkdown;
		private List<String> keywords;
		private String message;

		public static SummaryResult normal(String md) {
			SummaryResult r = new SummaryResult();
			r.success = true;
			r.mode = "normal";
			r.summaryMarkdown = md;
			r.keywords = List.of();
			r.message = "OK";
			return r;
		}

		public static SummaryResult economy(String md, List<String> keywords) {
			SummaryResult r = new SummaryResult();
			r.success = true;
			r.mode = "economy";
			r.summaryMarkdown = md;
			r.keywords = keywords;
			r.message = "파일 크기가 커서 주요 단어만 필터링하여 LLM 모델을 진행합니다.";
			return r;
		}

		public static SummaryResult blocked(String msg) {
			SummaryResult r = new SummaryResult();
			r.success = false;
			r.mode = "blocked";
			r.summaryMarkdown = "";
			r.keywords = List.of();
			r.message = msg;
			return r;
		}
	}

}
