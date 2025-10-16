package com.smhrd.web.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

// PDFBox 3.x
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

// Apache POI (DOCX)
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

// Apache POI (DOC / HWPF)
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

// HWP (hwplib)
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractOption;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;

@Slf4j
@Service
public class FileParseService {

	/** 프리뷰/요약 공통 정규화 최대 길이 (문자 수) */
	private static final int MAX_LEN = 20_000;

	/** MIME 감지용 */
	private final Tika tika = new Tika();

	// ---------------------------------------------------------------------
	// 공개 API
	// ---------------------------------------------------------------------

	/** MultipartFile 입력을 바이트 오버로드로 위임 (라우팅 일원화) */
	public String extractText(MultipartFile file) throws Exception {
		String name = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
		byte[] bytes = file.getBytes();
		return extractText(bytes, name); // ✅ 공통 라우팅으로 위임
	}

	/** GridFS 등 바이트+파일명 입력 전용 (전문 파싱 / 프리뷰 절삭 없음) */
	public String extractText(byte[] bytes, String filename) throws Exception {
		String name = (filename == null || filename.isBlank()) ? "unknown" : filename;
		String lower = name.toLowerCase(Locale.ROOT);

		// 1) 확장자 우선 라우팅
		if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
			return normalize(parseText(bytes));
		}
		if (lower.endsWith(".pdf"))
			return normalize(parsePdf(bytes));
		if (lower.endsWith(".docx"))
			return safeDocx(bytes);
		if (lower.endsWith(".doc"))
			return safeDoc(bytes);
		if (lower.endsWith(".hwp"))
			return normalize(parseHwp(bytes));
		if (lower.endsWith(".hwpx"))
			return normalize(parseHwpx(bytes));

		// 2) MIME (Tika) 보조
		String mime = detectMime(bytes, name);
		if (mime.contains("pdf"))
			return normalize(parsePdf(bytes));
		if (mime.contains("wordprocessingml"))
			return safeDocx(bytes); // DOCX
		if (mime.contains("msword"))
			return safeDoc(bytes); // DOC
		if (mime.startsWith("text/"))
			return normalize(parseText(bytes));

		// 3) 시그니처 가드 (오탐 보완)
		if (looksLikeZip(bytes))
			return safeDocx(bytes); // 'PK' → DOCX(Zip 컨테이너)
		if (looksLikeOle2(bytes))
			return safeDoc(bytes); // OLE2 → DOC

		// 4) 최종 폴백 (평문)
		return normalize(parseText(bytes));
	}

	// ---------------------------------------------------------------------
	// 포맷별 파서
	// ---------------------------------------------------------------------

	/** TXT/MD */
	private String parseText(byte[] bytes) {
		String s = new String(bytes, StandardCharsets.UTF_8);
		if (looksGarbled(s)) {
			// 일부 CP949/ANSI 텍스트 대응
			s = new String(bytes, Charset.forName("MS949"));
		}
		return s;
	}

	/** 간단한 깨짐 감지: U+FFFD 비율 */
	private boolean looksGarbled(String s) {
		int len = Math.max(1, Math.min(2000, s.length()));
		int bad = 0;
		for (int i = 0; i < len; i++) {
			if (s.charAt(i) == '\uFFFD')
				bad++;
		}
		return bad > len * 0.02;
	}

	/** PDF (PDFBox 3.x) */

	private String parsePdf(byte[] bytes) throws Exception {
		if (bytes.length > 10 * 1024 * 1024) {
			return "[안내] 파일 크기가 커서 스캔(이미지) 기반 PDF일 가능성이 높습니다. " + "현재 OCR 기능은 비활성화되어 있습니다. 텍스트 기반 PDF를 업로드해 주세요.";
		}
		try (PDDocument doc = Loader.loadPDF(bytes)) {
			if (doc.isEncrypted())
				return "[안내] 암호화된 PDF는 텍스트를 추출할 수 없습니다.";
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			String text = stripper.getText(doc);
			String trimmed = (text == null) ? "" : text.strip();

			int pages = doc.getNumberOfPages();
			int imagePages = countImagePages(doc);
			double imageDensity = (pages > 0) ? (double) imagePages / pages : 0.0;

			int textLen = trimmed.length();
			boolean looksScanned = (textLen < 100)
					&& (bytes.length >= 1 * 1024 * 1024 || imageDensity >= 0.6 || pages >= 3);

			if (looksScanned) {
				return "[안내] 스캔(이미지) 기반 PDF로 텍스트를 추출할 수 없습니다. " + "현재 OCR 기능은 비활성화되어 있습니다.";
			}
			return trimmed.isEmpty() ? "[안내] 본문 텍스트를 찾지 못했습니다." : trimmed;
		} catch (Exception e) {
			return "PDF 텍스트 추출 실패: " + e.getMessage();
		}
	}

