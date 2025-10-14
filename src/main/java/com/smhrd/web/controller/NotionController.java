package com.smhrd.web.controller;

import com.smhrd.web.entity.Prompt;
import com.smhrd.web.entity.TestSummary;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.PromptRepository;
import com.smhrd.web.service.LLMUnifiedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.smhrd.web.service.NotionContentService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/notion")
public class NotionController {
	private final PromptRepository promptRepository;
	private final LLMUnifiedService llmService;
	private final Environment env;
	private final NotionContentService notionContentService;
	private final com.smhrd.web.repository.UserRepository userRepository;

	// -----------------------------
	// LLM 요약 생성 페이지
	// -----------------------------
	@GetMapping("/create")
	public String showCreatePage(Model model) {
		model.addAttribute("vllmBaseUrl", env.getProperty("vllm.api.url"));
		model.addAttribute("vllmApiModel", env.getProperty("vllm.api.model"));
		model.addAttribute("vllmApiMaxTokens", env.getProperty("vllm.api.max-tokens"));
		model.addAttribute("vllmApiTemperature", env.getProperty("vllm.api.temperature"));
		List<Prompt> prompts = promptRepository.findAll();
		model.addAttribute("prompts", prompts);
		model.addAttribute("pageTitle", "노션 작성");
		model.addAttribute("activeMenu", "notionCreate");
		model.addAttribute("image", "/images/Group.svg");
		return "NotionCreate";
	}

	// -----------------------------
	// 텍스트 기반 요약 생성
	// -----------------------------
	@PostMapping("/create-text")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> createFromText(@RequestBody Map<String, String> req,
															  Authentication auth) {
		String userId = auth.getName();
		String content = req.get("content");
		String promptTitle = req.get("promptTitle");

		log.info("[TEXT] LLM 요약 요청 - prompt: {}", promptTitle);

		Map<String, Object> result = new HashMap<>();
		try {
			TestSummary summary = llmService.processText(userId, content, promptTitle);
			result.put("success", true);
			result.put("summary", summary.getAiSummary());
			result.put("status", summary.getStatus());
			result.put("processingTimeMs", summary.getProcessingTimeMs());
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			result.put("success", false);
			result.put("error", e.getMessage());
			return ResponseEntity.status(500).body(result);
		}
	}

	// -----------------------------
	// 파일 기반 요약 생성
	// -----------------------------
	@PostMapping("/create-file")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> createFromFile(@RequestParam("file") MultipartFile file,
															  @RequestParam("promptTitle") String promptTitle,
															  Authentication auth) throws IOException {
		String userId = auth.getName();
		log.info("[FILE] LLM 요약 요청 - file: {}", file.getOriginalFilename());

		Map<String, Object> result = new HashMap<>();
		try {
			TestSummary summary = llmService.processFile(userId, file, promptTitle);
			result.put("success", true);
			result.put("summary", summary.getAiSummary());
			result.put("status", summary.getStatus());
			result.put("fileName", summary.getFileName());
			result.put("fileSize", summary.getFileSize());
			result.put("processingTimeMs", summary.getProcessingTimeMs());
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			result.put("success", false);
			result.put("error", e.getMessage());
			return ResponseEntity.status(500).body(result);
		}
	}


	@PostMapping(value = "/api/notion/save-text", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> saveTextNote(
			@RequestParam("title") String title,
			@RequestParam("content") String content,
			@RequestParam("notionType") String notionType,
			Authentication auth
	) {
		String userId = auth.getName();
		try {
			Long noteId = notionContentService.createNotionFromText(userId, title, content, notionType);
			return ResponseEntity.ok(Map.of("success", true, "noteId", noteId));
		} catch (Exception e) {
			return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
		}

	}
	@PostMapping("/save-note")
	@ResponseBody
	public ResponseEntity<Map<String,Object>> saveNote(
			@RequestBody Map<String,String> req,
			Authentication auth) {

		// 1) 로그인 사용자 조회
		String username = auth.getName();
		User user = userRepository.findByUserId(username)
				.orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));
		Long userIdx = user.getUserIdx();

		// 2) 요청 데이터
		String title     = req.getOrDefault("title", "제목없음");
		String summary   = req.getOrDefault("summary", "");
		Long   promptId  = Long.parseLong(req.getOrDefault("promptId", "0"));

		Map<String,Object> res = new HashMap<>();
		try {
			// 3) 노트 저장 (user_idx에 로그인 사용자 설정)
			Long noteId = notionContentService.saveNote(
					userIdx,
					title,
					summary,
					promptId
			);
			res.put("success", true);
			res.put("noteId", noteId);
			return ResponseEntity.ok(res);

		} catch (Exception e) {
			log.error("노트 저장 실패", e);
			res.put("success", false);
			res.put("error", e.getMessage());
			return ResponseEntity.status(500).body(res);
		}
	}
}