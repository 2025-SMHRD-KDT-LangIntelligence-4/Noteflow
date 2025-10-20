package com.smhrd.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.*;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.repository.NoteFolderRepository;
import com.smhrd.web.repository.NoteTagRepository;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.ExamService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/exam")
public class ExamController {

    private final ExamService examService;
    private final NoteRepository noteRepository;
    private final NoteFolderRepository noteFolderRepository;
    private final NoteTagRepository noteTagRepository;

    /**
     * 노트 정보를 세션에 저장 (POST)
     */
    @PostMapping("/prepare-from-note")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> prepareFromNote(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long noteIdx = request.get("noteIdx") != null ?
                    Long.parseLong(request.get("noteIdx").toString()) : null;
            String noteTitle = (String) request.get("noteTitle");
            List<String> keywords = (List<String>) request.get("keywords");

            // 세션에 저장
            session.setAttribute("preselectedNoteIdx", noteIdx);
            session.setAttribute("preselectedNoteTitle", noteTitle);
            session.setAttribute("preselectedKeywords", keywords);

            log.info("노트 정보 세션 저장: noteIdx={}, title={}, keywords={}",
                    noteIdx, noteTitle, keywords);

            response.put("success", true);
            response.put("message", "노트 정보가 저장되었습니다.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("노트 정보 저장 실패", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    /**
     * 시험 생성 페이지
     */
    @GetMapping("/create")
    public String createExamPage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session,
            Model model) {

        try {
            if (userDetails == null) {
                return "redirect:/login";
            }

            model.addAttribute("nickname", userDetails.getNickname());
            model.addAttribute("email", userDetails.getEmail());
            model.addAttribute("pageTitle", "시험 생성");
            model.addAttribute("activeMenu", "exam");

            // 세션에서 사전 선택 정보 가져오기
            Long preselectedNoteIdx = (Long) session.getAttribute("preselectedNoteIdx");
            String preselectedNoteTitle = (String) session.getAttribute("preselectedNoteTitle");
            List<String> preselectedKeywords = (List<String>) session.getAttribute("preselectedKeywords");

            // 세션 정보 전달
            if (preselectedNoteIdx != null) {
                model.addAttribute("preselectedNoteIdx", preselectedNoteIdx);
                model.addAttribute("preselectedNoteTitle", preselectedNoteTitle);
                model.addAttribute("preselectedKeywords", preselectedKeywords != null ? preselectedKeywords : Collections.emptyList());

                // 세션에서 제거 (일회성)
                session.removeAttribute("preselectedNoteIdx");
                session.removeAttribute("preselectedNoteTitle");
                session.removeAttribute("preselectedKeywords");

                log.info("세션에서 노트 정보 로드: noteIdx={}, title={}",
                        preselectedNoteIdx, preselectedNoteTitle);
            }

            // 모든 노트 조회 (안전하게)
            List<Note> allNotes = new ArrayList<>();
            try {
                allNotes = noteRepository.findAll().stream()
                        .filter(note -> note.getUser() != null &&
                                note.getUser().getUserIdx().equals(userDetails.getUserIdx()))
                        .filter(note -> "ACTIVE".equals(note.getStatus()))
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("노트 조회 실패", e);
            }

            // 노트 데이터 변환
            List<Map<String, Object>> notesData = allNotes.stream()
                    .map(note -> {
                        Map<String, Object> noteMap = new HashMap<>();
                        noteMap.put("noteIdx", note.getNoteIdx());
                        noteMap.put("title", note.getTitle());
                        noteMap.put("createdAt", note.getCreatedAt().toString());
                        noteMap.put("folderId", note.getFolderId());

                        // 태그 안전하게 조회
                        List<String> tags = new ArrayList<>();
                        try {
                            tags = noteTagRepository.findTagNamesByNoteIdx(note.getNoteIdx());
                        } catch (Exception e) {
                            log.warn("태그 조회 실패: noteIdx={}", note.getNoteIdx());
                        }
                        noteMap.put("tags", tags);

                        return noteMap;
                    })
                    .collect(Collectors.toList());

            // 폴더 계층 구조 생성
            List<NoteFolder> allFolders = new ArrayList<>();
            try {
                allFolders = noteFolderRepository.findAll().stream()
                        .filter(folder -> folder.getUserIdx().equals(userDetails.getUserIdx()))
                        .sorted(Comparator.comparing(NoteFolder::getFolderName))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("폴더 조회 실패", e);
            }

            List<Map<String, Object>> folderTree = buildFolderTree(allFolders, null, notesData);

            // JSON 문자열로 변환
            ObjectMapper mapper = new ObjectMapper();
            String folderTreeJson = mapper.writeValueAsString(folderTree);

            List<Map<String, Object>> rootNotes = notesData.stream()
                    .filter(n -> n.get("folderId") == null)
                    .collect(Collectors.toList());
            String rootNotesJson = mapper.writeValueAsString(rootNotes);

            model.addAttribute("folderTreeJson", folderTreeJson);
            model.addAttribute("rootNotesJson", rootNotesJson);

            log.info("시험 생성 페이지 로드 완료: 노트 {}개, 폴더 {}개",
                    notesData.size(), allFolders.size());

            return "examCreate";

        } catch (Exception e) {
            log.error("시험 생성 페이지 로드 실패", e);
            model.addAttribute("errorMessage", "페이지를 불러오는 중 오류가 발생했습니다: " + e.getMessage());
            return "error";
        }
    }

    /**
     * 재귀적으로 폴더 트리 생성
     */
    private List<Map<String, Object>> buildFolderTree(
            List<NoteFolder> allFolders,
            Long parentId,
            List<Map<String, Object>> allNotes) {

        return allFolders.stream()
                .filter(f -> Objects.equals(f.getParentFolderId(), parentId))
                .map(folder -> {
                    Map<String, Object> folderMap = new HashMap<>();
                    folderMap.put("folderId", folder.getFolderId());
                    folderMap.put("folderName", folder.getFolderName());
                    folderMap.put("parentFolderId", folder.getParentFolderId());

                    // 하위 폴더 재귀 조회
                    List<Map<String, Object>> subfolders = buildFolderTree(
                            allFolders, folder.getFolderId(), allNotes);
                    folderMap.put("subfolders", subfolders);

                    // 이 폴더에 속한 노트들
                    List<Map<String, Object>> notes = allNotes.stream()
                            .filter(n -> folder.getFolderId().equals(n.get("folderId")))
                            .collect(Collectors.toList());
                    folderMap.put("notes", notes);

                    return folderMap;
                })
                .collect(Collectors.toList());
    }



    /**
     * 키워드 기반 시험 생성 API
     */
    @PostMapping("/api/create-from-keywords")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createExamFromKeywords(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Map<String, Object> response = new HashMap<>();

        try {
            Long noteIdx = request.get("noteIdx") != null ?
                    Long.parseLong(request.get("noteIdx").toString()) : null;

            List<String> keywords = (List<String>) request.get("keywords");
            String title = (String) request.get("title");
            String difficulty = (String) request.get("difficulty");
            Integer questionCount = (Integer) request.get("questionCount");
            Integer scorePerQuestion = (Integer) request.get("scorePerQuestion");
            Boolean adaptiveDifficulty = (Boolean) request.getOrDefault("adaptiveDifficulty", false);

            if (keywords == null || keywords.isEmpty()) {
                response.put("success", false);
                response.put("message", "키워드를 최소 1개 이상 입력해주세요.");
                return ResponseEntity.badRequest().body(response);
            }

            Long userIdx = userDetails != null ? userDetails.getUserIdx() : null;

            Test test = examService.createExamFromKeywords(
                    keywords, title, userIdx, difficulty, questionCount, scorePerQuestion, adaptiveDifficulty);

            response.put("success", true);
            response.put("message", "시험이 생성되었습니다.");
            response.put("testIdx", test.getTestIdx());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("키워드 기반 시험 생성 실패", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 시험 목록 페이지
     */
    @GetMapping("/list")
    public String listPage(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "15") int size,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           Model model) {

        if (userDetails != null) {
            model.addAttribute("nickname", userDetails.getNickname());
            model.addAttribute("email", userDetails.getEmail());
        }

        Page<Test> testPage = examService.getExamList(page, size);

        model.addAttribute("pageTitle", "시험 목록");
        model.addAttribute("activeMenu", "exam");
        model.addAttribute("tests", testPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", testPage.getTotalPages());
        model.addAttribute("totalItems", testPage.getTotalElements());

        return "examList";
    }

    /**
     * 시험 응시 페이지
     */
    @GetMapping("/solve/{testIdx}")
    public String solvePage(@PathVariable Long testIdx,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {

        if (userDetails != null) {
            model.addAttribute("nickname", userDetails.getNickname());
            model.addAttribute("email", userDetails.getEmail());
            model.addAttribute("userIdx", userDetails.getUserIdx());
        }

        Map<String, Object> examData = examService.getExamWithQuestions(testIdx);
        Test test = (Test) examData.get("test");
        List<TestItem> items = (List<TestItem>) examData.get("items");

        // 문제 데이터 변환 (정답 제외)
        List<Map<String, Object>> questions = items.stream()
                .map(item -> {
                    Map<String, Object> q = new HashMap<>();
                    TestSource ts = item.getTestSource();
                    q.put("testSourceIdx", ts.getTestSourceIdx());
                    q.put("sequence", item.getSequence());
                    q.put("question", ts.getQuestion());
                    q.put("questionType", ts.getQuestionType());
                    q.put("options", ts.getOptions());
                    q.put("score", item.getScore());
                    q.put("difficulty", ts.getDifficulty());
                    q.put("categoryLarge", ts.getCategoryLarge());
                    return q;
                })
                .collect(Collectors.toList());

        model.addAttribute("pageTitle", test.getTestTitle());
        model.addAttribute("activeMenu", "exam");
        model.addAttribute("test", test);
        model.addAttribute("questions", questions);
        model.addAttribute("totalScore", examData.get("totalScore"));
        model.addAttribute("questionCount", examData.get("questionCount"));

        return "examSolve";
    }

    /**
     * 시험 제출 API
     */
    @PostMapping("/api/submit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitExam(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (userDetails == null) {
                response.put("success", false);
                response.put("message", "로그인이 필요합니다.");
                return ResponseEntity.status(401).body(response);
            }

            Long testIdx = Long.parseLong(request.get("testIdx").toString());
            Map<Long, String> answers = new HashMap<>();

            // 답안 파싱
            Map<String, String> answersRaw = (Map<String, String>) request.get("answers");
            answersRaw.forEach((k, v) -> answers.put(Long.parseLong(k), v));

            String startTimeStr = (String) request.get("startTime");
            String endTimeStr = (String) request.get("endTime");

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr, formatter);

            log.info("시험 제출: testIdx={}, userIdx={}, 답안 수={}",
                    testIdx, userDetails.getUserIdx(), answers.size());

            // 채점
            TestResult result = examService.submitExam(
                    testIdx,
                    userDetails.getUserIdx(),
                    answers,
                    startTime,
                    endTime
            );

            response.put("success", true);
            response.put("message", "채점이 완료되었습니다.");
            response.put("resultIdx", result.getResultIdx());
            response.put("totalScore", result.getTotalScore());
            response.put("userScore", result.getUserScore());
            response.put("correctCount", result.getCorrectCount());
            response.put("wrongCount", result.getWrongCount());
            response.put("passed", result.getPassed());
            response.put("passRate", String.format("%.1f",
                    (double) result.getUserScore() / result.getTotalScore() * 100));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("시험 제출 실패", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 시험 결과 페이지
     */
    @GetMapping("/result/{resultIdx}")
    public String resultPage(@PathVariable Long resultIdx,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             Model model) {

        if (userDetails != null) {
            model.addAttribute("nickname", userDetails.getNickname());
            model.addAttribute("email", userDetails.getEmail());
        }

        Map<String, Object> resultData = examService.getResultDetail(resultIdx);
        TestResult result = (TestResult) resultData.get("result");
        List<UserAnswer> answers = (List<UserAnswer>) resultData.get("answers");

        // 답안 데이터 변환
        List<Map<String, Object>> answerList = answers.stream()
                .map(ua -> {
                    Map<String, Object> ans = new HashMap<>();
                    TestSource ts = ua.getTestSource();
                    ans.put("testSourceIdx", ts.getTestSourceIdx());
                    ans.put("question", ts.getQuestion());
                    ans.put("correctAnswer", ts.getAnswer());
                    ans.put("userAnswer", ua.getUserAnswer());
                    ans.put("isCorrect", ua.getIsCorrect());
                    ans.put("explanation", ts.getExplanation());
                    ans.put("questionType", ts.getQuestionType());
                    ans.put("options", ts.getOptions());
                    ans.put("difficulty", ts.getDifficulty());
                    return ans;
                })
                .collect(Collectors.toList());

        model.addAttribute("pageTitle", "시험 결과");
        model.addAttribute("activeMenu", "exam");
        model.addAttribute("result", result);
        model.addAttribute("answers", answerList);
        model.addAttribute("passRate", resultData.get("passRate"));

        return "examResult";
    }

    /**
     * 내 시험 결과 목록 페이지
     */
    @GetMapping("/my-results")
    public String myResultsPage(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "15") int size,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                Model model) {

        if (userDetails == null) {
            return "redirect:/login";
        }

        model.addAttribute("nickname", userDetails.getNickname());
        model.addAttribute("email", userDetails.getEmail());

        List<TestResult> results = examService.getUserResults(userDetails.getUserIdx(), page, size);

        model.addAttribute("pageTitle", "내 시험 기록");
        model.addAttribute("activeMenu", "exam");
        model.addAttribute("results", results);
        model.addAttribute("currentPage", page);

        return "examMyResults";
    }

    /**
     * 오답노트 페이지
     */
    @GetMapping("/wrong-answers")
    public String wrongAnswersPage(@RequestParam(required = false) String category,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   Model model) {

        if (userDetails == null) {
            return "redirect:/login";
        }

        model.addAttribute("nickname", userDetails.getNickname());
        model.addAttribute("email", userDetails.getEmail());

        List<UserAnswer> wrongAnswers = examService.getWrongAnswers(userDetails.getUserIdx(), category);

        // 오답 데이터 변환
        List<Map<String, Object>> wrongList = wrongAnswers.stream()
                .map(ua -> {
                    Map<String, Object> wrong = new HashMap<>();
                    TestSource ts = ua.getTestSource();
                    wrong.put("answerIdx", ua.getAnswerIdx());
                    wrong.put("question", ts.getQuestion());
                    wrong.put("correctAnswer", ts.getAnswer());
                    wrong.put("userAnswer", ua.getUserAnswer());
                    wrong.put("explanation", ts.getExplanation());
                    wrong.put("categoryLarge", ts.getCategoryLarge());
                    wrong.put("difficulty", ts.getDifficulty());
                    wrong.put("createdAt", ua.getCreatedAt());
                    return wrong;
                })
                .collect(Collectors.toList());

        model.addAttribute("pageTitle", "오답노트");
        model.addAttribute("activeMenu", "exam");
        model.addAttribute("wrongAnswers", wrongList);
        model.addAttribute("selectedCategory", category);

        return "examWrongAnswers";
    }

    /**
     * 통계 페이지
     */
    @GetMapping("/statistics")
    public String statisticsPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 Model model) {

        if (userDetails == null) {
            return "redirect:/login";
        }

        model.addAttribute("nickname", userDetails.getNickname());
        model.addAttribute("email", userDetails.getEmail());

        Map<String, Object> stats = examService.getUserStatistics(userDetails.getUserIdx());

        model.addAttribute("pageTitle", "학습 통계");
        model.addAttribute("activeMenu", "exam");
        model.addAttribute("stats", stats);

        return "examStatistics";
    }

    /**
     * 시험 삭제 API
     */
    @DeleteMapping("/api/{testIdx}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteExam(@PathVariable Long testIdx) {

        Map<String, Object> response = new HashMap<>();

        try {
            examService.deleteExam(testIdx);

            response.put("success", true);
            response.put("message", "시험이 삭제되었습니다.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("시험 삭제 실패", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
