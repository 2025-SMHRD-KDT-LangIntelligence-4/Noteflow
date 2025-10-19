package com.smhrd.web.controller;

import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.Note;
import com.smhrd.web.entity.NoteTag;
import com.smhrd.web.entity.Prompt;
import com.smhrd.web.entity.Tag;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.repository.NoteTagRepository;
import com.smhrd.web.repository.PromptRepository;
import com.smhrd.web.repository.TagRepository;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.*;

import jakarta.servlet.http.HttpServletResponse;
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


    /**
     * 노트 저장 (자동 카테고리 추출 + 폴더 생성)
     * POST /notion/save-note
     */
    @PostMapping("/save-note")
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveNote(
            @RequestBody Map<String, String> req,
            Authentication auth
    ) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        String title = req.getOrDefault("title", "제목없음");
        String summary = req.getOrDefault("summary", "");
        Long promptId = Long.parseLong(req.getOrDefault("promptId", "0"));

        Map<String, Object> res = new HashMap<>();
        try {
            // 1) 노트 저장
            Long noteId = notionContentService.saveNote(userIdx, title, summary, promptId);

            // 2) ✅ 키워드/카테고리 자동 추출 (공개 + 본인 카테고리 기준)
            CategoryResult categoryResult = keywordExtractionService
                    .extractAndClassifyWithRAG(title, summary, userIdx);  // ✅ userIdx 전달

            // 키워드 추출 (최대 5개)
            List<String> keywords = new ArrayList<>(categoryResult.getExtractedKeywords());
            if (keywords.size() > 5) {
                keywords = keywords.subList(0, 5);
            }

            // 3) ✅ 폴더 생성/조회 (AutoFolderService가 대/중/소 추출)
            Long folderId = null;
            if (categoryResult.hasCategory()) {
                folderId = autoFolderService.createOrFindFolder(userIdx, categoryResult);

                if (folderId != null) {
                    noteRepository.updateNoteFolderId(noteId, folderId);
                    log.info("노트 {} → 폴더 {} 연결", noteId, folderId);
                }
            }

            // 4) 원본 파일 저장 (GridFS)
            String sourceGridId = fileStorageService.storeTextAsFile(
                    title + ".md",
                    summary,
                    userIdx,
                    folderId
            );
            noteRepository.updateNoteSourceId(noteId, sourceGridId);

            // 5) 응답
            res.put("success", true);
            res.put("message", "노트가 저장되었습니다.");
            res.put("noteId", noteId);
            res.put("keywords", keywords);
            res.put("categoryPath", categoryResult.generateFolderPath());  // ✅ 개선
            res.put("folderId", folderId);

            return ResponseEntity.ok(res);

        } catch (Exception e) {
            log.error("노트 저장 오류: {}", e.getMessage(), e);
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
    
}