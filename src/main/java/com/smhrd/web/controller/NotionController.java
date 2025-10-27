package com.smhrd.web.controller;

import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
    private final TagRepository tagRepository;
    private final NoteTagRepository noteTagRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final FolderRepository folderRepository;
    private final NoteFolderRepository noteFolderRepository;

    // ------------------------------------------------------------
    // LLM 요약 생성 페이지
    // ------------------------------------------------------------
    @GetMapping("/create")
    public String showCreatePage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("vllmBaseUrl", env.getProperty("vllm.api.url"));
        model.addAttribute("vllmApiModel", env.getProperty("vllm.api.model"));
        model.addAttribute("vllmApiMaxTokens", env.getProperty("vllm.api.max-tokens"));
        model.addAttribute("vllmApiTemperature", env.getProperty("vllm.api.temperature"));
        
        // ✅ 전체 프롬프트를 전달 (프론트엔드에서 1~16번만 필터링)
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
        
        return "NotionCreateUnified";
    }

    // ------------------------------------------------------------
    // ✅ 파일 ID 기반 요약 (promptId 사용)
    // ------------------------------------------------------------
    @PostMapping("/create-by-id")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFromFileId(
            @RequestBody Map<String, String> req,
            @AuthenticationPrincipal(expression = "userIdx") Long userIdx
    ) {
        String gridfsId = req.get("fileId");
        
        // ✅ promptId로 변경
        Long promptId = null;
        try {
            String promptIdStr = req.get("promptId");
            if (promptIdStr != null && !promptIdStr.isBlank()) {
                promptId = Long.parseLong(promptIdStr);
            }
        } catch (NumberFormatException e) {
            log.warn("⚠️ promptId 파싱 실패: {}", req.get("promptId"));
        }
        
        Map<String, Object> out = new HashMap<>();
        
        try {
            // 파일 조회
            FileStorageService.FileInfo meta = fileStorageService.previewFile(gridfsId);
            if (meta == null) {
                return ResponseEntity.ok(Map.of("success", false, "error", "파일을 찾을 수 없습니다."));
            }
            
            // 권한 체크
            if (userIdx == null || !String.valueOf(userIdx).equals(meta.getUploaderIdx())) {
                return ResponseEntity.status(403)
                    .body(Map.of("success", false, "error", "권한이 없습니다."));
            }
            
            // 파일 다운로드 및 텍스트 추출
            byte[] bytes = fileStorageService.downloadFile(gridfsId);
            String filename = (meta.getOriginalName() == null) ? "file" : meta.getOriginalName();
            String fullText = fileParseService.extractText(bytes, filename);
            
            // ✅ promptId로 Prompt 엔티티 조회
            String promptTitle = "심플버전"; // 기본값
            if (promptId != null) {
                Optional<Prompt> promptOpt = promptRepository.findByPromptIdValue(promptId);
                if (promptOpt.isPresent()) {
                    promptTitle = promptOpt.get().getTitle();
                } else {
                    log.warn("⚠️ 프롬프트를 찾을 수 없음: promptId={}", promptId);
                }
            }
            
            log.info("✅ 파일 요약 요청: fileId={}, promptId={}, promptTitle={}", 
                     gridfsId, promptId, promptTitle);
            
            // LLM 요약 실행
            LLMUnifiedService.SummaryResult unified = 
            	    llmService.summarizeLongDocument(userIdx, promptId, fullText);
            out.put("success", unified.isSuccess());
            out.put("mode", unified.getMode());
            out.put("summary", unified.getSummaryMarkdown());
            out.put("keywords", unified.getKeywords());
            out.put("message", unified.getMessage());
            
            return ResponseEntity.ok(out);
            
        } catch (Exception e) {
            log.error("❌ 파일 요약 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------
    // ✅ 텍스트 기반 요약 (promptId 사용)
    // ------------------------------------------------------------
    @PostMapping("/create-text")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFromText(
        @RequestBody Map<String, String> req,
        Authentication auth
    ) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String content = req.getOrDefault("content", "");
        
        // ✅ promptId 파싱
        Long promptId = null;
        try {
            String promptIdStr = req.get("promptId");
            if (promptIdStr != null && !promptIdStr.isBlank()) {
                promptId = Long.parseLong(promptIdStr);
            }
        } catch (NumberFormatException e) {
            log.warn("⚠️ promptId 파싱 실패: {}", req.get("promptId"));
        }
        
        // ✅ 기본값 설정 (promptId가 없으면 1번 사용)
        if (promptId == null) {
            promptId = 1L; // "심플버전"의 promptId
        }

        Map<String, Object> result = new HashMap<>();
        try {
            log.info("✅ 텍스트 요약 요청: userIdx={}, promptId={}, contentLength={}",
                userIdx, promptId, content.length());

            // ✅ promptId로 직접 호출 (promptTitle 조회 불필요)
            var summary = llmService.summarizeLongDocument(userIdx, promptId, content);

            result.put("success", summary.isSuccess());
            result.put("summary", summary.getSummaryMarkdown());
            result.put("mode", summary.getMode());
            result.put("keywords", summary.getKeywords());
            result.put("message", summary.getMessage());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ 텍스트 요약 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------
    // ✅ 파일 업로드 기반 요약 (promptId 사용)
    // ------------------------------------------------------------
    @PostMapping("/create-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFromFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "promptId", required = false) Long promptId,
        Authentication auth
    ) throws IOException {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        
        // ✅ 기본값 설정
        if (promptId == null) {
            promptId = 1L;
        }

        Map<String, Object> result = new HashMap<>();
        try {
            String text = fileParseService.extractText(file);

            log.info("✅ 파일 업로드 요약 요청: fileName={}, promptId={}",
                file.getOriginalFilename(), promptId);

            // ✅ promptId로 직접 호출
            var summary = llmService.summarizeLongDocument(userIdx, promptId, text);

            result.put("success", summary.isSuccess());
            result.put("summary", summary.getSummaryMarkdown());
            result.put("mode", summary.getMode());
            result.put("keywords", summary.getKeywords());
            result.put("message", summary.getMessage());
            result.put("fileName", file.getOriginalFilename());
            result.put("fileSize", file.getSize());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ 파일 요약 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }


    // ------------------------------------------------------------
    // ✅ 노트 저장 (promptId 저장)
    // ------------------------------------------------------------
    @PostMapping("/save-note")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveNote(
            @RequestBody Map<String, String> req,
            Authentication auth,
            HttpSession session) {
        
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String title = req.getOrDefault("title", "");
        String summary = req.getOrDefault("summary", "");
        String originalContent = req.getOrDefault("originalContent", "");
        
        // ✅ promptId 받기
        Long promptId = null;
        try {
            String promptIdStr = req.get("promptId");
            if (promptIdStr != null && !promptIdStr.isBlank() && !promptIdStr.equals("null")) {
                promptId = Long.parseLong(promptIdStr);
            }
        } catch (NumberFormatException e) {
            log.warn("⚠️ promptId 파싱 실패: {}", req.get("promptId"));
        }
        
        String gridfsId = req.get("gridfsId");
        
        Map<String, Object> res = new HashMap<>();
        
        try {
            // 1. 키워드 추출 및 분류
            CategoryResult categoryResult = keywordExtractionService
                .extractAndClassifyWithRAG(title, summary, userIdx);
            
            List<String> keywords = new ArrayList<>(categoryResult.getExtractedKeywords());
            if (keywords.size() > 5) {
                keywords = keywords.subList(0, 5);
            }
            
            log.info("✅ 키워드 추출: {}", keywords);
            
            // 2. 폴더 생성
            Long noteFolderId = null;
            String mongoFolderId = null;
            String categoryPath = "미분류";
            
            if (categoryResult.hasCategory()) {
                noteFolderId = autoFolderService.createOrFindFolder(userIdx, categoryResult);
                mongoFolderId = getOrCreateMongoFolder(userIdx, categoryResult);
                categoryPath = autoFolderService.generateFolderPath(categoryResult);
                log.info("✅ 자동 분류 폴더: mysql={}, mongo={}", noteFolderId, mongoFolderId);
            }
            
            // 3. ✅ 노트 생성 (promptId 포함)
            Long noteId = notionContentService.saveNoteWithPrompt(
                userIdx, title, summary, noteFolderId, promptId
            );
            
            log.info("✅ 노트 생성 완료: noteId={}, promptId={}", noteId, promptId);
            
            // 4. 원본 파일 처리
            String originalGridId = null;
            if (gridfsId != null && !gridfsId.isBlank()) {
                originalGridId = gridfsId;
                if (mongoFolderId != null) {
                    try {
                        Optional<FileMetadata> fileMeta = fileMetadataRepository.findById(gridfsId);
                        if (fileMeta.isPresent()) {
                            FileMetadata file = fileMeta.get();
                            file.setFolderId(mongoFolderId);
                            fileMetadataRepository.save(file);
                            log.info("✅ 업로드 파일 폴더 이동: {}", mongoFolderId);
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ 파일 이동 실패: {}", e.getMessage());
                    }
                }
            } else if (originalContent != null && !originalContent.isBlank()) {
                try {
                    originalGridId = fileStorageService.storeTextAsFile(
                        title + "_원본.md",
                        originalContent,
                        userIdx,
                        mongoFolderId
                    );
                    log.info("✅ 원본 텍스트 GridFS 저장: {} (폴더: {})", originalGridId, mongoFolderId);
                } catch (Exception e) {
                    log.warn("⚠️ 원본 저장 실패: {}", e.getMessage());
                }
            }
            
            // 5. 노트에 원본 파일 ID 연결
            if (originalGridId != null) {
                noteRepository.updateNoteSourceId(noteId, originalGridId);
            }
            
            // 6. 태그 저장
            notionContentService.syncNoteTags(
                noteRepository.findById(noteId).get(), 
                keywords
            );
            
            // 7. Session 저장
            session.setAttribute("savedNoteId", noteId);
            session.setAttribute("savedTitle", title);
            session.setAttribute("savedCreatedAt", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            );
            session.setAttribute("savedKeywords", String.join(", ", keywords));
            session.setAttribute("savedCategoryPath", categoryPath);
            session.setAttribute("savedFolderId", noteFolderId);
            
            res.put("success", true);
            res.put("message", "노트가 저장되었습니다.");
            res.put("noteId", noteId);
            res.put("keywords", keywords);
            res.put("categoryPath", categoryPath);
            res.put("folderId", noteFolderId);
            
            return ResponseEntity.ok(res);
            
        } catch (Exception e) {
            log.error("❌ 노트 저장 실패: {}", e.getMessage(), e);
            res.put("success", false);
            res.put("error", e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }

    // MongoDB 폴더 계층적 생성
    private String getOrCreateMongoFolder(Long userIdx, CategoryResult categoryResult) {
        String fullPath = autoFolderService.generateFolderPath(categoryResult);
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }
        
        String[] pathParts = fullPath.split(" > ");
        String parentId = null;
        
        for (String folderName : pathParts) {
            folderName = folderName.trim();
            if (folderName.isEmpty()) continue;
            
            Optional<Folder> existing;
            if (parentId == null) {
                existing = folderRepository.findByUserIdxAndFolderNameAndParentFolderIdIsNull(
                    userIdx, folderName
                );
            } else {
                existing = folderRepository.findByUserIdxAndFolderNameAndParentFolderId(
                    userIdx, folderName, parentId
                );
            }
            
            if (existing.isPresent()) {
                parentId = existing.get().getId();
            } else {
                Folder newFolder = new Folder();
                newFolder.setUserIdx(userIdx);
                newFolder.setFolderName(folderName);
                newFolder.setParentFolderId(parentId);
                newFolder.setCreatedAt(LocalDateTime.now());
                parentId = folderRepository.save(newFolder).getId();
                log.info("✅ MongoDB 폴더 생성: {} (parent={})", folderName, parentId);
            }
        }
        
        return parentId;
    }

    // ... 나머지 메서드들은 기존 코드 유지 (complete, update, delete, download, test-vllm 등) ...
    
    @GetMapping("/complete")
    public String completePage(Model model, HttpSession session) {
        Long noteId = (Long) session.getAttribute("savedNoteId");
        String title = (String) session.getAttribute("savedTitle");
        String createdAt = (String) session.getAttribute("savedCreatedAt");
        String keywords = (String) session.getAttribute("savedKeywords");
        String categoryPath = (String) session.getAttribute("savedCategoryPath");
        Long folderId = (Long) session.getAttribute("savedFolderId");
        
        model.addAttribute("noteId", noteId);
        model.addAttribute("title", title);
        model.addAttribute("createdAt", createdAt);
        model.addAttribute("keywords", keywords);
        model.addAttribute("categoryPath", categoryPath);
        model.addAttribute("folderId", folderId);
        
        // Session 정리
        session.removeAttribute("savedNoteId");
        session.removeAttribute("savedTitle");
        session.removeAttribute("savedCreatedAt");
        session.removeAttribute("savedKeywords");
        session.removeAttribute("savedCategoryPath");
        session.removeAttribute("savedFolderId");
        
        log.info("✅ Complete 페이지: noteId={}, title={}, keywords={}", noteId, title, keywords);
        
        return "NotionComplete";
    }
}
