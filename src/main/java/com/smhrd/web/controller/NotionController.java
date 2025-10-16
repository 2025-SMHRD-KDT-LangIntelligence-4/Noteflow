package com.smhrd.web.controller;

import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.Prompt;
import com.smhrd.web.entity.TestSummary;
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
import java.util.ArrayList;
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
    private final KeywordExtractionService keywordExtractionService;
    private final AutoFolderService autoFolderService;
    private final NoteRepository noteRepository;
    private final FileStorageService fileStorageService;
    private final FileParseService fileParseService;
    

    // -----------------------------
    // LLM 요약 데이터 입력 페이지
    // -----------------------------
    
    @GetMapping("/precreate")
    public String preCreatePage(@RequestParam(required=false) String title, Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("preTitle", title != null ? title : "");
        model.addAttribute("activeMenu", "notionCreate");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        // prompts 등도 추가
        return "NotionCreate";
    }
    
    
    // -----------------------------
    // LLM 요약 생성 페이지
    // -----------------------------
    @GetMapping("/create")
    public String showCreatePage(Model model,@AuthenticationPrincipal UserDetails userDetails) {
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
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }

        return "NotionCreate2";
    }

    // -----------------------------
    // 파일 id 기반 차단
    // -----------------------------
    


@PostMapping("/create-by-id")
@ResponseBody
public ResponseEntity<Map<String, Object>> createFromFileId(
        @RequestBody Map<String, String> req,
        @AuthenticationPrincipal(expression = "userIdx") Long userIdx,
        @Value("${notion.summary.normal-max-bytes:51200}") int normalMaxBytes,   // 50KB
        @Value("${notion.summary.economy-max-bytes:512000}") int economyMaxBytes // 500KB
) {
    String gridfsId = req.get("fileId");
    String promptTitle = req.getOrDefault("promptTitle", "심플버전");
    Map<String, Object> out = new HashMap<>();
    try {
        FileStorageService.FileInfo meta = fileStorageService.previewFile(gridfsId);
        if (meta == null) return ResponseEntity.ok(Map.of("success", false, "error", "파일을 찾을 수 없습니다."));

        long size = meta.getSize();
        if (size > economyMaxBytes) {
            // 500KB 초과 → 요약 차단(저장만)
            out.put("success", false);
            out.put("mode", "blocked");
            out.put("message", "[안내] 파일이 너무 커서 요약을 진행하지 않습니다. 텍스트를 줄이거나 파일을 나눠 업로드해 주세요.");
            return ResponseEntity.ok(out);
        }

        // 500KB 이하만 파싱 진행
        byte[] bytes = fileStorageService.downloadFile(gridfsId);
        String filename = meta.getOriginalName() == null ? "file" : meta.getOriginalName();
        String fullText = fileParseService.extractText(bytes, filename);

        // 길이 기준으로 normal/economy 결정
        int textBytes = fullText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        LLMUnifiedService.SummaryResult unified;
        if (textBytes <= normalMaxBytes) {
            unified = llmService.summarizeWithPolicy(userIdx, promptTitle, fullText, "normal");
        } else {
            unified = llmService.summarizeWithPolicy(userIdx, promptTitle, fullText, "economy");
        }

        out.put("success", unified.isSuccess());
        out.put("mode", unified.getMode());
        out.put("summary", unified.getSummaryMarkdown());
        out.put("keywords", unified.getKeywords());
        out.put("message", unified.getMessage());
        return ResponseEntity.ok(out);
    } catch (Exception e) {
        return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
    }
}


    
    // -----------------------------
    // 텍스트 기반 요약 생성
    // -----------------------------
    @PostMapping("/create-text")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFromText(@RequestBody Map<String, String> req,
                                                              Authentication auth) {
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String content = req.get("content");
        String promptTitle = req.get("promptTitle");

        log.info("[TEXT] LLM 요약 요청 - prompt: {}", promptTitle);

        Map<String, Object> result = new HashMap<>();
        try {
            TestSummary summary = llmService.processText(userIdx, content, promptTitle);
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
public ResponseEntity<Map<String, Object>> createFromFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam("promptTitle") String promptTitle,
        Authentication auth,
        @Value("${notion.summary.normal-max-bytes:51200}") int normalMaxBytes,   // 50KB
        @Value("${notion.summary.economy-max-bytes:512000}") int economyMaxBytes // 500KB
) throws IOException {
    Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
    Map<String, Object> result = new HashMap<>();
    try {
        long size = file.getSize();
        if (size > economyMaxBytes) {
            result.put("success", false);
            result.put("mode", "blocked");
            result.put("message", "[안내] 파일이 너무 커서 요약을 진행하지 않습니다. 텍스트를 줄이거나 파일을 나눠 업로드해 주세요.");
            return ResponseEntity.ok(result);
        }
        // 파싱 및 텍스트 길이에 따른 normal/economy
        String text = fileParseService.extractText(file);
        int textBytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        LLMUnifiedService.SummaryResult unified;
        if (textBytes <= normalMaxBytes) {
            unified = llmService.summarizeWithPolicy(userIdx, promptTitle, text, "normal");
        } else {
            unified = llmService.summarizeWithPolicy(userIdx, promptTitle, text, "economy");
        }
        result.put("success", unified.isSuccess());
        result.put("mode", unified.getMode());
        result.put("summary", unified.getSummaryMarkdown());
        result.put("keywords", unified.getKeywords());
        result.put("message", unified.getMessage());
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
            Authentication auth,@AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
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
            // 1. 노트 저장
            Long noteId = notionContentService.saveNote(userIdx, title, summary, promptId);

            // 2. 키워드 + 카테고리 매칭
            CategoryResult categoryResult = keywordExtractionService.extractAndClassifyWithRAG(title, summary);
            List<String> keywords = new ArrayList<>(categoryResult.getExtractedKeywords());
            if (keywords.size() > 5) keywords = keywords.subList(0, 5);

            String categoryPath = categoryResult.getSuggestedFolderPath();
            Long folderId = autoFolderService.createOrFindFolder(userIdx, categoryResult);
            noteRepository.updateNoteFolderId(noteId, folderId);

            String sourceGridId = fileStorageService.storeTextAsFile(title + ".md", summary, userIdx, folderId);
            noteRepository.updateNoteSourceId(noteId, sourceGridId);


            // 응답
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
    public String showCompletePage(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "저장 완료");
        
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "NotionComplete";
    }

}
