package com.smhrd.web.controller;

import com.smhrd.web.entity.Note;
import com.smhrd.web.service.NotionContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class NotionController {

    private final NotionContentService notionContentService;

    // --------------------------
    // 텍스트로 노션 요약 생성
    // --------------------------
    @PostMapping("/api/notion/create-text")
    @ResponseBody
    public String createNotionFromText(@RequestParam("title") String title,
                                       @RequestParam("content") String content,
                                       @RequestParam("notionType") String notionType,
                                       Authentication authentication) {
        try {
            String userId = authentication.getName();
            Long noteId = notionContentService.createNotionFromText(userId, title, content, notionType);
            return "success:" + noteId;
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    // --------------------------
    // 파일로 노션 생성
    // --------------------------
    @PostMapping("/notion/create-file")
    public String createNotionFromFile(@RequestParam("file") MultipartFile file,
                                       @RequestParam("notionType") String notionType,
                                       @RequestParam(value = "customTitle", required = false) String customTitle,
                                       Authentication authentication) {
        try {
            String userId = authentication.getName();
            Long noteId = notionContentService.createNotionFromFile(userId, file, notionType, customTitle);
            return "redirect:/notion/complete?noteId=" + noteId;
        } catch (Exception e) {
            return "redirect:/notion/create?error=" + e.getMessage();
        }
    }

    // --------------------------
    // 목록 조회
    // --------------------------
    @GetMapping("/api/notion/list")
    @ResponseBody
    public List<Note> getNotionList(Authentication authentication) {
        String userId = authentication.getName();
        return notionContentService.getNotionList(userId);
    }

    // --------------------------
    // 노션 상세 조회
    // --------------------------
    @GetMapping("/api/notion/{noteId}")
    @ResponseBody
    public Note getNotionDetail(@PathVariable Long noteId, Authentication authentication) {
        String userId = authentication.getName();

        // 권한 확인
        if (!notionContentService.isOwner(noteId, userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        return notionContentService.getNotionDetail(noteId);
    }
}