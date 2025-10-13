package com.smhrd.web.controller;

import com.smhrd.web.entity.Folder;
import com.smhrd.web.repository.FolderRepository;
import com.smhrd.web.service.FileStorageService;
import com.smhrd.web.service.FileStorageService.FileInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class FileController {

	private final FileStorageService fileStorageService;
	private final FolderRepository folderRepository;

	// ---------------------------------------------------------------------
	// 파일 업로드: 선택 폴더로 저장 (folderId 없으면 루트)
	// ---------------------------------------------------------------------

	@PostMapping("/api/files/upload")
	public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "folderId", required = false) String folderId, Authentication auth) {
		String userId = auth.getName();
		Map<String, Object> result = new HashMap<>();
		try {
			if (file == null || file.isEmpty()) {
				result.put("success", false);
				result.put("message", "빈 파일입니다.");
				return result;
			}

			// 선택 폴더 업로드인 경우에만 소유자 검증
			if (folderId != null && !folderId.isBlank()) {
				var folder = folderRepository.findById(folderId)
						.orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
				if (!userId.equals(folder.getUserId())) {
					result.put("success", false);
					result.put("message", "권한 없음");
					return result;
				}
				// 선택 폴더로 저장
				String gridfsId = fileStorageService.storeFile(file, userId, folderId);
				result.put("success", true);
				result.put("gridfsId", gridfsId);
				result.put("folderId", folderId);
				return result;
			}

			// 선택 폴더가 없으면 기존 동작(루트 저장) 그대로
			String gridfsId = fileStorageService.storeFile(file, userId);
			result.put("success", true);
			result.put("gridfsId", gridfsId);
			result.put("folderId", null);
			return result;

		} catch (Exception e) {
			result.put("success", false);
			result.put("message", e.getMessage());
			return result;
		}
	}

	// ---------------------------------------------------------------------
	// 파일 미리보기 (텍스트/마크다운/CSV/JSON만)
	// ---------------------------------------------------------------------

	@GetMapping("/api/files/preview/{id}")
	public ResponseEntity<String> previewFile(@PathVariable String id, Authentication auth) throws IOException {
	    FileInfo info = fileStorageService.previewFile(id); // 메타 조회(이름/타입/업로더 등)
	    if (info == null) {
	        return ResponseEntity.notFound().build();
	    }
	    String userId = auth.getName();
	    if (!userId.equals(info.getUploaderId())) {
	        return ResponseEntity.status(403).build();
	    }
	
	    String contentType = info.getMimeType();
	    if (contentType == null || contentType.isBlank()) {
	        contentType = "application/octet-stream"; // 기본값
	    }
	
	    // 텍스트성만 본문 반환
	    boolean textLike = isTextLike(contentType);
	    textLike = textLike || isTextLikeByName(info.getOriginalName());
	
	    if (!textLike) {
	        return ResponseEntity.status(415).body("미리보기를 지원하지 않는 파일 형식입니다: " + contentType);
	    }
	
	    byte[] data = fileStorageService.downloadFile(id);
	    String body = new String(data, StandardCharsets.UTF_8);
	    return ResponseEntity.ok()
	            .contentType(MediaType.TEXT_PLAIN)
	            .body(body);
	}


	// ---------------------------------------------------------------------
	// 파일 다운로드 (본인만)
	// ---------------------------------------------------------------------
	@GetMapping("/api/files/download/{id}")
	public ResponseEntity<byte[]> downloadFile(@PathVariable String id, Authentication auth) throws IOException {
		FileInfo info = fileStorageService.previewFile(id);
		if (info == null) {
			return ResponseEntity.notFound().build();
		}
		String userId = auth.getName();
		if (!userId.equals(info.getUploaderId())) {
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

	// ---------------------------------------------------------------------
	// 파일 목록 조회 (본인 파일만)
	// ---------------------------------------------------------------------
	@GetMapping("/api/files/list")
	public List<FileInfo> listFiles(Authentication auth) {
		String userId = auth.getName();
		return fileStorageService.listFiles().stream().filter(info -> userId.equals(info.getUploaderId()))
				.collect(Collectors.toList());
	}

	// ---------------------------------------------------------------------
	// 다중 파일 ZIP 다운로드 (본인 것만)
	// ---------------------------------------------------------------------
	@PostMapping("/api/files/download-zip")
	public void downloadZip(@RequestBody List<String> ids, Authentication auth, HttpServletResponse response)
			throws IOException {
		String userId = auth.getName();
		List<String> authorized = fileStorageService.listFiles().stream()
				.filter(info -> userId.equals(info.getUploaderId()) && ids.contains(info.getId())).map(FileInfo::getId)
				.collect(Collectors.toList());

		String zipName = URLEncoder.encode("files.zip", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + zipName);
		response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

		try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
			fileStorageService.writeFilesAsZip(authorized, zos);
		}
	}

	// ---------------------------------------------------------------------
	// 파일 삭제 (본인만)
	// ---------------------------------------------------------------------
	@DeleteMapping("/api/files/delete/{id}")
	public Map<String, Object> deleteFile(@PathVariable String id, Authentication auth) {
		String userId = auth.getName();
		Map<String, Object> result = new HashMap<>();
		try {
			FileInfo info = fileStorageService.previewFile(id);
			if (info == null || !userId.equals(info.getUploaderId())) {
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

	// ---------------------------------------------------------------------
	// 내부 유틸: 텍스트 타입 판별
	// ---------------------------------------------------------------------

	private boolean isTextLike(String contentType) {
	    if (contentType == null) return false;
	    String ct = contentType.toLowerCase();
	    return ct.startsWith("text/")
	        || ct.contains("json")
	        || ct.contains("csv")
	        || ct.contains("xml")
	        || ct.contains("markdown")
	        || ct.contains("md");
	}
	
	// 보조: 파일명으로 판별 (선택)
	private boolean isTextLikeByName(String name) {
	    String lower = (name == null ? "" : name).toLowerCase();
	    return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")
	        || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".log");
	}

}