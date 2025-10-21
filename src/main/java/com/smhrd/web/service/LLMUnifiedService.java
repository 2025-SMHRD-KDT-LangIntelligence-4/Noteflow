package com.smhrd.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LLMUnifiedService {

	// ====== 필드 ======
	private final WebClient vllmWebClient;
	private final WebClient embeddingClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final PromptRepository promptRepository;
	private final UserRepository userRepository;
	private final NoteRepository noteRepository;
	private final NoteFolderRepository noteFolderRepository;
	private final TagRepository tagRepository;
	private final NoteTagRepository noteTagRepository;
	private final CategoryHierarchyRepository categoryHierarchyRepository;
	private final TestSummaryRepository testSummaryRepository;
	private final FileParseService fileParseService;

	@Value("${vllm.api.model}")
	private String modelName;

	@Value("${vllm.api.max-tokens}")
	private int maxTokens;

	@Value("${vllm.api.temperature}")
	private double temperature;

	@Value("${vllm.api.context-limit:30000}")
	private int contextLimit;

	private static final int NORMAL_MAX_BYTES = 50 * 1024;
	private static final int MEDIUM_MAX_BYTES = 200 * 1024;

	// ====== 생성자 ======
	public LLMUnifiedService(
			@Qualifier("vllmApiClient") WebClient vllmWebClient,
			@Qualifier("embeddingClient") WebClient embeddingClient,
			PromptRepository promptRepository,
			UserRepository userRepository,
			NoteRepository noteRepository,
			NoteFolderRepository noteFolderRepository,
			TagRepository tagRepository,
			NoteTagRepository noteTagRepository,
			CategoryHierarchyRepository categoryHierarchyRepository,
			TestSummaryRepository testSummaryRepository,
			FileParseService fileParseService) {
		this.vllmWebClient = vllmWebClient;
		this.embeddingClient = embeddingClient;
		this.promptRepository = promptRepository;
		this.userRepository = userRepository;
		this.noteRepository = noteRepository;
		this.noteFolderRepository = noteFolderRepository;
		this.tagRepository = tagRepository;
		this.noteTagRepository = noteTagRepository;
		this.categoryHierarchyRepository = categoryHierarchyRepository;
		this.testSummaryRepository = testSummaryRepository;
		this.fileParseService = fileParseService;
	}

	// ====== 고급 요약 메서드 ======

	public SummaryResult summarizeLongDocument(long userIdx, String promptTitle, String original) throws Exception {
		String compact = compactText(original);
		int bytes = byteLen(compact);
		int estimatedTokens = estimateTokens(compact);

		log.info("문서 크기: {} bytes, 예상 토큰: {}", bytes, estimatedTokens);

		// ✅ 토큰이 3500 이하면 SIMPLE (context limit 8192의 절반 이하)
		if (estimatedTokens < 3500) {
			log.info("전략: SIMPLE (토큰: {})", estimatedTokens);
			try {
				String md = runPromptMarkdown(userIdx, promptTitle, compact);
				SummaryResult result = SummaryResult.normal(md);
				result.setMode("simple");
				return result;
			} catch (Exception e) {
				log.warn("SIMPLE 실패, RECURSIVE로 전환");
				return summarizeWithRecursiveChunking(userIdx, promptTitle, compact);
			}
		}

		// 중간 크기: Recursive Chunking
		if (estimatedTokens < 15000) {
			log.info("전략: RECURSIVE (토큰: {})", estimatedTokens);
			return summarizeWithRecursiveChunking(userIdx, promptTitle, compact);
		}

		// 대용량: Semantic Chunking
		log.info("전략: SEMANTIC (토큰: {})", estimatedTokens);
		return summarizeWithSemanticChunking(userIdx, promptTitle, compact);
	}

	private SummaryResult summarizeWithRecursiveChunking(long userIdx, String promptTitle, String text) throws Exception {
		int chunkSize = 4000;
		int overlap = 500;

		List<String> chunks = splitWithOverlap(text, chunkSize, overlap);
		log.info("Recursive: {} 청크", chunks.size());

		List<String> summaries = new ArrayList<>();
		for (int i = 0; i < chunks.size(); i++) {
			String prompt = String.format("[%d/%d]\n\n%s", i + 1, chunks.size(), chunks.get(i));
			String summary = runPromptMarkdown(userIdx, promptTitle, prompt, null, 600, 0.3);
			summaries.add(summary);
		}

		String combined = String.join("\n\n", summaries);
		String finalSummary = runPromptMarkdown(userIdx, promptTitle, "통합:\n\n" + combined, null, 2000, 0.3);

		SummaryResult result = SummaryResult.normal(finalSummary);
		result.setMode("recursive");
		result.setMessage(chunks.size() + "개 청크 분할");
		return result;
	}

	private SummaryResult summarizeWithSemanticChunking(long userIdx, String promptTitle, String text) throws Exception {
		List<String> paragraphs = splitIntoParagraphs(text);
		log.info("문단: {} 개", paragraphs.size());

		List<List<Float>> embeddings = getParagraphEmbeddings(paragraphs);
		List<SemanticChunk> chunks = mergeSemanticChunks(paragraphs, embeddings, 0.75);
		log.info("의미 청크: {} 개", chunks.size());

		List<String> summaries = new ArrayList<>();
		for (int i = 0; i < chunks.size(); i++) {
			String prompt = String.format("[섹션 %d/%d]\n\n%s", i + 1, chunks.size(), chunks.get(i).getText());
			String summary = runPromptMarkdown(userIdx, promptTitle, prompt, null, 800, 0.3);
			summaries.add(summary);
		}

		String finalSummary = hierarchicalReduce(userIdx, promptTitle, summaries);

		SummaryResult result = SummaryResult.economy(finalSummary, extractTopKeywords(text, 50));
		result.setMode("semantic");
		result.setMessage(chunks.size() + " 섹션 분석");
		return result;
	}

	// ====== 보조 메서드 ======

	private List<String> splitWithOverlap(String text, int chunkSize, int overlap) {
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = Math.min(start + chunkSize, text.length());
			chunks.add(text.substring(start, end));
			start += (chunkSize - overlap);
		}
		return chunks;
	}

	private List<String> splitIntoParagraphs(String text) {
		String[] paras = text.split("\n\n+");
		List<String> result = new ArrayList<>();

		for (String para : paras) {
			para = para.trim();
			if (para.length() < 50) continue;

			if (para.length() > 2000) {
				String[] sentences = para.split("\\. ");
				StringBuilder sb = new StringBuilder();
				for (String sent : sentences) {
					sb.append(sent).append(". ");
					if (sb.length() > 1500) {
						result.add(sb.toString().trim());
						sb = new StringBuilder();
					}
				}
				if (sb.length() > 0) result.add(sb.toString().trim());
			} else {
				result.add(para);
			}
		}
		return result;
	}

	private List<List<Float>> getParagraphEmbeddings(List<String> paragraphs) {
		try {
			Map<String, Object> response = embeddingClient.post()
					.uri("/embed")
					.bodyValue(Map.of("texts", paragraphs))
					.retrieve()
					.bodyToMono(Map.class)
					.block();

			List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
			return embeddings.stream()
					.map(emb -> emb.stream().map(Double::floatValue).collect(Collectors.toList()))
					.collect(Collectors.toList());
		} catch (Exception e) {
			log.error("임베딩 실패: {}", e.getMessage());
			return Collections.nCopies(paragraphs.size(), Collections.emptyList());
		}
	}

	private double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
		if (vec1.isEmpty() || vec2.isEmpty()) return 0.0;
		double dotProduct = 0.0, norm1 = 0.0, norm2 = 0.0;
		for (int i = 0; i < Math.min(vec1.size(), vec2.size()); i++) {
			dotProduct += vec1.get(i) * vec2.get(i);
			norm1 += vec1.get(i) * vec1.get(i);
			norm2 += vec2.get(i) * vec2.get(i);
		}
		return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
	}

	private List<SemanticChunk> mergeSemanticChunks(List<String> paragraphs, List<List<Float>> embeddings, double threshold) {
		List<SemanticChunk> chunks = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		List<Integer> indices = new ArrayList<>();

		for (int i = 0; i < paragraphs.size(); i++) {
			if (current.length() == 0) {
				current.append(paragraphs.get(i));
				indices.add(i);
			} else {
				int prevIdx = indices.get(indices.size() - 1);
				double sim = cosineSimilarity(embeddings.get(prevIdx), embeddings.get(i));

				if (sim >= threshold && current.length() < 5000) {
					current.append("\n\n").append(paragraphs.get(i));
					indices.add(i);
				} else {
					chunks.add(new SemanticChunk(current.toString(), new ArrayList<>(indices)));
					current = new StringBuilder(paragraphs.get(i));
					indices.clear();
					indices.add(i);
				}
			}
		}

		if (current.length() > 0) {
			chunks.add(new SemanticChunk(current.toString(), indices));
		}
		return chunks;
	}

	private String hierarchicalReduce(long userIdx, String promptTitle, List<String> summaries) throws Exception {
		if (summaries.size() <= 3) {
			String combined = String.join("\n\n", summaries);
			return runPromptMarkdown(userIdx, promptTitle, "통합:\n\n" + combined, null, 2000, 0.3);
		}

		List<String> intermediate = new ArrayList<>();
		for (int i = 0; i < summaries.size(); i += 3) {
			List<String> batch = summaries.subList(i, Math.min(i + 3, summaries.size()));
			String combined = String.join("\n\n", batch);
			String summary = runPromptMarkdown(userIdx, promptTitle, "통합:\n\n" + combined, null, 1000, 0.3);
			intermediate.add(summary);
		}
		return hierarchicalReduce(userIdx, promptTitle, intermediate);
	}

	@Data
	private static class SemanticChunk {
		private final String text;
		private final List<Integer> paragraphIndices;
	}

	// ====== 기본 요약 메서드 ======

	public String runPromptMarkdown(long userIdx, String promptTitle, String original) throws Exception {
		userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자"));

		String instruction = promptRepository.findByTitle(promptTitle)
				.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음"))
				.getContent();

		String systemMsg = "규칙: <요약대상> 태그 사이 텍스트만 요약. 창작 금지.\n" + instruction;
		String userMsg = "<요약대상>\n" + (original == null ? "" : original) + "\n</요약대상>";

		// ✅ vLLM 요청 포맷 수정
		Map<String, Object> req = new HashMap<>();
		req.put("model", modelName);
		req.put("max_tokens", computeSafeMaxTokens(userMsg));
		req.put("temperature", temperature);
		req.put("stream", false);  // 중요!

		List<Map<String, String>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", systemMsg));
		messages.add(Map.of("role", "user", "content", userMsg));
		req.put("messages", messages);

		try {
			String resp = vllmWebClient.post()
					.uri("/v1/chat/completions")
					.bodyValue(req)
					.retrieve()
					.bodyToMono(String.class)
					.block();

			return fixFences(extractAnyContent(resp));

		} catch (Exception e) {
			log.error("vLLM 호출 실패: {}", e.getMessage());
			throw new RuntimeException("AI 요약 실패: " + e.getMessage(), e);
		}
	}

	public String runPromptMarkdown(long userIdx, String promptTitle, String original,
									String systemOverride, Integer maxTok, Double temp) throws Exception {
		String instruction = promptRepository.findByTitle(promptTitle)
				.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음"))
				.getContent();

		String systemMsg = (systemOverride != null) ? systemOverride : instruction;
		String userMsg = original;

		int maxTokensVal = (maxTok != null) ? maxTok : computeSafeMaxTokens(userMsg);
		double temperatureVal = (temp != null) ? temp : this.temperature;

		// ✅ vLLM 요청 포맷 수정
		Map<String, Object> req = new HashMap<>();
		req.put("model", modelName);
		req.put("max_tokens", maxTokensVal);
		req.put("temperature", temperatureVal);
		req.put("stream", false);

		List<Map<String, String>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", systemMsg));
		messages.add(Map.of("role", "user", "content", userMsg));
		req.put("messages", messages);

		try {
			String resp = vllmWebClient.post()
					.uri("/v1/chat/completions")
					.bodyValue(req)
					.retrieve()
					.bodyToMono(String.class)
					.block();

			return fixFences(extractAnyContent(resp));

		} catch (Exception e) {
			log.error("vLLM 호출 실패: {}", e.getMessage());
			throw new RuntimeException("AI 요약 실패: " + e.getMessage(), e);
		}
	}

	// ====== NotionContentService용 메서드 ======

	public UnifiedResult summarizeText(long userIdx, String content, String promptTitle) {
		try {
			userRepository.findByUserIdx(userIdx)
					.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자"));

			String promptText = promptRepository.findByTitle(promptTitle)
					.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음"))
					.getContent();

			return callUnifiedLLM(promptText, trimForTokens(content));
		} catch (Exception e) {
			throw new RuntimeException("텍스트 요약 실패: " + e.getMessage(), e);
		}
	}

	public UnifiedResult summarizeFile(long userIdx, MultipartFile file, String promptTitle) {
		try {
			userRepository.findByUserIdx(userIdx)
					.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자"));

			String promptText = promptRepository.findByTitle(promptTitle)
					.orElseThrow(() -> new IllegalArgumentException("프롬프트 없음"))
					.getContent();

			String text = fileParseService.extractText(file);
			return callUnifiedLLM(promptText, trimForTokens(text));
		} catch (Exception e) {
			throw new RuntimeException("파일 요약 실패: " + e.getMessage(), e);
		}
	}

	public CategoryPath matchCategory(List<String> keywords, CategoryPath llmCategory) {
		if (keywords == null) keywords = List.of();

		Set<String> keyset = keywords.stream()
				.filter(k -> k != null && !k.isBlank())
				.map(String::toLowerCase)
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
		if (path == null) return null;

		Long parentId = null;
		parentId = findOrCreate(userIdx, parentId, path.getLarge());
		parentId = findOrCreate(userIdx, parentId, path.getMedium());
		parentId = findOrCreate(userIdx, parentId, path.getSmall());
		return parentId;
	}

	private Long findOrCreate(long userIdx, Long parentId, String name) {
		if (name == null || name.isBlank()) return parentId;

		List<NoteFolder> siblings = (parentId == null)
				? noteFolderRepository.findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(userIdx)
				: noteFolderRepository.findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userIdx, parentId);

		Optional<NoteFolder> found = siblings.stream()
				.filter(f -> f.getFolderName().equals(name))
				.findFirst();

		if (found.isPresent()) return found.get().getFolderId();

		NoteFolder folder = NoteFolder.builder()
				.userIdx(userIdx)
				.folderName(name)
				.parentFolderId(parentId)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.build();

		return noteFolderRepository.save(folder).getFolderId();
	}

	private UnifiedResult callUnifiedLLM(String promptText, String original) {
		try {
			String fullPrompt = promptText + "\n\n" + original + "\n\n" +
					"JSON 스키마로 출력:\n" +
					"{\n" +
					"  \"summary\": string,\n" +
					"  \"keywords\": string[5],\n" +
					"  \"category\": { \"large\": string, \"medium\": string, \"small\": string },\n" +
					"  \"tags\": string[]\n" +
					"}\n";

			int safeMax = computeSafeMaxTokens(fullPrompt);
			Map<String, Object> req = Map.of(
					"model", modelName,
					"max_tokens", safeMax,
					"temperature", temperature,
					"messages", List.of(Map.of("role", "user", "content", fullPrompt))
			);

			String response = vllmWebClient.post()
					.uri("/v1/chat/completions")
					.bodyValue(req)
					.retrieve()
					.bodyToMono(String.class)
					.block();

			String jsonText = stripFence(extractAnyContent(response));
			JsonNode root = objectMapper.readTree(jsonText);

			UnifiedResult r = new UnifiedResult();
			r.setSummary(root.path("summary").asText(""));
			r.setKeywords(readArray(root.path("keywords")));

			JsonNode cat = root.path("category");
			r.setCategory(new CategoryPath(
					cat.path("large").asText("기타"),
					cat.path("medium").asText("미분류"),
					cat.path("small").asText("일반")
			));
			r.setTags(readArray(root.path("tags")));

			return r;
		} catch (Exception e) {
			throw new RuntimeException("LLM 호출 실패: " + e.getMessage(), e);
		}
	}

	// ====== 유틸 메서드 ======

	private String extractAnyContent(String response) throws Exception {
		JsonNode root = objectMapper.readTree(response);
		JsonNode choices = root.path("choices");
		if (choices.isArray() && choices.size() > 0) {
			String c = choices.get(0).path("message").path("content").asText(null);
			if (c != null && !c.isBlank()) return c;
		}
		return response;
	}



	private String fixFences(String md) {
		if (md == null) return "";
		String s = md.strip(); // 양끝만 정리

		// JSON 그대로 들어온 경우 안전하게 건드리지 않음
		if (looksLikeJson(s)) return s;

		boolean open = false;
		for (String line : s.split("\\R")) {    // 모든 줄바꿈 대응
			String t = line.strip();
			if (t.startsWith("```")) {
				open = !open;
			}
		}
		if (open) {
			if (!s.endsWith("\n")) s += "\n";
			s += "```";
		}
		return s;
	}

	private boolean looksLikeJson(String s) {
		String t = s.trim();
		if (!(t.startsWith("{") && t.endsWith("}"))) return false;
		// vLLM OpenAI 응답의 대표 키가 있으면 JSON으로 간주
		return t.contains("\"choices\"") || t.contains("\"object\"") || t.contains("\"id\"");
	}

	private String stripFence(String s) {
	if (s == null) return "";
	String t = s.trim();
	// ``` 로 시작하면 fence를 벗겨서 내부만 반환
	if (t.startsWith("```")) {
		int first = t.indexOf('\n');
		int last  = t.lastIndexOf("```");
		if (first > 0 && last > first) {
			return t.substring(first + 1, last).trim();
		}
	}
	return t;
}