// ---- 동일 보조 메서드 (필요시 FileParseService에도 정의) ----
	private int countImagePages(PDDocument doc) throws Exception {
		int imagePages = 0;
		for (int i = 0; i < doc.getNumberOfPages(); i++) {
			PDPage page = doc.getPage(i);
			PDResources res = page.getResources();
			if (res == null)
				continue;
			if (hasImage(res))
				imagePages++;
		}
		return imagePages;
	}

	private boolean hasImage(PDResources res) throws Exception {
		for (COSName name : res.getXObjectNames()) {
			PDXObject xobj = res.getXObject(name);
			if (xobj instanceof PDImageXObject)
				return true;
			if (xobj instanceof PDFormXObject) {
				PDResources sub = ((PDFormXObject) xobj).getResources();
				if (sub != null && hasImage(sub))
					return true;
			}
		}
		return false;
	}

	/** DOC (HWPF) */
	private String parseDoc(byte[] bytes) throws Exception {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
				HWPFDocument doc = new HWPFDocument(bis);
				WordExtractor extractor = new WordExtractor(doc)) {
			String text = extractor.getText();
			return text == null ? "" : text;
		} catch (Exception e) {
			return "[안내] 파일 확장자가 DOC로 보이나 실제 내용은 다른 형식이거나 손상된 것으로 보입니다. " + "원본 확장자를 점검해 주세요.\n원인: " + e.getMessage();
		}
	}

	/** DOCX */
	private String parseDocx(byte[] bytes) throws Exception {
		try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
				XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
			String text = extractor.getText();
			return text == null ? "" : text;
		} catch (Exception e) {
			return "[안내] 파일 확장자가 DOCX로 보이나 실제 내용은 다른 형식이거나 손상된 것으로 보입니다. " + "원본 확장자를 점검해 주세요.\n원인: " + e.getMessage();
		}
	}

	/** HWP (hwplib) — TextExtractor 사용 */
	private String parseHwp(byte[] bytes) throws Exception {
		File tmp = File.createTempFile("npre_", ".hwp");
		try {
			try (FileOutputStream fos = new FileOutputStream(tmp)) {
				fos.write(bytes);
			}
			HWPFile hwp = HWPReader.fromFile(tmp.getAbsolutePath());
			// 컨트롤 문자 포함/제외 두 가지 옵션으로 추출 후 더 나은 쪽 선택
			TextExtractOption opt1 = new TextExtractOption();
			opt1.setMethod(TextExtractMethod.InsertControlTextBetweenParagraphText);
			opt1.setWithControlChar(true);
			opt1.setAppendEndingLF(true);
			String t1 = TextExtractor.extract(hwp, opt1);

			TextExtractOption opt2 = new TextExtractOption();
			opt2.setMethod(TextExtractMethod.InsertControlTextBetweenParagraphText);
			opt2.setWithControlChar(false);
			opt2.setAppendEndingLF(true);
			String t2 = TextExtractor.extract(hwp, opt2);

			String best = pickBestByKoreanDensity(t1, t2);
			return (best == null || best.isBlank()) ? "[안내] 본문 텍스트를 찾지 못했습니다." : best;
		} catch (Exception e) {
			return "HWP 파싱 실패: " + e.getMessage();
		} finally {
			// noinspection ResultOfMethodCallIgnored
			tmp.delete();
		}
	}

	/** HWPX (hwpxlib) — FQN으로만 사용 (충돌 방지) */
	private String parseHwpx(byte[] bytes) throws Exception {
		File tmp = File.createTempFile("npre_", ".hwpx");
		try {
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
			return (text == null || text.isBlank()) ? "[안내] HWPX 문서에서 텍스트를 찾지 못했습니다." : text;
		} catch (Exception e) {
			return "HWPX 파싱 실패: " + e.getMessage();
		} finally {
			// noinspection ResultOfMethodCallIgnored
			tmp.delete();
		}
	}

	// ---------------------------------------------------------------------
	// 라우팅/가드 헬퍼
	// ---------------------------------------------------------------------

	/** DOCX(Zip) 시그니처: 'PK' */
	private boolean looksLikeZip(byte[] b) {
		return b.length >= 2 && b[0] == 0x50 && b[1] == 0x4B;
	}

	/** DOC(OLE2) 시그니처: D0 CF 11 E0 A1 B1 1A E1 */
	private boolean looksLikeOle2(byte[] b) {
		byte[] sig = new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
				(byte) 0xE1 };
		if (b.length < sig.length)
			return false;
		for (int i = 0; i < sig.length; i++) {
			if (b[i] != sig[i])
				return false;
		}
		return true;
	}

	/** DOCX 안전 실행 + 교차-폴백 (Zip 시그니처가 없으면 DOC→평문) */
	private String safeDocx(byte[] bytes) throws Exception {
		if (!looksLikeZip(bytes)) {
			String docTry = normalize(parseDoc(bytes));
			if (!isParserError(docTry))
				return docTry;
			return normalize(parseText(bytes));
		}
		return normalize(parseDocx(bytes)); // 정상 DOCX
	}

	private String safeDoc(byte[] bytes) throws Exception {
		// OLE2인 경우라도 HWP 가능성을 먼저 체크
		if (looksLikeOle2(bytes)) {
			// 간단 HWP 판별: 실제 파서를 시도해 봄
			try {
				File tmp = File.createTempFile("detect_", ".hwp");
				try (FileOutputStream fos = new FileOutputStream(tmp)) {
					fos.write(bytes);
				}
				HWPReader.fromFile(tmp.getAbsolutePath());
				tmp.delete();
				return normalize(parseHwp(bytes));
			} catch (Exception ignore) {
				// HWP 아님 → DOC 시도
			}
		} else {
			// OLE2가 아니면 DOCX → 평문 폴백
			String docxTry = normalize(parseDocx(bytes));
			if (!isParserError(docxTry))
				return docxTry;
			return normalize(parseText(bytes));
		}
		return normalize(parseDoc(bytes));
	}

	/** Tika MIME 감지 */
	private String detectMime(byte[] bytes, String name) {
		try {
			String mime = tika.detect(bytes, name);
			log.info("Detected MIME: {}", mime);
			return (mime == null || mime.isBlank()) ? "application/octet-stream" : mime;
		} catch (Exception e) {
			log.warn("MIME detect failed, fallback to text parsing: {}", e.getMessage());
			return "application/octet-stream";
		}
	}

	/** 파서 에러/안내문 여부 판단(간단) */
	private boolean isParserError(String s) {
		if (s == null)
			return true;
		String t = s.strip();
		return t.startsWith("[안내]") || t.startsWith("PDF ") || t.startsWith("DOC ") || t.startsWith("DOCX ")
				|| t.startsWith("HWP ") || t.startsWith("HWPX ");
	}

	// ---------------------------------------------------------------------
	// 품질 보조(정규화/선택)
	// ---------------------------------------------------------------------

	/** 한글 밀도 + 길이 스코어로 더 나은 결과 선택 */
	private String pickBestByKoreanDensity(String... candidates) {
		String out = null;
		int bestScore = -1;
		for (String c : candidates) {
			if (c == null)
				continue;
			String s = c.strip();
			if (s.isEmpty())
				continue;
			int len = s.length();
			int han = 0;
			for (int i = 0; i < Math.min(len, 8000); i++) {
				char ch = s.charAt(i);
				if ((ch >= 0xAC00 && ch <= 0xD7A3) || (ch >= 0x3130 && ch <= 0x318F))
					han++;
			}
			int score = han * 2 + Math.min(len, 8000);
			if (score > bestScore) {
				bestScore = score;
				out = s;
			}
		}
		return out;
	}

	/** 공통 정규화: 잡스런 토큰 제거 + 라인/공백 정리 + 길이 상한 */
	private String normalize(String s) {
		if (s == null)
			return "";
		// Markdown 이미지/장식 토큰 제거 + HTML <img> 제거
		s = s.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ").replaceAll("<img\\s+[^>]*>", " ");
		// 공백/개행 정리
		s = s.replace("\r\n", "\n").replace("\r", "\n").replaceAll("[ \\t\\u00A0\\u200B]+", " ");
		// 중복 라인 제거 (상위 50k 라인까지)
		Set<String> seen = new LinkedHashSet<>();
		for (String line : s.split("\n")) {
			String t = line.trim();
			if (!t.isEmpty())
				seen.add(t);
			if (seen.size() > 50_000)
				break;
		}
		String compact = String.join("\n", seen);
		// 과도한 빈 줄 축약 + 길이 상한
		compact = compact.replaceAll("\n{3,}", "\n\n").trim();
		if (compact.length() > MAX_LEN) {
			compact = compact.substring(0, MAX_LEN) + "\n\n... (내용이 더 있습니다)";
		}
		return compact;
	}
}
