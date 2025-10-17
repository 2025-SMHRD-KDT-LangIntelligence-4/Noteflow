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

	// ====== 길이차이 지정 ======

	private enum Tier {
		MICRO, SHORT, FULL
	}

	// 입력 길이로 티어 자동 결정
	private Tier chooseTier(String original) {
		int len = (original == null) ? 0 : original.strip().length();
		if (len <= 40)
			return Tier.MICRO; // 한두 문장
		if (len <= 150)
			return Tier.SHORT; // 짧은 단락
		return Tier.FULL; // 충분한 본문
	}

	// 티어별 max_tokens 정책
	private int tierMaxTokens(Tier tier, String fullPromptForBudgeting) {
		return switch (tier) {
		case MICRO -> 200; // 안전 기본치: 과도한 생성 방지
		case SHORT -> 600; // 요청사항: 600 토큰 고정
		case FULL -> computeSafeMaxTokens(fullPromptForBudgeting); // 사실상 제한 없음(컨텍스트 한도만 반영)
		};
	}

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
	// 0. 프롬프트 공통규칙 추가
	// =====================================================================

	private String buildCommonSystem(String instruction, Tier tier) {
		String lengthHint = switch (tier) {
		case MICRO -> "출력은 아주 간결하게(3문장 이내).";
		case SHORT -> "출력은 간결하게(6문장, 600토큰 내).";
		case FULL -> "필요 시 섹션을 채우되, 입력에 근거해 작성.";
		};
		return """
				너는 입력 텍스트를 가공하는 도우미다.

				[공통 규칙]
				- 오직 <CONTENT> 태그 사이의 텍스트만 사용한다.
				- 입력에 없는 사건/코드/결론/감정/팀 활동 등은 상상·추가하지 않는다(창작 금지).
				- 프롬프트가 요구하는 섹션이 있어도, 근거가 없으면 '없음' 또는 생략한다.
				- 과도한 수사는 피하고, 사실/근거 기반으로만 작성한다.
				- %s

				[참고 지시문]
				%s
				""".formatted(lengthHint, instruction == null ? "" : instruction);
	}
	
	// 전체 정책 결정
	public SummaryResult summarizeWithGlobalPolicy(long userIdx, String promptTitle, String original) throws Exception {
	    // 1) 티어 결정
	    Tier tier = chooseTier(original);

	    // 2) 공통 system 메시지 + 예산 산정
	    String instruction = promptRepository.findByTitle(promptTitle)
	            .orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle))
	            .getContent();

	    String systemMsg = buildCommonSystem(instruction, tier);
	    String userMsgForBudget = "<CONTENT>\n" + (original == null ? "" : original.strip()) + "\n</CONTENT>";
	    int budget = tierMaxTokens(tier, systemMsg + "\n" + userMsgForBudget);

	    // 3) 오버로드 호출 (온도 낮춰 창작 억제)
	    String md = runPromptMarkdown(userIdx, promptTitle, original, systemMsg, budget, 0.2);

	    // 4) 사후 트림(선택)
	    if (tier == Tier.MICRO && md.length() > 400) {
	        md = md.substring(0, 400) + "\n\n...(truncated by micro policy)";
	    }
	    if (tier == Tier.SHORT && md.length() > 1200) {
	        md = md.substring(0, 1200) + "\n\n...(truncated by short policy)";
	    }

	    SummaryResult r = SummaryResult.normal(md);
	    r.setMode("global-" + tier.name().toLowerCase()); // global-micro|short|full
	    return r;
	}

	// =====================================================================
	// A. RAW 마크다운 실행
	// =====================================================================

	public String runPromptMarkdown(long userIdx, String promptTitle, String original) throws Exception {
		// 1) 사용자 존재 확인
		userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userIdx));

		// 2) 프롬프트 텍스트 로딩(지시문)
		String instruction = promptRepository.findByTitle(promptTitle)
				.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle)).getContent();

		// 3) 요약 범위 고정 규칙(system)
		String systemMsg = """
				너는 아래 규칙을 엄격히 따른다.
				- 오직 <CONTENT> 태그 사이의 텍스트만 요약 대상이다.
				- 그 외의 텍스트(이 지시문 포함)는 요약 대상이 아니다.
				- 출력은 Markdown으로 하되, 원문에 없는 정보/추정은 금지한다.
				--------
				지시문:
				%s
				""".formatted(instruction);

		// 4) 사용자 원문을 콘텐츠 태그로 감싸서 user 메시지로
		String userMsg = "<CONTENT>\n" + (original == null ? "" : original) + "\n</CONTENT>";

		// 5) vLLM 요청
		Map<String, Object> req = new HashMap<>();
		req.put("model", modelName);
		req.put("max_tokens", computeSafeMaxTokens(userMsg)); // 사용자 원문 기준으로 안전 토큰
		req.put("temperature", temperature);
		req.put("stream", false);
		req.put("messages",
				List.of(Map.of("role", "system", "content", systemMsg), Map.of("role", "user", "content", userMsg)));

		String resp = vllmWebClient.post().uri("/v1/chat/completions").bodyValue(req).retrieve()
				.bodyToMono(String.class).block();

		String md = extractAnyContent(resp); // choices[0].message.content || text || output_text
		return fixFences(md);
	}

	// =====================================================================
	// A-1. 마크다운 오버라이드 실행
	// =====================================================================

	public String runPromptMarkdown(long userIdx, String promptTitle, String original, String systemMsgOverride, // null이면
																													// 기존
																													// system
																													// 로직
																													// 사용
			Integer maxTokensOverride, // null이면 기존 computeSafeMaxTokens(userMsg)
			Double temperatureOverride // null이면 기존 temperature 필드
	) throws Exception {

		// 1) 사용자 검사
		userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userIdx));

		// 2) 프롬프트 로딩
		String instruction = promptRepository.findByTitle(promptTitle)
				.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle)).getContent();

		// 3) system 메시지: override가 있으면 사용, 없으면 기존 규칙 사용
		String defaultSystem = """
				너는 아래 규칙을 엄격히 따른다.
				- 오직 <CONTENT> 태그 사이의 텍스트만 요약 대상이다.
				- 그 외의 텍스트(이 지시문 포함)는 요약 대상이 아니다.
				- 출력은 Markdown으로 하되, 원문에 없는 정보/추정은 금지한다.
				--------
				지시문:
				%s
				""".formatted(instruction);
		String systemMsg = (systemMsgOverride != null && !systemMsgOverride.isBlank()) ? systemMsgOverride
				: defaultSystem;

		// 4) user 메시지(요약 대상 고정)
		String userMsg = "<CONTENT>\n" + (original == null ? "" : original) + "\n</CONTENT>";

		// 5) 토큰/온도: override 우선
		int maxTok = (maxTokensOverride != null) ? maxTokensOverride : computeSafeMaxTokens(userMsg);
		double temp = (temperatureOverride != null) ? temperatureOverride : this.temperature;

		Map<String, Object> req = new HashMap<>();
		req.put("model", modelName);
		req.put("max_tokens", maxTok);
		req.put("temperature", temp);
		req.put("stream", false);
		req.put("messages",
				List.of(Map.of("role", "system", "content", systemMsg), Map.of("role", "user", "content", userMsg)));

		String resp = vllmWebClient.post().uri("/v1/chat/completions").bodyValue(req).retrieve()
				.bodyToMono(String.class).block();

		String md = extractAnyContent(resp);
		return fixFences(md);
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

			String system = "You are a helpful assistant. 무조건 한국어로 대답하세요. 변수나 이름 제외하고 무조건 한국어.";
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

	private int byteLen(String s) {
		return (s == null) ? 0 : s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}

	// 정책 지정형 summarize (모드 힌트 normal/economy)

	public SummaryResult summarizeWithPolicy(long userIdx, String promptTitle, String original,
			boolean forcePromptSummary) throws Exception {

		String target; // 실제 요약 대상 텍스트
		String modeNote; // "user-priority" | "prompt-priority"

		if (!forcePromptSummary) {
			target = original == null ? "" : original;
			modeNote = "user-priority";
		} else {
			// 프롬프트 텍스트 자체를 ‘콘텐츠’로 요약하고 싶은 특별 케이스
			String instruction = promptRepository.findByTitle(promptTitle)
					.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle)).getContent();
			target = instruction;
			modeNote = "prompt-priority";
		}

		String compact = compactText(target);
		int bytes = byteLen(compact);

		if (bytes > ECONOMY_MAX_BYTES) {
			return SummaryResult.blocked("[안내] 텍스트가 너무 길어 요약을 진행하지 않습니다. 파일을 나누거나 텍스트를 줄여 주세요.");
		}

		if (bytes <= NORMAL_MAX_BYTES) {
			String md = runPromptMarkdown(userIdx, promptTitle, compact);
			SummaryResult r = SummaryResult.normal(md);
			r.setMode(modeNote);
			return r;
		}

		// economy: 키워드 + 앞/중간/뒤 샘플
		List<String> keywords = extractTopKeywords(compact, 80);
		String head = sliceChars(compact, 0, 8000);
		String mid = sliceChars(compact, Math.max(0, compact.length() / 2 - 4000), 8000);
		String tail = sliceChars(compact, Math.max(0, compact.length() - 8000), 8000);

		String economyPrompt = """
				아래 키워드와 샘플 텍스트(앞/중간/뒤 일부)만 바탕으로, [요약 대상]의 핵심을 정리하세요.
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
		SummaryResult r = SummaryResult.economy(md, keywords);
		r.setMode(modeNote);
		return r;
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

	private String shrinkRuns(String input) {
		if (input == null || input.isEmpty())
			return input;
		// ([=\\-_*#])를 1개 캡처하고, 같은 문자가 8회 이상 추가 반복되는 구간을 8개로 축약
		java.util.regex.Pattern p = java.util.regex.Pattern.compile("([=\\-_*#])\\1{8,}");
		java.util.regex.Matcher m = p.matcher(input);

		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String ch = m.group(1);
			m.appendReplacement(sb, ch.repeat(8)); // 정확히 8개로 바꿈
		}
		m.appendTail(sb);
		return sb.toString();
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
		compact = shrinkRuns(compact);
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
