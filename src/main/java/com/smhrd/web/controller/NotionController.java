
package com.smhrd.web.controller;

// ── Project Entities & Repositories & Services ────────────────────────────────
import com.smhrd.web.entity.Attachment;
import com.smhrd.web.entity.Note;
import com.smhrd.web.repository.AttachmentRepository;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.service.FileStorageService;
import com.smhrd.web.service.NotionContentService;
import com.smhrd.web.service.UnifiedFolderService;

// ── Lombok ───────────────────────────────────────────────────────────────────
import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.Setter;

// ── Spring Framework ─────────────────────────────────────────────────────────
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// ── Servlet API ──────────────────────────────────────────────────────────────
import jakarta.servlet.http.HttpServletResponse;

// ── Java Standard Library ────────────────────────────────────────────────────
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
@RequiredArgsConstructor
public class NotionController {

	private final NotionContentService notionContentService;
	private final FileStorageService fileStorageService;
	private final UnifiedFolderService unifiedFolderService;
	private final AttachmentRepository attachmentRepository;
	private final NoteRepository noteRepository;

	// --------------------------
	// 텍스트로 노션 요약 생성
	// --------------------------
	@PostMapping("/api/notion/create-text")
	@ResponseBody
	public String createNotionFromText(@RequestParam("title") String title, @RequestParam("content") String content,
			@RequestParam("notionType") String notionType, Authentication authentication) {
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
			@RequestParam(value = "customTitle", required = false) String customTitle, Authentication authentication) {
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

//	@GetMapping("/api/files/preview/{fileId}")
//	@ResponseBody
//	public String getFilePreview(@PathVariable String fileId, Authentication auth) {
//		return fileStorageService.getFilePreview(fileId, auth.getName());
//	}  중복된거제거

	@GetMapping("/api/notion/download/{noteId}")
	public void downloadNote(@PathVariable Long noteId, Authentication auth, HttpServletResponse response)
			throws IOException {
		String userId = auth.getName();
		Note note = noteRepository.findById(noteId).orElseThrow(() -> new IllegalArgumentException("노트 없음"));
		if (!note.getUser().getUserId().equals(userId)) {
			response.sendError(403, "권한 없음");
			return;
		}
		List<Attachment> atts = attachmentRepository.findByNoteAndStatusOrderByCreatedAtDesc(note, "ACTIVE");
		if (atts.isEmpty()) {
			response.sendError(404, "첨부 없음");
			return;
		}
		if (atts.size() == 1) {
			Attachment a = atts.get(0);
			byte[] data = fileStorageService.downloadFile(a.getMongoDocId());
			String fname = URLEncoder.encode(a.getOriginalFilename(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
			response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fname);
			response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
			response.setContentLength(data.length);
			response.getOutputStream().write(data);
		} else {
			response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
					+ URLEncoder.encode(note.getTitle() + ".zip", StandardCharsets.UTF_8).replaceAll("\\+", "%20"));
			response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
			try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
				for (Attachment a : atts) {
					String entry = URLEncoder.encode(a.getOriginalFilename(), StandardCharsets.UTF_8).replaceAll("\\+",
							"%20");
					zos.putNextEntry(new ZipEntry(entry));
					byte[] data = fileStorageService.downloadFile(a.getMongoDocId());
					zos.write(data);
					zos.closeEntry();
				}
				zos.finish();
			}
		}
	}

	@PutMapping("/api/notion/{noteId}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> updateNotion(@PathVariable Long noteId,
			@RequestBody NoteUpdateRequest req, Authentication authentication) {
		String userId = authentication.getName();
		notionContentService.updateNote(userId, noteId, req.getTitle(), req.getContent(), req.getIsPublic());
		return ResponseEntity.ok(Map.of("success", true, "noteId", noteId));
	}

	// 요청 DTO
	@Getter
	@Setter
	static class NoteUpdateRequest {
		private String title; // 선택
		private String content; // 선택
		private Boolean isPublic;
	} // 선택

	@PutMapping("/api/notion/{noteId}/move")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> moveNotion(@PathVariable Long noteId,
			@RequestParam(required = false) Long targetFolderId, Authentication authentication) {
		String userId = authentication.getName();
		unifiedFolderService.moveNoteToFolder(userId, noteId, targetFolderId);
		return ResponseEntity.ok(Map.of("success", true, "noteId", noteId, "targetFolderId", targetFolderId));

	}

}