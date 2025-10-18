package com.smhrd.web.service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.GridFSUploadStream;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.repository.FileMetadataRepository;
import com.smhrd.web.repository.FolderRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

// DOCX
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

// DOC (HWPF)
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

// HWP
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractOption;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;

/**
 * 파일 저장, 탐색, 미리보기, ZIP 다운로드 서비스
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

	@Autowired
	private FolderRepository folderRepository; // 현재는 사용하지 않지만, 향후 폴더 검증용으로 유지
	private final GridFSBucket gridFSBucket;
	private final FileMetadataRepository fileMetadataRepository;

	private final Tika tika = new Tika();

	// ─────────────────────────────────────────────────────────────────
	// 파일 저장
	// ─────────────────────────────────────────────────────────────────
	public String storeFile(MultipartFile file, Long userIdx, String folderId) throws IOException {
		String filename = file.getOriginalFilename();
		String storedFilename = generateStoredFilename(filename);

		Document metadata = new Document().append("originalFilename", filename)
				.append("mimeType", file.getContentType()).append("size", file.getSize())
				.append("uploadedAt", Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()))
				.append("uploaderIdx", userIdx);

		GridFSUploadOptions options = new GridFSUploadOptions().metadata(metadata);
		ObjectId objectId;
		try (GridFSUploadStream uploadStream = gridFSBucket.openUploadStream(storedFilename, options)) {
			uploadStream.write(file.getBytes());
			objectId = uploadStream.getObjectId();
		}

		// MongoDB 메타데이터 저장
		FileMetadata meta = FileMetadata.builder().originalName(filename).storedName(storedFilename)
				.fileSize(file.getSize()).mimeType(file.getContentType()).userIdx(userIdx)
				.folderId((folderId == null || folderId.isBlank()) ? null : folderId).uploadDate(LocalDateTime.now())
				.gridfsId(objectId.toHexString()).build();
		fileMetadataRepository.save(meta);

		return objectId.toHexString();
	}

	private String generateStoredFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			originalFilename = "unknown";
		}
		String ext = "";
		int dotIndex = originalFilename.lastIndexOf('.');
		if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
			ext = originalFilename.substring(dotIndex);
		}
		String uuid = UUID.randomUUID().toString();
		return uuid + ext;
	}
	public String storeTextAsFile(String filename, String content, Long userIdx, String folderId) throws IOException {
	    if (filename == null || filename.isBlank()) filename = "note.txt";
	    if (!filename.contains(".")) filename += ".md";
	    byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);

	    Document metadata = new Document()
	        .append("originalFilename", filename)
	        .append("mimeType", "text/markdown")
	        .append("size", bytes.length)
	        .append("uploadedAt", Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()))
	        .append("uploaderIdx", userIdx);

	    GridFSUploadOptions options = new GridFSUploadOptions().metadata(metadata);
	    ObjectId objectId;
	    try (GridFSUploadStream uploadStream = gridFSBucket.openUploadStream(filename, options)) {
	        uploadStream.write(bytes);
	        objectId = uploadStream.getObjectId();
	    }

	    FileMetadata meta = FileMetadata.builder()
	        .originalName(filename)
	        .storedName(filename)
	        .fileSize((long) bytes.length)
	        .mimeType("text/markdown")
	        .userIdx(userIdx)
	        .folderId(folderId) // String 타입 그대로
	        .uploadDate(LocalDateTime.now())
	        .gridfsId(objectId.toHexString())
	        .build();
	    
	    fileMetadataRepository.save(meta);
	    return objectId.toHexString();
	}

	// Long folderId 버전 (기존 호환성 유지)
	public String storeTextAsFile(String filename, String content, Long userIdx, Long folderId) throws IOException {
	    return storeTextAsFile(filename, content, userIdx, folderId == null ? null : String.valueOf(folderId));
	}

	// ─────────────────────────────────────────────────────────────────
	// 파일 다운로드
	// ─────────────────────────────────────────────────────────────────
	public byte[] downloadFile(String mongoDocId) throws IOException {
		ObjectId id = new ObjectId(mongoDocId);
		try (GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(id);
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[1024];
			int len;
			while ((len = downloadStream.read(buffer)) != -1) {
				output.write(buffer, 0, len);
			}
			return output.toByteArray();
		}
	}

	// ─────────────────────────────────────────────────────────────────
	// 파일 삭제
	// ─────────────────────────────────────────────────────────────────
	public boolean deleteFile(String mongoDocId) {
		try {
			gridFSBucket.delete(new ObjectId(mongoDocId));
			fileMetadataRepository.findByGridfsId(mongoDocId)
					.ifPresent(m -> fileMetadataRepository.deleteById(m.getId()));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// ─────────────────────────────────────────────────────────────────
	// 파일 트리 조회
	// ─────────────────────────────────────────────────────────────────
	public List<FileInfo> listFiles() {
		GridFSFindIterable files = gridFSBucket.find();
		List<FileInfo> list = new ArrayList<>();
		for (GridFSFile gf : files) {
			Document md = gf.getMetadata();
			Long uploaderIdxValue = md != null ? md.getLong("uploaderIdx") : null;
			list.add(new FileInfo(gf.getObjectId().toString(), gf.getFilename(),
					md != null ? md.getString("originalFilename") : gf.getFilename(), gf.getLength(),
					md != null ? md.getString("mimeType") : null,
					gf.getUploadDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
					uploaderIdxValue != null ? String.valueOf(uploaderIdxValue) : null));
		}
		return list;
	}

	// ─────────────────────────────────────────────────────────────────
	// 파일 메타 조회 (미리보기 전용 경량 정보)
	// ─────────────────────────────────────────────────────────────────
	public FileInfo previewFile(String mongoDocId) {
		ObjectId id = new ObjectId(mongoDocId);
		GridFSFile gf = gridFSBucket.find(new Document("_id", id)).first();
		if (gf == null)
			return null;
		Document md = gf.getMetadata();
		Long uploaderIdxValue = md != null ? md.getLong("uploaderIdx") : null;
		return new FileInfo(gf.getObjectId().toString(), gf.getFilename(),
				md != null ? md.getString("originalFilename") : gf.getFilename(), gf.getLength(),
				md != null ? md.getString("mimeType") : null,
				gf.getUploadDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
				uploaderIdxValue != null ? String.valueOf(uploaderIdxValue) : null);
	}

	// ─────────────────────────────────────────────────────────────────
	// 미리보기(텍스트) 고수준 API
	// ─────────────────────────────────────────────────────────────────
	public String getFilePreview(String mongoDocId, Long userIdx) {
		try {
			FileInfo fileInfo = previewFile(mongoDocId);
			if (fileInfo == null)
				return "파일을 찾을 수 없습니다.";
			if (!String.valueOf(userIdx).equals(fileInfo.getUploaderIdx()))
				return "접근 권한이 없습니다.";

			String mimeType = fileInfo.getMimeType();
			if (mimeType == null || mimeType.isBlank()) {
				mimeType = detectMimeTypeFromFilename(fileInfo.getOriginalName());
			}
			return extractTextContent(mongoDocId, mimeType, fileInfo.getOriginalName());
		} catch (Exception e) {
			return "파일 미리보기 중 오류가 발생했습니다: " + e.getMessage();
		}
	}

	// ─────────────────────────────────────────────────────────────────
	// ZIP 다운로드 지원
	// ─────────────────────────────────────────────────────────────────
	public void writeFilesAsZip(List<String> fileIds, ZipOutputStream zos) throws IOException {
		System.out.println("=== writeFilesAsZip 시작 ===");
		System.out.println("받은 fileIds: " + fileIds);

		if (fileIds == null || fileIds.isEmpty()) {
			System.out.println("⚠️ fileIds가 비어있습니다!");
			return;
		}

		for (String fileId : fileIds) {
			System.out.println("처리 중인 fileId: " + fileId);

			try {
				// FileMetadata 조회
				Optional<FileMetadata> metaOpt = fileMetadataRepository.findById(fileId);

				if (metaOpt.isEmpty()) {
					System.out.println("  ❌ FileMetadata를 찾을 수 없음: " + fileId);
					continue;
				}

				FileMetadata meta = metaOpt.get();
				System.out.println("  ✅ FileMetadata 찾음: " + meta.getOriginalName());
				System.out.println("     GridfsId: " + meta.getGridfsId());

				if (meta.getGridfsId() == null) {
					System.out.println("  ❌ GridfsId가 null입니다!");
					continue;
				}

				// GridFS에서 파일 다운로드
				byte[] fileData = downloadFile(meta.getGridfsId());
				System.out.println("  ✅ 파일 다운로드 완료, 크기: " + fileData.length + " bytes");

				// ZIP에 추가
				ZipEntry entry = new ZipEntry(meta.getOriginalName());
				zos.putNextEntry(entry);
				zos.write(fileData);
				zos.closeEntry();
				System.out.println("  ✅ ZIP에 추가 완료");

			} catch (Exception e) {
				System.out.println("  ❌ 오류 발생: " + e.getMessage());
				e.printStackTrace();
			}
		}

		System.out.println("=== writeFilesAsZip 완료 ===");
	}

	// ─────────────────────────────────────────────────────────────────
	// 내부: 텍스트 추출 라우팅
	// ─────────────────────────────────────────────────────────────────

	// 시그니처 헬퍼
	private boolean looksLikeZip(byte[] b) {
		return b.length >= 4 && b[0] == 0x50 && b[1] == 0x4B; // 'PK'
	}

	private boolean looksLikeOle2(byte[] b) {
		byte[] sig = new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
				(byte) 0xE1 };
		if (b.length < sig.length)
			return false;
		for (int i = 0; i < sig.length; i++)
			if (b[i] != sig[i])
				return false;
		return true;
	}


private String extractTextContent(String mongoDocId, String mimeTypeHint, String filename) {
    try {
        byte[] fileData = downloadFile(mongoDocId);
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);

        // 1) Tika MIME (힌트는 보조)
        String mime = "application/octet-stream";
        try {
            mime = tika.detect(fileData, filename);
        } catch (Exception ignore) { }

        // 2) 시그니처
        boolean zip  = looksLikeZip(fileData);   // DOCX/OOXML
        boolean ole2 = looksLikeOle2(fileData);  // DOC/HWP 등

        // 3) 라우팅 (HWP → PDF → DOCX → DOC → TEXT)
        if (lower.endsWith(".hwp")
            || "application/x-hwp".equalsIgnoreCase(mime)
            || "application/haansofthwp".equalsIgnoreCase(mime)
            || isLikelyHwp(fileData)) {
            return extractHwpText(fileData);
        }

        if (mime.contains("pdf") || lower.endsWith(".pdf")) {
            return extractPdfText(fileData);
        }

        if (mime.contains("wordprocessingml") || lower.endsWith(".docx") || zip) {
            return extractDocxText(fileData);
        }

        if (mime.contains("msword") || lower.endsWith(".doc")) {
            // OLE2라고 해서 무조건 DOC로 보면 안 됨(HWP도 OLE2)
            if (ole2 && isLikelyHwp(fileData)) {
                return extractHwpText(fileData);
            }
            return extractDocText(fileData);
        }

        if (mime.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return extractPlainText(fileData);
        }

        // 4) 최종 폴백: 평문 시도
        return extractPlainText(fileData);
    } catch (Exception e) {
        return "파일 내용 추출 중 오류가 발생했습니다: " + e.getMessage();
    }
}


private boolean isLikelyHwp(byte[] fileData) {
    java.io.File tmp = null;
    try {
        tmp = java.io.File.createTempFile("detect_", ".hwp");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
            fos.write(fileData);
        }
        kr.dogfoot.hwplib.reader.HWPReader.fromFile(tmp.getAbsolutePath());
        return true; // 파싱 시도 성공 → HWP일 가능성 매우 높음
    } catch (Exception ignore) {
        return false;
    } finally {
        if (tmp != null) tmp.delete();
    }
}



	private String extractPlainText(byte[] fileData) {
		try {
			String content = new String(fileData, StandardCharsets.UTF_8);
			if (content.length() > 2000)
				content = content.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
			return content;
		} catch (Exception e) {
			return "텍스트 파일을 읽을 수 없습니다: " + e.getMessage();
		}
	}




private String extractPdfText(byte[] fileData) {
    // 큰 파일은 안내 우선(스캔 가능성 높음)
    if (fileData.length > 10 * 1024 * 1024) {
        return "[안내] 파일 크기가 커서 스캔(이미지) 기반 PDF일 가능성이 높습니다. "
             + "현재 OCR 기능은 비활성화되어 있어 텍스트를 제공할 수 없습니다. "
             + "텍스트 기반 PDF를 업로드해 주세요.";
    }
    try (PDDocument doc = Loader.loadPDF(fileData)) {
        if (doc.isEncrypted()) {
            return "[안내] 암호화된 PDF는 텍스트를 추출할 수 없습니다.";
        }
        // 1) 텍스트 추출
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        String text = stripper.getText(doc);
        String trimmed = (text == null) ? "" : text.strip();

        // 2) 이미지 밀도 계산(재귀 포함)
        int pages = doc.getNumberOfPages();
        int imagePages = countImagePages(doc);
        double imageDensity = (pages > 0) ? (double) imagePages / pages : 0.0;

        // 3) 오탐 방지: 짧은 정상 PDF면 그대로 반환
        int textLen = trimmed.length();
        long fileBytes = fileData.length;

        boolean looksScanned = (textLen < 100) &&
                               (fileBytes >= 1 * 1024 * 1024   // 1MB 이상
                             || imageDensity >= 0.6           // 이미지 페이지 비율 높음
                             || pages >= 3);                   // 여러 페이지

        if (looksScanned) {
            return "[안내] 스캔(이미지) 기반 PDF로 텍스트를 추출할 수 없습니다. "
                 + "현재 OCR 기능은 비활성화되어 있습니다. "
                 + "텍스트 기반 PDF를 업로드해 주세요.";
        }

        if (trimmed.isEmpty()) {
            return "[안내] 본문 텍스트를 찾지 못했습니다.";
        }
        // 프리뷰 길이 제한(2,000자) 유지
        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
        }
        return trimmed;
    } catch (Exception e) {
        return "PDF 파일 처리 중 오류가 발생했습니다: " + e.getMessage();
    }
}

// ---- 재귀적으로 이미지 존재 여부를 판정해서 '이미지 페이지 수'를 계산 ----
private int countImagePages(PDDocument doc) throws Exception {
    int imagePages = 0;
    for (int i = 0; i < doc.getNumberOfPages(); i++) {
        PDPage page = doc.getPage(i);
        PDResources res = page.getResources();
        if (res == null) continue;
        if (hasImage(res)) {
            imagePages++;
        }
    }
    return imagePages;
}

// ---- 리소스 트리 안에 이미지(PDImageXObject)가 존재하는지 재귀 확인 ----
private boolean hasImage(PDResources res) throws Exception {
    for (COSName name : res.getXObjectNames()) {                       // Iterable<COSName>
        PDXObject xobj = res.getXObject(name);                         // XObject 얻기
        if (xobj instanceof PDImageXObject) {
            return true;                                               // 이미지 발견
        } else if (xobj instanceof PDFormXObject) {
            PDResources sub = ((PDFormXObject) xobj).getResources();   // Form 내부 리소스
            if (sub != null && hasImage(sub)) return true;             // 재귀 탐색
        }
    }
    return false;
}



	private String extractDocxText(byte[] fileData) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(fileData);
				XWPFDocument document = new XWPFDocument(bis);
				XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
			String text = extractor.getText();
			if (text.length() > 2000)
				text = text.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
			return text.isEmpty() ? "DOCX에서 텍스트를 추출할 수 없습니다." : text;
		} catch (Exception e) {
			return "DOCX 파일 처리 중 오류가 발생했습니다: " + e.getMessage();
		}
	}

	private String extractDocText(byte[] fileData) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(fileData);
				HWPFDocument doc = new HWPFDocument(bis);
				WordExtractor extractor = new WordExtractor(doc)) {
			String text = extractor.getText();
			if (text.length() > 2000)
				text = text.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
			return text.isEmpty() ? "DOC에서 텍스트를 추출할 수 없습니다." : text;
		} catch (Exception e) {
			return "DOC 파일 처리 중 오류가 발생했습니다: " + e.getMessage();
		}
	}

	// HWP는 NotionCreate 단계에서 FileParseService로 처리 (파일탭 미리보기는 미지원)
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private String extractHwpText(byte[] bytes) {
		// 필요 시(정책 변경 시) 활성화
		try {
			File tmp = File.createTempFile("preview_", ".hwp");
			try (FileOutputStream fos = new FileOutputStream(tmp)) {
				fos.write(bytes);
			}
			HWPFile hwp = HWPReader.fromFile(tmp.getAbsolutePath());
			TextExtractOption opt = new TextExtractOption();
			opt.setMethod(TextExtractMethod.InsertControlTextBetweenParagraphText);
			opt.setWithControlChar(true);
			opt.setAppendEndingLF(true);
			String text = TextExtractor.extract(hwp, opt);
			tmp.delete();
			if (text == null || text.isBlank())
				return "[안내] 본문 텍스트를 찾지 못했습니다.";
			if (text.length() > 2000)
				text = text.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
			return text;
		} catch (Exception e) {
			return "HWP 파싱 실패: " + e.getMessage();
		}
	}

	private String extractHwpxText(byte[] bytes) {
		File tmp = null;
		try {
			tmp = File.createTempFile("preview_", ".hwpx");
			try (FileOutputStream fos = new FileOutputStream(tmp)) {
				fos.write(bytes);
			}

			kr.dogfoot.hwpxlib.object.HWPXFile x = kr.dogfoot.hwpxlib.reader.HWPXReader.fromFile(tmp);

			kr.dogfoot.hwpxlib.tool.textextractor.TextMarks marks = new kr.dogfoot.hwpxlib.tool.textextractor.TextMarks()
					.paraSeparatorAnd("\n\n").lineBreakAnd("\n").tabAnd("\t").tableStartAnd("\n[TABLE_START]\n")
					.tableEndAnd("\n[TABLE_END]\n").tableRowSeparatorAnd("[ROW_END]\n").tableCellSeparatorAnd(" | ");

			String text = kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor.extract(x,
					kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod.InsertControlTextBetweenParagraphText, true, // 컨트롤
																															// 텍스트
																															// 포함
					marks);

			if (text == null || text.isBlank()) {
				return "[안내] HWPX 문서에서 텍스트를 찾지 못했습니다.";
			}
			// 길이 제한/정규화 (프리뷰 품질 고려)
			if (text.length() > 2000)
				text = text.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
			return text;

		} catch (Exception e) {
			return "HWPX 파싱 실패: " + e.getMessage();
		} finally {
			if (tmp != null)
				tmp.delete();
		}
	}

	// ─────────────────────────────────────────────────────────────────
	// 파일명 기반 MIME 추론 (공개 메서드로 변경)
	// ─────────────────────────────────────────────────────────────────
	public String detectMimeTypeFromFilename(String filename) {
		if (filename == null)
			return "application/octet-stream";
		String lower = filename.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".txt"))
			return "text/plain";
		if (lower.endsWith(".pdf"))
			return "application/pdf";
		if (lower.endsWith(".docx"))
			return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		if (lower.endsWith(".doc"))
			return "application/msword";
		if (lower.endsWith(".md") || lower.endsWith(".markdown"))
			return "text/markdown";
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
			return "image/jpeg";
		if (lower.endsWith(".png"))
			return "image/png";
		if (lower.endsWith(".gif"))
			return "image/gif";
		if (lower.endsWith(".hwp"))
			return "application/x-hwp";
		if (lower.endsWith(".hwpx"))
			return "application/vnd.hancom.hwpx";
		return "application/octet-stream";
	}

	// ─────────────────────────────────────────────────────────────────
	// 파일 정보 DTO
	// ─────────────────────────────────────────────────────────────────
	@Getter
	@AllArgsConstructor
	public static class FileInfo {
		private final String id;
		private final String storedName;
		private final String originalName;
		private final long size;
		private final String mimeType;
		private final LocalDateTime uploadedAt;
		private final String uploaderIdx; // long -> String
	}

	public String storeFile(MultipartFile file, Long userIdx) throws IOException {
		return storeFile(file, userIdx, null);
	}
}
