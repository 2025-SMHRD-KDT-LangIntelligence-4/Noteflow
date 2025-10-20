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

	public void syncNoteTags(Note note, List<String> keywords) {
		// 기존 태그 관계 삭제
		noteTagRepository.deleteByNote(note);

		// 새 태그 저장 및 연결
		for (String keyword : keywords) {
			Tag tag = tagRepository.findByName(keyword)
					.orElseGet(() -> {
						Tag newTag = new Tag();
						newTag.setName(keyword);
						newTag.setUsageCount(0);
						return tagRepository.save(newTag);
					});

			// usage_count 증가
			tagRepository.bumpUsage(tag.getTagIdx(), 1);

			// 노트-태그 연결
			NoteTag noteTag = new NoteTag();
			noteTag.setNote(note);
			noteTag.setTag(tag);
			noteTagRepository.save(noteTag);
		}
	}
}
