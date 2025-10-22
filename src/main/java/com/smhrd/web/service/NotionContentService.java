package com.smhrd.web.service;

import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotionContentService {

	private final UserRepository userRepository;
	private final NoteRepository noteRepository;
	private final TagRepository tagRepository;
	private final NoteTagRepository noteTagRepository;
	private final LLMUnifiedService llmUnifiedService;
	private final MongoTemplate mongoTemplate;
	private final NoteFolderRepository noteFolderRepository;

	@Qualifier("embeddingClient")
	private final WebClient embeddingClient;

	// ✅ 기존 saveNote 메서드 (MongoDB 저장 추가)
	@Transactional
	public Long saveNote(Long userIdx, String title, String content, Long folderId) {
		User user = userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

		Note note = Note.builder()
				.user(user)
				.title(title)
				.content(content)
				.folderId(folderId)
				.isPublic(false)
				.status("ACTIVE")
				.viewCount(0)
				.likeCount(0)
				.commentCount(0)
				.reportCount(0)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.build();

		Note savedNote = noteRepository.save(note);

		// ✅ MongoDB에도 저장
		saveToMongoDB(userIdx, savedNote.getNoteIdx(), title, content, null);

		return savedNote.getNoteIdx();
	}

	@Transactional
	public Long createNotionFromText(long userIdx, String title, String content, String notionType) {
		User user = userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

		LLMUnifiedService.UnifiedResult unified = llmUnifiedService.summarizeText(userIdx, content, notionType);
		LLMUnifiedService.CategoryPath finalCat = llmUnifiedService.matchCategory(unified.getKeywords(), unified.getCategory());
		Long folderId = llmUnifiedService.ensureNoteFolderPath(userIdx, finalCat);

		Note note = Note.builder()
				.user(user)
				.title(title)
				.content(content)
				.folderId(folderId)
				.isPublic(false)
				.status("ACTIVE")
				.viewCount(0)
				.likeCount(0)
				.commentCount(0)
				.reportCount(0)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.build();

		Note savedNote = noteRepository.save(note);
		saveTags(savedNote, unified.getTags());
		saveToMongoDB(user.getUserIdx(), savedNote.getNoteIdx(), title, content, unified.getSummary());

		return savedNote.getNoteIdx();
	}

	@Transactional
	public Long createNotionFromFile(long userIdx, MultipartFile file, String notionType) throws Exception {
		User user = userRepository.findByUserIdx(userIdx)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

		LLMUnifiedService.UnifiedResult unified = llmUnifiedService.summarizeFile(userIdx, file, notionType);
		LLMUnifiedService.CategoryPath finalCat = llmUnifiedService.matchCategory(unified.getKeywords(), unified.getCategory());
		Long folderId = llmUnifiedService.ensureNoteFolderPath(userIdx, finalCat);

		String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Untitled";

		Note note = Note.builder()
				.user(user)
				.title(fileName)
				.content("[파일 업로드]")
				.folderId(folderId)
				.isPublic(false)
				.status("ACTIVE")
				.viewCount(0)
				.likeCount(0)
				.commentCount(0)
				.reportCount(0)
				.createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now())
				.build();

		Note savedNote = noteRepository.save(note);
		saveTags(savedNote, unified.getTags());
		saveToMongoDB(user.getUserIdx(), savedNote.getNoteIdx(), fileName, unified.getSummary(), unified.getSummary());

		return savedNote.getNoteIdx();
	}

	private void saveTags(Note note, List<String> tags) {
		if (tags == null || tags.isEmpty()) return;

		for (String tagName : tags) {
			if (tagName == null || tagName.isBlank()) continue;

			Tag tag = tagRepository.findByName(tagName)
					.orElseGet(() -> {
						Tag newTag = Tag.builder()
								.name(tagName)
								.usageCount(0)
								.build();
						return tagRepository.save(newTag);
					});

			NoteTag noteTag = NoteTag.builder()
					.note(note)
					.tag(tag)
					.build();

			noteTagRepository.save(noteTag);
		}
	}

	private void saveToMongoDB(long userIdx, Long noteIdx, String title, String content, String summary) {
		try {
			log.info("MongoDB 저장 - noteIdx: {}", noteIdx);

			List<Float> embedding = generateEmbedding(summary != null ? summary : content);

			// MySQL에서 태그와 폴더 정보 가져오기
			Note note = noteRepository.findById(noteIdx).orElse(null);
			List<String> tags = new ArrayList<>();
			String category = null;

			if (note != null) {
				// ✅ 태그 가져오기
				tags = noteTagRepository.findByNote(note).stream()
						.map(nt -> nt.getTag().getName())
						.collect(Collectors.toList());

				// ✅ 폴더(카테고리) 경로 생성
				if (note.getFolderId() != null) {
					category = buildFolderPath(note.getFolderId());
				}
			}

			Document doc = new Document();
			doc.put("user_idx", userIdx);
			doc.put("note_idx", noteIdx);
			doc.put("title", title);
			doc.put("content", content);
			doc.put("summary", summary);
			doc.put("tags", tags);
			doc.put("category", category);
			doc.put("embedding", embedding);
			doc.put("created_at", LocalDateTime.now());

			mongoTemplate.save(doc, "user_notes");
			log.info("MongoDB 저장 성공 (태그: {}, 카테고리: {})", tags, category);

		} catch (Exception e) {
			log.error("MongoDB 저장 실패: {}", e.getMessage(), e);
		}
	}

	// ✅ 폴더 경로 생성 헬퍼 메서드
	private String buildFolderPath(Long folderId) {
		List<String> path = new ArrayList<>();
		Long currentId = folderId;

		// 최대 10단계까지만 (무한루프 방지)
		for (int i = 0; i < 10 && currentId != null; i++) {
			Optional<NoteFolder> folder = noteFolderRepository.findById(currentId);
			if (folder.isPresent()) {
				path.add(0, folder.get().getFolderName()); // 앞에 추가
				currentId = folder.get().getParentFolderId();
			} else {
				break;
			}
		}

		return path.isEmpty() ? null : String.join(" > ", path);
	}


	private List<Float> generateEmbedding(String text) {
		try {
			if (text == null || text.isBlank()) {
				return new ArrayList<>();
			}

			if (text.length() > 1000) {
				text = text.substring(0, 1000);
			}

			Map<String, Object> response = embeddingClient.post()
					.uri("/embed")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(Map.of("texts", List.of(text)))
					.retrieve()
					.bodyToMono(Map.class)
					.block();

			if (response == null) return new ArrayList<>();

			List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
			if (embeddings == null || embeddings.isEmpty()) return new ArrayList<>();

			return embeddings.get(0).stream()
					.map(Double::floatValue)
					.collect(Collectors.toList());

		} catch (Exception e) {
			log.error("임베딩 생성 실패: {}", e.getMessage());
			return new ArrayList<>();
		}
	}
	@Transactional
	public void syncNoteTags(Note note, List<String> tagNames) {
	    if (tagNames == null || tagNames.isEmpty()) {
	        return;
	    }

	    // 기존 태그 전부 삭제
	    noteTagRepository.deleteByNote(note);
	    noteTagRepository.flush(); // ⭐ 강제 반영
	    
	    // 새 태그 추가
	    for (String tagName : tagNames) {
	        if (tagName == null || tagName.isBlank()) continue;

	        Tag tag = tagRepository.findByName(tagName)
	                .orElseGet(() -> {
	                    try {
	                        Tag newTag = Tag.builder()
	                                .name(tagName)
	                                .usageCount(0)
	                                .build();
	                        return tagRepository.save(newTag);
	                    } catch (DataIntegrityViolationException e) {
	                        // 동시성 문제로 이미 생성되었을 수 있음
	                        return tagRepository.findByName(tagName)
	                                .orElseThrow(() -> new RuntimeException("태그 조회 실패"));
	                    }
	                });

	        // ⭐ 중복 체크 (안전장치)
	        if (!noteTagRepository.existsByNoteAndTag(note, tag)) {
	            NoteTag noteTag = NoteTag.builder()
	                    .note(note)
	                    .tag(tag)
	                    .build();
	            noteTagRepository.save(noteTag);
	        } else {
	            log.warn("⚠️ 이미 연결된 태그 건너뜀: note={}, tag={}", note.getNoteIdx(), tag.getName());
	        }
	    }
	}
}
