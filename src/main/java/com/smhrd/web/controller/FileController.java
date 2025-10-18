package com.smhrd.web.controller;

import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.entity.Note;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.FileMetadataRepository;
import com.smhrd.web.repository.FolderRepository;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.FileStorageService;
import com.smhrd.web.service.FileStorageService.FileInfo;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
@RestController
@RequiredArgsConstructor
public class FileController {

	private final FileStorageService fileStorageService;
	private final FolderRepository folderRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final NoteRepository noteRepository;
	// 파일 메타정보로 크기제한
	@GetMapping("/api/files/preview-meta/{id}")
	public ResponseEntity<Map<String, Object>> previewMeta(@PathVariable String id,
			@AuthenticationPrincipal(expression = "userIdx") Long userIdx,
			@Value("${notion.summary.block-file-size-mb:0.5}") int blockMb) {
		FileStorageService.FileInfo meta = fileStorageService.previewFile(id);
		if (meta == null)
			return ResponseEntity.ok(Map.of("success", false, "message", "파일을 찾을 수 없습니다."));

		// 프리뷰 텍스트(2,000자 절삭)는 그대로 사용: 화면 미리보기 용
		String previewText = fileStorageService.getFilePreview(id, userIdx);
		boolean truncated = previewText != null && previewText.endsWith("... (내용이 더 있습니다)");

		long sizeBytes = meta.getSize();
		boolean blocked = sizeBytes >= (long) blockMb * 1024 * 1024; // ✅ 크기 기반 차단

		Map<String, Object> body = new HashMap<>();
		body.put("success", true);
		body.put("text", previewText == null ? "" : previewText);
		body.put("truncated", truncated);
		body.put("sizeBytes", sizeBytes);
		body.put("blocked", blocked);
		return ResponseEntity.ok(body);
	}

