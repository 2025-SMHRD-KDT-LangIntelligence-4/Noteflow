package com.smhrd.web.service;

import com.smhrd.web.entity.Attachment;
import com.smhrd.web.entity.Note;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.AttachmentRepository;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotionContentService {

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final AttachmentRepository attachmentRepository;
    private final VllmApiService vllmApiService;
    private final FileStorageService fileStorageService;

    // --------------------------
    // 텍스트로 노션 생성
    // --------------------------
    @Transactional
    public Long createNotionFromText(String userId, String title, String content, String notionType) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // AI 노션 변환
        String aiGeneratedContent = vllmApiService.generateNotion(content, notionType);

        // 테이블에 저장
        Note note = Note.builder()
                .user(user)
                .title(title)
                .content(aiGeneratedContent)
                .isPublic(false)
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .reportCount(0)
                .status("ACTIVE")
                .aiPromptId(notionType)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Note savedNote = noteRepository.save(note);
        return savedNote.getNoteIdx();
    }

    // --------------------------
    // 파일로 노션 생성
    // --------------------------
    @Transactional
    public Long createNotionFromFile(String userId, MultipartFile file, String notionType, String customTitle) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        try {
            // MongoDB GridFS에 파일 저장 (subDir 파라미터 제거)
            String mongoFileId = fileStorageService.storeFile(file, userId);

            // 파일 메타 정보
            String originalName = file.getOriginalFilename();
            String fileExtension = "";
            if (originalName != null && originalName.contains(".")) {
                fileExtension = originalName.substring(originalName.lastIndexOf('.') + 1);
            }

            // 파일 내용 간단 추출
            String textContent = "파일: " + originalName + "\n크기: " + file.getSize() + " bytes";

            // AI로 노션 생성
            String aiGeneratedContent = vllmApiService.generateNotion(textContent, notionType);

            // 제목 결정
            String finalTitle = (customTitle != null && !customTitle.isBlank())
                    ? customTitle
                    : originalName;

            // Note 저장
            Note note = Note.builder()
                    .user(user)
                    .title(finalTitle)
                    .content(aiGeneratedContent)
                    .isPublic(false)
                    .viewCount(0)
                    .likeCount(0)
                    .commentCount(0)
                    .reportCount(0)
                    .status("ACTIVE")
                    .aiPromptId(notionType)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            Note savedNote = noteRepository.save(note);

            // Attachment 저장
            Attachment attachment = Attachment.builder()
                    .note(savedNote)
                    .originalFilename(originalName)
                    .storedFilename(originalName)
                    .fileExtension(fileExtension)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .mongoDocId(mongoFileId)
                    .downloadCount(0)
                    .status("ACTIVE")
                    .expiresAt(LocalDateTime.now().plusYears(1))
                    .createdAt(LocalDateTime.now())
                    .build();
            attachmentRepository.save(attachment);

            // 사용자 첨부파일 카운트 증가
            user.setAttachmentCount(user.getAttachmentCount() + 1);
            userRepository.save(user);

            return savedNote.getNoteIdx();

        } catch (Exception e) {
            throw new RuntimeException("파일 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    // --------------------------
    // 노션 목록 조회
    // --------------------------
    public List<Note> getNotionList(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        return noteRepository.findByUserAndStatusOrderByCreatedAtDesc(user, "ACTIVE");
    }

    // --------------------------
    // 노션 상세 조회
    // --------------------------
    @Transactional
    public Note getNotionDetail(Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 노션입니다."));

        note.incrementViewCount();
        noteRepository.save(note);

        return note;
    }

    // --------------------------
    // 권한처리
    // --------------------------
    public boolean isOwner(Long noteId, String userId) {
        return noteRepository.findById(noteId)
                .map(note -> note.getUser().getUserId().equals(userId))
                .orElse(false);
    }
}