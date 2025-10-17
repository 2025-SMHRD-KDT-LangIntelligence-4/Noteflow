package com.smhrd.web.controller;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.FolderRepository;
import com.smhrd.web.service.FileStorageService;
import com.smhrd.web.service.FileStorageService.FileInfo;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class FileController {

	private final FileStorageService fileStorageService;
	private final FolderRepository folderRepository;

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
	@GetMapping("/api/files/preview/{id}")
	public ResponseEntity<?> previewFile(@PathVariable String id, Authentication auth) throws IOException {
		FileInfo info = fileStorageService.previewFile(id);
		if (info == null) {
			return ResponseEntity.notFound().build();
		}
		Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
		if (!String.valueOf(userIdx).equals(info.getUploaderIdx())) {
			return ResponseEntity.status(403).build();
		}

		String mime = info.getMimeType();
		if (mime == null || mime.isBlank()) {
			mime = fileStorageService.detectMimeTypeFromFilename(info.getOriginalName());
		}
		String filename = info.getOriginalName() == null ? "file" : info.getOriginalName();
		String lower = filename.toLowerCase(Locale.ROOT);

		// 1) PDF → inline으로 바로 열람/인쇄

		if ("application/pdf".equalsIgnoreCase(mime) || lower.endsWith(".pdf")) {
			byte[] data = fileStorageService.downloadFile(id);
			String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_PDF);
			headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded);
			headers.setContentLength(data.length);
			return ResponseEntity.ok().headers(headers).body(data);
		}

		// 2) HWP
		if ("application/x-hwp".equalsIgnoreCase(mime) || "application/haansofthwp".equalsIgnoreCase(mime)
				|| lower.endsWith(".hwp") || "application/vnd.hancom.hwpx".equalsIgnoreCase(mime)
				|| lower.endsWith(".hwpx")) {
			return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
					.body("[안내] 한글(HWP/HWPX) 파일의 본문 미리보기는 원본파일 탭에서 제공하지 않습니다. "

							+ "노션 작성 페이지에서 요약/파싱을 진행해 주세요.");
		}

		// 3) 기본 폴백: 텍스트 미리보기 (TXT/DOC/DOCX/텍스트 추출 가능한 PDF 등)
		String text = fileStorageService.getFilePreview(id, userIdx);
		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
				.body((text == null || text.isBlank()) ? "[안내] 본문 텍스트를 찾지 못했습니다." : text);
	}

	@GetMapping("/api/files/preview-text/{id}")

	public ResponseEntity<String> previewText(@PathVariable String id,
			@AuthenticationPrincipal(expression = "userIdx") Long userIdx) {
		String text = fileStorageService.getFilePreview(id, userIdx);
		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
				.body((text == null || text.isBlank()) ? "[안내] 본문 텍스트를 찾지 못했습니다." : text);
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
	@PostMapping("/api/files/download-zip")
	public void downloadZip(@RequestBody List<String> ids, Authentication auth, HttpServletResponse response)
			throws IOException {
		Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
		List<String> authorized = fileStorageService.listFiles().stream()
				.filter(info -> String.valueOf(userIdx).equals(info.getUploaderIdx()) && ids.contains(info.getId()))
				.map(FileInfo::getId).collect(Collectors.toList());

		String zipName = URLEncoder.encode("files.zip", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + zipName);
		response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

		try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
			fileStorageService.writeFilesAsZip(authorized, zos);
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