private List<String> readArray(JsonNode arr) {
	List<String> list = new ArrayList<>();
	if (arr != null && arr.isArray()) {
		arr.forEach(n -> list.add(n.asText("")));
	}
	return list;
}

private String trimForTokens(String content) {
	if (content == null) return "";
	int MAX_CHARS = 4000;
	return content.length() > MAX_CHARS
			? content.substring(0, MAX_CHARS) + "\n\n...(truncated)"
			: content;
}

	private int computeSafeMaxTokens(String prompt) {
		int input = estimateTokens(prompt);
		int buffer = Math.max(256, contextLimit / 10);
		int available = contextLimit - input - buffer;
		int result = Math.max(256, Math.min(maxTokens, available));

		log.info("토큰 계산 - input: {}, buffer: {}, available: {}, result: {}, contextLimit: {}",
				input, buffer, available, result, contextLimit);

		// ✅ max_tokens가 음수가 되면 안됨!
		if (result <= 0) {
			log.warn("max_tokens가 0 이하! 강제로 256으로 설정");
			return 256;
		}

		return result;
	}

	public int estimateTokens(String text) {
		if (text == null) return 0;
		// 한국어는 토큰이 더 많이 필요
		return (int) Math.ceil(text.length() / 2.5);  // 기존 3.5 → 2.5로 수정
	}

