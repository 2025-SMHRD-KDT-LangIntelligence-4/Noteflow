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
            @AuthenticationPrincipal(expression = "userIdx") Long userIdx
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

            byte[] bytes = fileStorageService.downloadFile(gridfsId);
            String filename = (meta.getOriginalName() == null) ? "file" : meta.getOriginalName();
            String fullText = fileParseService.extractText(bytes, filename);

            // ✅ 고급 요약 (파일 크기 제한 없음!)
            LLMUnifiedService.SummaryResult unified =
                    llmService.summarizeLongDocument(userIdx, promptTitle, fullText);

            out.put("success", unified.isSuccess());
            out.put("mode", unified.getMode());
            out.put("summary", unified.getSummaryMarkdown());
            out.put("keywords", unified.getKeywords());
            out.put("message", unified.getMessage());

            return ResponseEntity.ok(out);

        } catch (Exception e) {
            log.error("파일 요약 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------
    // 텍스트 기반 요약 (현행 유지)
    // ------------------------------------------------------------
    @PostMapping("/create-text")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createFromText(
            @RequestBody Map<String, String> req,
            Authentication auth
    ) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String content = req.getOrDefault("content", "");
        String promptTitle = req.getOrDefault("promptTitle", "심플버전");
        Map<String, Object> result = new HashMap<>();

        try {
            // ✅ 새로운 고급 요약 메서드 사용
            var summary = llmService.summarizeLongDocument(userIdx, promptTitle, content);

            result.put("success", summary.isSuccess());
            result.put("summary", summary.getSummaryMarkdown());
            result.put("mode", summary.getMode());
            result.put("keywords", summary.getKeywords());
            result.put("message", summary.getMessage());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("텍스트 요약 실패: {}", e.getMessage(), e);
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

            // ✅ 새로운 고급 요약 메서드 사용
            var summary = llmService.summarizeLongDocument(userIdx, promptTitle, text);

            result.put("success", summary.isSuccess());
            result.put("summary", summary.getSummaryMarkdown());
            result.put("mode", summary.getMode());
            result.put("keywords", summary.getKeywords());
            result.put("message", summary.getMessage());
            result.put("fileName", file.getOriginalFilename());
            result.put("fileSize", file.getSize());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("파일 요약 실패: {}", e.getMessage(), e);
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
    public ResponseEntity<Map<String, Object>> saveNote(
            @RequestBody Map<String, String> req,
            Authentication auth,
            HttpSession session) {

        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String title = req.getOrDefault("title", "");
        String summary = req.getOrDefault("summary", "");
        String originalContent = req.getOrDefault("originalContent", "");
        Long promptId = Long.parseLong(req.getOrDefault("promptId", "0"));
        String gridfsId = req.get("gridfsId");

        Map<String, Object> res = new HashMap<>();

        try {
            // 1. ❌ promptId를 folderId로 쓰지 말고 null로
            // Long noteId = notionContentService.saveNote(userIdx, title, summary, promptId);

            // 2. 키워드 추출 및 분류 먼저 실행
            CategoryResult categoryResult = keywordExtractionService
                    .extractAndClassifyWithRAG(title, summary, userIdx);

            List<String> keywords = new ArrayList<>(categoryResult.getExtractedKeywords());
            if (keywords.size() > 5) {
                keywords = keywords.subList(0, 5);
            }
            log.info("✅ 키워드 추출: {}", keywords);

            // 3. 폴더 생성 (MySQL + MongoDB)
            Long noteFolderId = null;
            String mongoFolderId = null;
            String categoryPath = "미분류";

            if (categoryResult.hasCategory()) {
                // MySQL 폴더 생성
                noteFolderId = autoFolderService.createOrFindFolder(userIdx, categoryResult);

                // MongoDB 폴더 생성 (계층 구조)
                mongoFolderId = getOrCreateMongoFolder(userIdx, categoryResult);

                categoryPath = autoFolderService.generateFolderPath(categoryResult);
                log.info("✅ 자동 분류 폴더: mysql={}, mongo={}", noteFolderId, mongoFolderId);
            }

            // 4. ✅ 이제 유효한 folderId로 노트 생성
            Long noteId = notionContentService.saveNote(userIdx, title, summary, noteFolderId);
            log.info("✅ 노트 생성 완료: noteId={}", noteId);

            // 5. 원본 파일 처리
            String originalGridId = null;

            if (gridfsId != null && !gridfsId.isBlank()) {
                // ✅ 업로드한 파일: 카테고리 폴더로 이동
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
                // ✅ 텍스트 작성: 원본 텍스트를 카테고리 폴더에 저장
                try {
                    originalGridId = fileStorageService.storeTextAsFile(
                            title + "_원본.md",
                            originalContent,
                            userIdx,
                            mongoFolderId  // ✅ 카테고리 폴더에 저장
                    );
                    log.info("✅ 원본 텍스트 GridFS 저장: {} (폴더: {})", originalGridId, mongoFolderId);
                } catch (Exception e) {
                    log.warn("⚠️ 원본 저장 실패: {}", e.getMessage());
                }
            }

            // 6. 노트에 원본 파일 ID 연결
            if (originalGridId != null) {
                noteRepository.updateNoteSourceId(noteId, originalGridId);
            }

            // 7. 태그 저장
            notionContentService.syncNoteTags(noteRepository.findById(noteId).get(), keywords);

            // 8. Session에 저장
            session.setAttribute("savedNoteId", noteId);
            session.setAttribute("savedTitle", title);
            session.setAttribute("savedCreatedAt", LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            ));
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


    // ✅ MongoDB 폴더 계층적 생성
    private String getOrCreateMongoFolder(Long userIdx, CategoryResult categoryResult) {
        String fullPath = autoFolderService.generateFolderPath(categoryResult);

        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }

        // "Spring > 데이터 접근 > Repository" → ["Spring", "데이터 접근", "Repository"]
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

    @GetMapping("/complete")
    public String completePage(Model model, HttpSession session) {
        Long noteId = (Long) session.getAttribute("savedNoteId");
        String title = (String) session.getAttribute("savedTitle");  // ✅ 추가
        String createdAt = (String) session.getAttribute("savedCreatedAt");  // ✅ 추가
        String keywords = (String) session.getAttribute("savedKeywords");
        String categoryPath = (String) session.getAttribute("savedCategoryPath");
        Long folderId = (Long) session.getAttribute("savedFolderId");

        model.addAttribute("noteId", noteId);
        model.addAttribute("title", title);  // ✅ 추가
        model.addAttribute("createdAt", createdAt);  // ✅ 추가
        model.addAttribute("keywords", keywords);
        model.addAttribute("categoryPath", categoryPath);
        model.addAttribute("folderId", folderId);

        // Session 정리
        session.removeAttribute("savedNoteId");
        session.removeAttribute("savedTitle");  // ✅ 추가
        session.removeAttribute("savedCreatedAt");  // ✅ 추가
        session.removeAttribute("savedKeywords");
        session.removeAttribute("savedCategoryPath");
        session.removeAttribute("savedFolderId");

        log.info("✅ Complete 페이지: noteId={}, title={}, keywords={}", noteId, title, keywords);

        return "NotionComplete";
    }


    @PutMapping("/api/notion/{noteIdx}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> updateNote(
            @PathVariable Long noteIdx,
            @RequestBody Map<String, Object> req,
            Authentication auth
    ) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String title = (String) req.getOrDefault("title", "제목없음");
        String content = (String) req.getOrDefault("content", "");

        Map<String, Object> res = new HashMap<>();
        try {
            Note note = noteRepository.findById(noteIdx)
                    .orElseThrow(() -> new IllegalArgumentException("노트를 찾을 수 없습니다."));

            // 권한 체크
            if (!note.getUser().getUserIdx().equals(userIdx)) {
                res.put("success", false);
                res.put("message", "권한이 없습니다.");
                return ResponseEntity.status(403).body(res);
            }

            // 제목/내용 수정
            note.setTitle(title);
            note.setContent(content);
            note.setUpdatedAt(LocalDateTime.now());

            // ✅ 카테고리 변경 시 폴더 자동 이동
            String large = (String) req.get("largeCategory");
            String medium = (String) req.get("mediumCategory");
            String small = (String) req.get("smallCategory");

            if (large != null && !large.trim().isEmpty()) {
                log.info("카테고리 변경 요청: {} / {} / {}", large, medium, small);

                // CategoryResult 수동 생성
                CategoryResult categoryResult = CategoryResult.builder()
                        .largeCategory(large.trim())
                        .mediumCategory(medium != null ? medium.trim() : null)
                        .smallCategory(small != null ? small.trim() : null)
                        .build();

                // 폴더 찾기 or 생성 (공개 + 본인 카테고리 기준)
                Long newFolderId = autoFolderService.createOrFindFolder(userIdx, categoryResult);

                if (newFolderId != null) {
                    Long oldFolderId = note.getFolderId();
                    note.setFolderId(newFolderId);

                    res.put("folderChanged", true);
                    res.put("oldFolderId", oldFolderId);
                    res.put("newFolderId", newFolderId);

                    log.info("폴더 이동: {} → {}", oldFolderId, newFolderId);
                }
            }

            // ✅ 태그 동기화
            List<String> keywords = (List<String>) req.get("keywords");
            if (keywords != null && !keywords.isEmpty()) {
                log.info("태그 업데이트: {}", keywords);


                 notionContentService.syncNoteTags(note, keywords);
            }

            noteRepository.save(note);

            res.put("success", true);
            res.put("message", "저장되었습니다.");
            return ResponseEntity.ok(res);

        } catch (Exception e) {
            log.error("노트 수정 오류: noteIdx={}, error={}", noteIdx, e.getMessage(), e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }

    /**
     * ✅ 태그 동기화 헬퍼 메소드
     * - 기존 태그 제거 (usage_count -1)
     * - 신규 태그 추가 (usage_count +1)
     */
    private void syncNoteTags(Note note, List<String> newKeywords) {
        // 1) 기존 태그 조회
        List<NoteTag> oldNoteTags = noteTagRepository.findAllByNote(note);
        Set<String> oldTagNames = oldNoteTags.stream()
                .map(nt -> nt.getTag().getName())
                .collect(Collectors.toSet());

        Set<String> newTagNames = new HashSet<>(newKeywords);

        // 2) 제거할 태그 (old - new)
        for (NoteTag nt : oldNoteTags) {
            if (!newTagNames.contains(nt.getTag().getName())) {
                tagRepository.bumpUsage(nt.getTag().getTagIdx(), -1);
                noteTagRepository.delete(nt);
            }
        }

        // 3) 추가할 태그 (new - old)
        for (String tagName : newKeywords) {
            String name = tagName.trim();
            if (name.isEmpty()) continue;

            if (!oldTagNames.contains(name)) {
                // 태그 생성 or 조회
                Tag tag = tagRepository.findByName(name).orElseGet(() -> {
                    try {
                        return tagRepository.save(Tag.builder().name(name).build());
                    } catch (DataIntegrityViolationException e) {
                        return tagRepository.findByName(name).orElseThrow();
                    }
                });

                // 연결 추가
                if (!noteTagRepository.existsByNoteAndTag(note, tag)) {
                    noteTagRepository.save(NoteTag.builder().note(note).tag(tag).build());
                    tagRepository.bumpUsage(tag.getTagIdx(), 1);
                }
            }
        }
    }
    @DeleteMapping("/api/notion/{noteIdx}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteNote(
            @PathVariable Long noteIdx,
            Authentication auth
    ) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        Map<String, Object> res = new HashMap<>();
        try {
            Note note = noteRepository.findById(noteIdx)
                    .orElseThrow(() -> new IllegalArgumentException("노트를 찾을 수 없습니다."));

            if (!note.getUser().getUserIdx().equals(userIdx)) {
                res.put("success", false);
                res.put("message", "권한이 없습니다.");
                return ResponseEntity.status(403).body(res);
            }

            // ✅ 태그 관계 조회
            List<NoteTag> noteTags = noteTagRepository.findAllByNote(note);

            // ✅ usage_count 감소
            for (NoteTag nt : noteTags) {
                tagRepository.bumpUsage(nt.getTag().getTagIdx(), -1);
            }

            // ✅ 관계 삭제
            noteTagRepository.deleteAll(noteTags);

            // ✅ 노트 상태 변경 (소프트 삭제)
            note.setStatus("DELETED");
            noteRepository.save(note);

            res.put("success", true);
            res.put("message", "삭제되었습니다.");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }

    @GetMapping("/api/notion/download/{noteIdx}")
    public void downloadNote(
        @PathVariable Long noteIdx,
        Authentication auth,
        HttpServletResponse response
    ) throws IOException {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        Note note = noteRepository.findById(noteIdx)
            .orElseThrow(() -> new IllegalArgumentException("노트를 찾을 수 없습니다."));
        
        if (!note.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        
        String filename = URLEncoder.encode(note.getTitle() + ".md", StandardCharsets.UTF_8)
            .replaceAll("\\+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename);
        response.setContentType("text/markdown;charset=UTF-8");
        response.getWriter().write(note.getContent());
        response.getWriter().flush();
    }

    @GetMapping("/test-vllm")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testVllm() {
        try {
            String result = llmService.testVllmConnection();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "result", result
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
    
}