	// ─────────────────────────────────────────────────────────────────────
	// 파일 업로드: 선택 폴더로 저장 (folderId 없으면 루트)
	// ─────────────────────────────────────────────────────────────────────
	@PostMapping("/api/files/upload")
	public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "folderId", required = false) String folderId, Authentication auth) {
		Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
		Map<String, Object> result = new HashMap<>();
		try {
			if (file == null || file.isEmpty()) {
				result.put("success", false);
				result.put("message", "빈 파일입니다.");
				return result;
			}
			String gridfsId = fileStorageService.storeFile(file, userIdx, folderId);
			result.put("success", true);
			result.put("gridfsId", gridfsId);
			result.put("folderId", folderId);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}

	// ─────────────────────────────────────────────────────────────────────
	// 파일 미리보기
	// - PDF : 브라우저 내장 뷰어로 'inline' 표시 (열람/인쇄)
	// - 그 외 지원 포맷(doc/docx/txt/md/json/csv/xml/log) : 텍스트 추출 결과
	// - HWP : 정책상 파일탭에서는 텍스트 미리보기 미지원 → 안내 메시지
	// ─────────────────────────────────────────────────────────────────────
	@GetMapping("/api/files/preview/{fileId}")
	public ResponseEntity<?> previewFile(@PathVariable String fileId, Authentication auth) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();

		try {
			Optional<FileMetadata> metaOpt = fileMetadataRepository.findByGridfsId(fileId);
			if (metaOpt.isEmpty()) {
				return ResponseEntity.notFound().build();
			}

			FileMetadata meta = metaOpt.get();
			if (!meta.getUserIdx().equals(userIdx)) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}

			byte[] data = fileStorageService.downloadFile(meta.getGridfsId());
			String fileName = meta.getOriginalName();
			String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

			// ========== PDF 파일 ==========
			if ("pdf".equals(ext)) {
				return ResponseEntity.ok()
						.contentType(MediaType.APPLICATION_PDF)
						.body(data);
			}

			// ========== 이미지 파일 ==========
			if (Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg").contains(ext)) {
				String mimeType = "image/" + ext.replace("jpg", "jpeg");
				return ResponseEntity.ok()
						.contentType(MediaType.parseMediaType(mimeType))
						.body(data);
			}

			// ========== Excel/CSV 파일 (바이너리 그대로 반환) ==========
			if (Arrays.asList("xlsx", "xls", "csv").contains(ext)) {
				return ResponseEntity.ok()
						.contentType(MediaType.APPLICATION_OCTET_STREAM)
						.body(data);
			}

			// ========== DOCX 파일 (텍스트 추출) ==========
			if ("docx".equals(ext)) {
				try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
					 XWPFDocument doc = new XWPFDocument(bis)) {

					XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
					String text = extractor.getText();

					return ResponseEntity.ok()
							.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
							.body(text);

				} catch (Exception e) {
					System.err.println("DOCX 처리 오류: " + e.getMessage());
					e.printStackTrace();
					return ResponseEntity.ok()
							.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
							.body("DOCX 파일을 처리할 수 없습니다.");
				}
			}

			// ========== DOC 파일 (텍스트 추출) ==========
			if ("doc".equals(ext)) {
				try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
					 HWPFDocument doc = new HWPFDocument(bis)) {

					WordExtractor extractor = new WordExtractor(doc);
					String text = extractor.getText();

					return ResponseEntity.ok()
							.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
							.body(text);

				} catch (Exception e) {
					System.err.println("DOC 처리 오류: " + e.getMessage());
					return ResponseEntity.ok()
							.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
							.body("DOC 파일을 처리할 수 없습니다.");
				}
			}

			// ========== HWP 파일 (안내 메시지) ==========
			if ("hwp".equals(ext)) {
				return ResponseEntity.ok()
						.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
						.body("HWP 파일은 미리보기를 지원하지 않습니다.\n다운로드하여 확인해주세요.");
			}

			// ========== 텍스트 파일 (txt, md, log, json, xml, html, css, js, java, py 등) ==========
			return ResponseEntity.ok()
					.contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
					.body(new String(data, StandardCharsets.UTF_8));

		} catch (Exception e) {
			System.err.println("파일 미리보기 오류: " + e.getMessage());
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("파일을 불러올 수 없습니다.");
		}
	}


	@GetMapping("/api/files/preview-text/{id}")

	public ResponseEntity<String> previewText(@PathVariable String id,
			@AuthenticationPrincipal(expression = "userIdx") Long userIdx) {
		String text = fileStorageService.getFilePreview(id, userIdx);
		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
				.body((text == null || text.isBlank()) ? "[안내] 본문 텍스트를 찾지 못했습니다." : text);
	}
	
	// ─────────────────────────────────────────────────────────────────────
	// 파일 수정
	// ─────────────────────────────────────────────────────────────────────
	@PutMapping("/api/files/update/{gridfsId}")
	@ResponseBody
	public Map<String, Object> updateFileContent(
	    @PathVariable String gridfsId,
	    @RequestBody Map<String, String> payload,
	    Authentication auth
	) {
	    Map<String, Object> result = new HashMap<>();
	    try {
	        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
	        
	        Optional<FileMetadata> metaOpt = fileMetadataRepository.findByGridfsId(gridfsId);
	        if (metaOpt.isEmpty()) {
	            result.put("success", false);
	            result.put("message", "파일을 찾을 수 없습니다.");
	            return result;
	        }
	        
	        FileMetadata meta = metaOpt.get();
	        if (!meta.getUserIdx().equals(userIdx)) {
	            result.put("success", false);
	            result.put("message", "권한이 없습니다.");
	            return result;
	        }
	        
	        String newContent = payload.get("content");
	        String filename = meta.getOriginalName();
	        String folderId = meta.getFolderId();
	        
	        // 기존 파일 삭제
	        fileStorageService.deleteFile(gridfsId);
	        
	        // 새 파일 업로드
	        String newGridfsId = fileStorageService.storeTextAsFile(filename, newContent, userIdx, folderId);
	        
	        result.put("success", true);
	        result.put("newGridfsId", newGridfsId);
	        result.put("message", "파일이 수정되었습니다.");
	    } catch (Exception e) {
	        result.put("success", false);
	        result.put("message", e.getMessage());
	    }
	    return result;
	}

	// ─────────────────────────────────────────────────────────────────────
	// 파일 다운로드 (본인만)
	// ─────────────────────────────────────────────────────────────────────
	@GetMapping("/api/files/download/{id}")
	public ResponseEntity<byte[]> downloadFile(@PathVariable String id, Authentication auth) throws IOException {
		FileInfo info = fileStorageService.previewFile(id);
		if (info == null) {
			return ResponseEntity.notFound().build();
		}
		Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
		if (!String.valueOf(userIdx).equals(info.getUploaderIdx())) {
			return ResponseEntity.status(403).build();
		}
		byte[] data = fileStorageService.downloadFile(id);
		String encodedName = URLEncoder.encode(info.getOriginalName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName);
		headers.setContentLength(data.length);
		return ResponseEntity.ok().headers(headers).body(data);
	}

	// ─────────────────────────────────────────────────────────────────────
	// 파일 목록 조회 (본인 파일만) - userIdx/String 타입 불일치 수정
	// ─────────────────────────────────────────────────────────────────────
	@GetMapping("/api/files/list")
	public List<FileInfo> listFiles(Authentication auth) {
		Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
		return fileStorageService.listFiles().stream()
				.filter(info -> String.valueOf(userIdx).equals(info.getUploaderIdx())).collect(Collectors.toList());
	}

	// ─────────────────────────────────────────────────────────────────────
	// 다중 파일 ZIP 다운로드 (본인 것만)
	// ─────────────────────────────────────────────────────────────────────

	@Data
	public static class ZipDownloadRequest {
		private List<String> folderIds = new ArrayList<>();
		private List<String> fileIds = new ArrayList<>();
		private List<Long> noteIds = new ArrayList<>();  // ✅ 추가
		private List<Map<String, Object>> folderStructure = new ArrayList<>();  // ✅ 추가
	}

	@PostMapping("/api/files/download-zip")
	public void downloadZip(
			@RequestBody ZipDownloadRequest request,
			Authentication auth,
			HttpServletResponse response) throws IOException {

		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();

		System.out.println("=== ZIP Download Request ===");
		System.out.println("User ID: " + userIdx);
		System.out.println("File IDs: " + request.getFileIds());

		List<String> authorized = new ArrayList<>();

		if (request.getFileIds() != null) {
			for (String requestedId : request.getFileIds()) {
				// ✅ 1. MongoDB _id로 검색
				Optional<FileMetadata> metaOpt = fileMetadataRepository.findById(requestedId);

				if (metaOpt.isPresent()) {
					FileMetadata meta = metaOpt.get();
					if (meta.getUserIdx().equals(userIdx) && meta.getGridfsId() != null) {
						authorized.add(meta.getId());
						System.out.println("✅ Found by ID: " + meta.getId());
						continue;
					}
				}

				// ✅ 2. gridfsId로 검색
				Optional<FileMetadata> byGridfs = fileMetadataRepository.findByGridfsId(requestedId);

				if (byGridfs.isPresent()) {
					FileMetadata meta = byGridfs.get();
					if (meta.getUserIdx().equals(userIdx)) {
						authorized.add(meta.getId());
						System.out.println("✅ Found by GridfsId: " + meta.getId() + " (gridfsId: " + requestedId + ")");
						continue;
					}
				}

				System.out.println("❌ NOT FOUND: " + requestedId);
			}
		}

		System.out.println("Final Authorized IDs: " + authorized);

		if (authorized.isEmpty()) {
			System.out.println("⚠️ 파일을 찾을 수 없습니다!");
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		String zipName = URLEncoder.encode("files.zip", StandardCharsets.UTF_8)
				.replaceAll("\\+", "%20");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename*=UTF-8''" + zipName);
		response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

		try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
			fileStorageService.writeFilesAsZip(authorized, zos);
		}
	}
	@PostMapping("/api/files/download-folder-zip")
	public void downloadFolderZip(
			@RequestBody ZipDownloadRequest request,
			Authentication auth,
			HttpServletResponse response) throws IOException {

		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();

		System.out.println("=== Folder ZIP Download Request ===");

		String zipName = URLEncoder.encode("folder.zip", StandardCharsets.UTF_8)
				.replaceAll("\\+", "%20");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename*=UTF-8''" + zipName);
		response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

		try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {

			if (request.getFolderStructure() != null) {
				for (Map<String, Object> item : request.getFolderStructure()) {
					String type = (String) item.get("type");
					String path = (String) item.get("path");
					String name = (String) item.get("name");

					if ("folder".equals(type)) {
						// ✅ 빈 폴더 생성
						String folderPath = path + "/";
						ZipEntry folderEntry = new ZipEntry(folderPath);
						zos.putNextEntry(folderEntry);
						zos.closeEntry();
						System.out.println("  ✅ 폴더 생성: " + folderPath);

					} else if ("file".equals(type)) {
						String zipPath = path + "/" + name;
						String fileId = (String) item.get("id");
						Optional<FileMetadata> metaOpt = fileMetadataRepository.findById(fileId);

						if (metaOpt.isEmpty()) {
							metaOpt = fileMetadataRepository.findByGridfsId(fileId);
						}

						if (metaOpt.isPresent()) {
							FileMetadata meta = metaOpt.get();
							if (meta.getUserIdx().equals(userIdx) && meta.getGridfsId() != null) {
								byte[] fileData = fileStorageService.downloadFile(meta.getGridfsId());
								ZipEntry entry = new ZipEntry(zipPath);
								zos.putNextEntry(entry);
								zos.write(fileData);
								zos.closeEntry();
								System.out.println("  ✅ 파일 추가: " + zipPath);
							}
						}

					} else if ("note".equals(type)) {
						String zipPath = path + "/" + name;
						Object noteIdObj = item.get("id");
						Long noteId = noteIdObj instanceof Integer ?
								((Integer) noteIdObj).longValue() : (Long) noteIdObj;

						Optional<Note> noteOpt = noteRepository.findById(noteId);

						if (noteOpt.isPresent()) {
							Note note = noteOpt.get();
							if (note.getUser().getUserIdx().equals(userIdx)) {
								String mdContent = "# " + note.getTitle() + "\n\n" +
										(note.getContent() != null ? note.getContent() : "");
								byte[] mdBytes = mdContent.getBytes(StandardCharsets.UTF_8);

								ZipEntry entry = new ZipEntry(zipPath);
								zos.putNextEntry(entry);
								zos.write(mdBytes);
								zos.closeEntry();
								System.out.println("  ✅ 노트 추가: " + zipPath);
							}
						}
					}
				}
			}

			System.out.println("=== Folder ZIP 생성 완료 ===");
		}
	}
	// ─────────────────────────────────────────────────────────────────────
	// 파일 삭제 (본인만)
	// ─────────────────────────────────────────────────────────────────────
	@DeleteMapping("/api/files/delete/{id}")
	public Map<String, Object> deleteFile(@PathVariable String id, Authentication auth) {
		Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
		Map<String, Object> result = new HashMap<>();
		try {
			FileInfo info = fileStorageService.previewFile(id);
			if (info == null || !String.valueOf(userIdx).equals(info.getUploaderIdx())) {
				result.put("success", false);
				result.put("message", "권한이 없습니다.");
				return result;
			}
			boolean deleted = fileStorageService.deleteFile(id);
			result.put("success", deleted);
		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		return result;
	}
}
