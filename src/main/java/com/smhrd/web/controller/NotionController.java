package com.smhrd.web.controller;

import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.Prompt;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.repository.PromptRepository;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/notion")
public class NotionController {

    private final PromptRepository promptRepository;
    private final LLMUnifiedService llmService;
    private final Environment env;
    private final NotionContentService notionContentService;
    private final KeywordExtractionService keywordExtractionService;
    private final AutoFolderService autoFolderService;
    private final NoteRepository noteRepository;
    private final FileStorageService fileStorageService;
    private final FileParseService fileParseService;

    // ------------------------------------------------------------
    // (선택) 예전 precreate 경로: 통합 페이지로 유도
    // ------------------------------------------------------------
    @GetMapping("/precreate")
    public String preCreateRedirect() {
        return "redirect:/notion/create";
    }

    // ------------------------------------------------------------
    // LLM 요약 생성 페이지 (템플릿: NotionCreateUnified 로 전환)
    // ------------------------------------------------------------
    @GetMapping("/create")
    public String showCreatePage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("vllmBaseUrl", env.getProperty("vllm.api.url"));
        model.addAttribute("vllmApiModel", env.getProperty("vllm.api.model"));
        model.addAttribute("vllmApiMaxTokens", env.getProperty("vllm.api.max-tokens"));
        model.addAttribute("vllmApiTemperature", env.getProperty("vllm.api.temperature"));

        List<Prompt> prompts = promptRepository.findAll();
        model.addAttribute("prompts", prompts);
        model.addAttribute("pageTitle", "노션 작성");
        model.addAttribute("activeMenu", "notionCreate");
        model.addAttribute("image", "/images/Group.svg");

        if (userDetails != null) {
            String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }

        // ✅ 통합 단일 페이지 템플릿
        return "NotionCreateUnified";
    }

    // ------------------------------------------------------------
    // (선택) 파일 사전 파싱(프리뷰/길이 체크) — Unified에서 사용 가능
    // ------------------------------------------------------------
    @PostMapping("/parse-file")
    @ResponseBody
    public ResponseEntity<Map<String,Object>> parseFileOnly(@RequestParam("file") MultipartFile file) {
        Map<String,Object> out = new HashMap<>();
        try {
            String text = fileParseService.extractText(file);
            int length = (text == null) ? 0 : text.strip().length();
            String preview = (text == null) ? "" : (text.length() > 600 ? (text.substring(0,600) + "\n\n...") : text);
            out.put("success", true);
            out.put("length", length);
            out.put("preview", preview);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------
    // 파일 id 기반 요약 (현행 유지)
    // ------------------------------------------------------------
    @PostMapping("/create-by-id")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFromFileId(
            @RequestBody Map<String, String> req,
            @AuthenticationPrincipal(expression = "userIdx") Long userIdx,
            @Value("${notion.summary.economy-max-bytes:512000}") int economyMaxBytes // 500KB
    ) {
        String gridfsId = req.get("fileId");
        String promptTitle = req.getOrDefault("promptTitle", "심플버전");
        Map<String, Object> out = new HashMap<>();
        try {
            FileStorageService.FileInfo meta = fileStorageService.previewFile(gridfsId);
            if (meta == null) {
                return ResponseEntity.ok(Map.of("success", false, "error", "파일을 찾을 수 없습니다."));
            }
            if (userIdx == null || !String.valueOf(userIdx).equals(meta.getUploaderIdx())) {
                return ResponseEntity.status(403).body(Map.of("success", false, "error", "권한이 없습니다."));
            }
            long size = meta.getSize();
            if (size > economyMaxBytes) {
                out.put("success", false);
                out.put("mode", "blocked");
                out.put("message", "[안내] 파일이 너무 커서 요약을 진행하지 않습니다. 텍스트를 줄이거나 파일을 나눠 업로드해 주세요.");
                return ResponseEntity.ok(out);
            }
            byte[] bytes = fileStorageService.downloadFile(gridfsId);
            String filename = (meta.getOriginalName() == null) ? "file" : meta.getOriginalName();
            String fullText = fileParseService.extractText(bytes, filename);

            LLMUnifiedService.SummaryResult unified =
                    llmService.summarizeWithPolicy(userIdx, promptTitle, fullText, false);

            out.put("success", unified.isSuccess());
            out.put("mode", unified.getMode()); // "user-priority" / "economy" / "blocked"
            out.put("summary", unified.getSummaryMarkdown());
            out.put("keywords", unified.getKeywords());
            out.put("message", unified.getMessage());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------
    // 텍스트 기반 요약 (현행 유지)
    // ------------------------------------------------------------
    @PostMapping("/create-text")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFromText(
            @RequestBody Map<String, String> req, Authentication auth
    ) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String content = req.getOrDefault("content", "");
        String promptTitle = req.getOrDefault("promptTitle", "심플버전");
        Map<String, Object> result = new HashMap<>();
        try {
            var summary = llmService.summarizeWithPolicy(userIdx, promptTitle, content, false);
            result.put("success", summary.isSuccess());
            result.put("summary", summary.getSummaryMarkdown());
            result.put("mode", summary.getMode()); // "user-priority" / "economy" / "blocked"
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------
    // 파일 업로드 기반 요약 (현행 유지)
    // ------------------------------------------------------------
    @PostMapping("/create-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("promptTitle") String promptTitle,
            Authentication auth
    ) throws IOException {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        Map<String, Object> result = new HashMap<>();
        try {
            String text = fileParseService.extractText(file);
            var summary = llmService.summarizeWithPolicy(userIdx, promptTitle, text, false);
            result.put("success", summary.isSuccess());
            result.put("summary", summary.getSummaryMarkdown());
            result.put("mode", summary.getMode());
            result.put("fileName", file.getOriginalFilename());
            result.put("fileSize", file.getSize());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------
    // 저장 (현행 유지)
    // ------------------------------------------------------------
    @PostMapping(value = "/api/notion/save-text", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveTextNote(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("notionType") String notionType,
            Authentication auth, @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        try {
            Long noteId = notionContentService.createNotionFromText(userIdx, title, content, notionType);
            return ResponseEntity.ok(Map.of("success", true, "noteId", noteId));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/save-note")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveNote(@RequestBody Map<String, String> req, Authentication auth) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String title = req.getOrDefault("title", "제목없음");
        String summary = req.getOrDefault("summary", "");
        Long promptId = Long.parseLong(req.getOrDefault("promptId", "0"));
        Map<String, Object> res = new HashMap<>();
        try {
            Long noteId = notionContentService.saveNote(userIdx, title, summary, promptId);
            CategoryResult categoryResult = keywordExtractionService.extractAndClassifyWithRAG(title, summary);
            List<String> keywords = new ArrayList<>(categoryResult.getExtractedKeywords());
            if (keywords.size() > 5) keywords = keywords.subList(0, 5);
            String categoryPath = categoryResult.getSuggestedFolderPath();
            Long folderId = autoFolderService.createOrFindFolder(userIdx, categoryResult);
            noteRepository.updateNoteFolderId(noteId, folderId);
            String sourceGridId = fileStorageService.storeTextAsFile(title + ".md", summary, userIdx, folderId);
            noteRepository.updateNoteSourceId(noteId, sourceGridId);

            res.put("success", true);
            res.put("noteId", noteId);
            res.put("keywords", keywords);
            res.put("categoryPath", categoryPath);
            res.put("folderId", folderId);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("error", e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }

    @GetMapping("/complete")
    public String showCompletePage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "저장 완료");
        if (userDetails != null) {
            String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "NotionComplete";
    }
}