public String compactText(String s) {
	if (s == null) return "";
	return s.replaceAll("\\s+", " ").trim();
}

private int byteLen(String s) {
	return (s == null) ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
}

public List<String> extractTopKeywords(String text, int topN) {
	if (text == null) return List.of();
	Map<String, Integer> freq = new HashMap<>();
	String[] tokens = text.toLowerCase().split("\\s+");
	for (String tok : tokens) {
		if (tok.length() >= 2) freq.merge(tok, 1, Integer::sum);
	}
	return freq.entrySet().stream()
			.sorted((a, b) -> b.getValue().compareTo(a.getValue()))
			.limit(topN)
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
}

private int scoreCategory(Set<String> keys, String categoryKeywordsCsv) {
	if (categoryKeywordsCsv == null || categoryKeywordsCsv.isBlank()) return 0;

	Set<String> cat = Arrays.stream(categoryKeywordsCsv.toLowerCase().split(","))
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.collect(Collectors.toSet());

	int score = 0;
	for (String k : keys) {
		for (String ck : cat) {
			if (k.contains(ck) || ck.contains(k)) score += 10;
		}
	}
	return score;
}
	// LLMUnifiedService.java 클래스 안에 추가
	public String testVllmConnection() {
		try {
			log.info("vLLM 연결 테스트 시작...");

			Map<String, Object> req = new HashMap<>();
			req.put("model", modelName);
			req.put("max_tokens", 100);
			req.put("temperature", 0.7);
			req.put("stream", false);

			List<Map<String, String>> messages = new ArrayList<>();
			messages.add(Map.of("role", "user", "content", "안녕하세요"));
			req.put("messages", messages);

			String resp = vllmWebClient.post()
					.uri("/v1/chat/completions")
					.bodyValue(req)
					.retrieve()
					.bodyToMono(String.class)
					.block();

			String result = extractAnyContent(resp);
			log.info("vLLM 테스트 성공: {}", result);
			return result;

		} catch (Exception e) {
			log.error("vLLM 테스트 실패: {}", e.getMessage(), e);
			return "실패: " + e.getMessage();
		}
	}
// ====== DTO 클래스 ======

@Data
public static class SummaryResult {
	private boolean success;
	private String mode;
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
		r.message = "대용량 파일 - 고급 분석 완료";
		return r;
	}
}

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
}
