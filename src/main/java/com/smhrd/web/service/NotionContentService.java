package com.smhrd.web.service;

import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
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
	private final FileStorageService fileStorageService;
	private final TagRepository tagRepository;
	private final NoteTagRepository noteTagRepository;

	// ✅ 통합된 LLM 처리 서비스 (요약 JSON만 반환)
	private final LLMUnifiedService llmUnifiedService;

	// ------------------------------------------------------------
	// [1] 텍스트로 노션 생성
	// ------------------------------------------------------------
	@Transactional
	public Long createNotionFromText(long userIdx, String title, String content, String notionType) {
		User user = userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

		// 1) LLM 요약(JSON)
		LLMUnifiedService.UnifiedResult llm = llmUnifiedService.summarizeText(userIdx, content, notionType);

		// 2) 카테고리 매칭 → 폴더 경로 확정
		LLMUnifiedService.CategoryPath finalPath =
				llmUnifiedService.matchCategory(llm.getKeywords(), llm.getCategory());

		Long folderId = llmUnifiedService.ensureNoteFolderPath(userIdx, finalPath);

		// 3) 노트 저장
		Note note = Note.builder()
				.user(user)
				.title((title != null && !title.isBlank()) ? title
						: String.format("[%s/%s/%s] 요약본",
						finalPath.getLarge(), finalPath.getMedium(), finalPath.getSmall()))
				.content(llm.getSummary())
				.folderId(folderId)
				.isPublic(false)
				.status("ACTIVE")
				.viewCount(0).likeCount(0).commentCount(0).reportCount(0)
				.aiPromptId(notionType)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.build();
		Note savedNote = noteRepository.save(note);

		// 4) 태그 연결 (tags 우선, 없으면 keywords)
		List<String> tags = (llm.getTags() != null && !llm.getTags().isEmpty()) ? llm.getTags() : llm.getKeywords();
		attachTags(savedNote, tags);

		return savedNote.getNoteIdx();
	}

	// ------------------------------------------------------------
	// [2] 파일로 노션 생성
	// ------------------------------------------------------------
	@Transactional
	public Long createNotionFromFile(long userIdx, MultipartFile file, String notionType, String customTitle) {
		User user = userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

		try {
			// 1) 파일 저장
			String mongoFileId = fileStorageService.storeFile(file, userIdx);

			// 2) LLM 요약(JSON)
			LLMUnifiedService.UnifiedResult llm = llmUnifiedService.summarizeFile(userIdx, file, notionType);

			// 3) 카테고리 매칭 → 폴더 경로 확정
			LLMUnifiedService.CategoryPath finalPath =
					llmUnifiedService.matchCategory(llm.getKeywords(), llm.getCategory());

			Long folderId = llmUnifiedService.ensureNoteFolderPath(userIdx, finalPath);

			// 4) 제목 결정
			String finalTitle = (customTitle != null && !customTitle.isBlank())
					? customTitle : file.getOriginalFilename();

			// 5) Note 저장
			Note note = Note.builder()
					.user(user)
					.title(finalTitle)
					.content(llm.getSummary())
					.folderId(folderId)
					.isPublic(false)
					.status("ACTIVE")
					.viewCount(0).likeCount(0).commentCount(0).reportCount(0)
					.aiPromptId(notionType)
					.createdAt(LocalDateTime.now())
					.updatedAt(LocalDateTime.now())
					.build();
			Note savedNote = noteRepository.save(note);

			// 6) 첨부 저장
			String ext = getFileExtension(file.getOriginalFilename());
			Attachment attachment = Attachment.builder()
					.note(savedNote)
					.originalFilename(file.getOriginalFilename())
					.storedFilename(file.getOriginalFilename())
					.fileExtension(ext)
					.fileSize(file.getSize())
					.mimeType(file.getContentType())
					.mongoDocId(mongoFileId)
					.downloadCount(0)
					.status("ACTIVE")
					.expiresAt(LocalDateTime.now().plusYears(1))
					.createdAt(LocalDateTime.now())
					.build();
			attachmentRepository.save(attachment);

			// 7) 태그 연결
			List<String> tags = (llm.getTags() != null && !llm.getTags().isEmpty()) ? llm.getTags() : llm.getKeywords();
			attachTags(savedNote, tags);

			return savedNote.getNoteIdx();
		} catch (Exception e) {
			throw new RuntimeException("파일 기반 노트 생성 실패: " + e.getMessage(), e);
		}
	}

	private void attachTags(Note note, List<String> tags) {
		if (tags == null) return;
		tags.stream()
				.filter(t -> t != null && !t.isBlank())
				.map(String::trim).map(String::toLowerCase)
				.distinct().limit(5)
				.forEach(tagName -> {
					Tag tag = tagRepository.findByName(tagName)
							.orElseGet(() -> tagRepository.save(Tag.builder().name(tagName).build()));
					noteTagRepository.save(NoteTag.builder().note(note).tag(tag).build());
				});
	}

	private String getFileExtension(String filename) {
		if (filename == null || !filename.contains(".")) return "";
		return filename.substring(filename.lastIndexOf('.') + 1);
	}

	// ------------------------------------------------------------
	// 임시용 saveNote (user_idx 기반)
	// ------------------------------------------------------------
	@Transactional
	public Long saveNote(
			Long userIdx,
			String title,
			String content,
			Long promptId) {

		// 1) User 엔티티 조회 (안전 확인)
		User user = userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userIdx));

		// 2) Note 엔티티 생성
		Note note = Note.builder()
				.user(user)
				.title(title)
				.content(content)
				.aiPromptId(String.valueOf(promptId))
				.viewCount(0)
				.likeCount(0)
				.commentCount(0)
				.reportCount(0)
				.status("ACTIVE")
				.isPublic(false)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.build();

		// 3) 저장 및 반환
		Note saved = noteRepository.save(note);
		return saved.getNoteIdx();
	}

}
