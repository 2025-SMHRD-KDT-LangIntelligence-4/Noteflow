package com.smhrd.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.*;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.ExamService;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.repository.NoteFolderRepository;
import com.smhrd.web.repository.NoteTagRepository;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

    // ===== 기존 메서드들 (변경 없음) =====

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
            model.addAttribute("activeMenu", "quizCreate");

            Long preselectedNoteIdx = (Long) session.getAttribute("preselectedNoteIdx");
            String preselectedNoteTitle = (String) session.getAttribute("preselectedNoteTitle");
            List<String> preselectedKeywords = (List<String>) session.getAttribute("preselectedKeywords");

            if (preselectedNoteIdx != null) {
                model.addAttribute("preselectedNoteIdx", preselectedNoteIdx);
                model.addAttribute("preselectedNoteTitle", preselectedNoteTitle);
                model.addAttribute("preselectedKeywords", preselectedKeywords != null ? preselectedKeywords : Collections.emptyList());

                session.removeAttribute("preselectedNoteIdx");
                session.removeAttribute("preselectedNoteTitle");
                session.removeAttribute("preselectedKeywords");

                log.info("세션에서 노트 정보 로드: noteIdx={}, title={}",
                        preselectedNoteIdx, preselectedNoteTitle);
            }

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

            List<Map<String, Object>> notesData = allNotes.stream()
                    .map(note -> {
                        Map<String, Object> noteMap = new HashMap<>();
                        noteMap.put("noteIdx", note.getNoteIdx());
                        noteMap.put("title", note.getTitle());
                        noteMap.put("createdAt", note.getCreatedAt().toString());
                        noteMap.put("folderId", note.getFolderId());

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

                    List<Map<String, Object>> subfolders = buildFolderTree(
                            allFolders, folder.getFolderId(), allNotes);
                    folderMap.put("subfolders", subfolders);

                    List<Map<String, Object>> notes = allNotes.stream()
                            .filter(n -> folder.getFolderId().equals(n.get("folderId")))
                            .collect(Collectors.toList());
                    folderMap.put("notes", notes);

                    return folderMap;
                })
                .collect(Collectors.toList());
    }

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
        model.addAttribute("activeMenu", "examList");
        model.addAttribute("tests", testPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", testPage.getTotalPages());
        model.addAttribute("totalItems", testPage.getTotalElements());

        return "examList";
    }

    @GetMapping("/solve/{testIdx}")
    public String solvePage(@PathVariable Long testIdx,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            Model model) {
        if (userDetails != null) {
            model.addAttribute("nickname", userDetails.getNickname());
            model.addAttribute("email", userDetails.getEmail());
            model.addAttribute("userIdx", userDetails.getUserIdx());
            model.addAttribute("activeMenu", "quizCreate");
        }

        // ✅ Service 사용
        Map<String, Object> examData = examService.getExamWithQuestions(testIdx);
        Test test = (Test) examData.get("test");
        List<TestItem> items = (List<TestItem>) examData.get("items");

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

        ObjectMapper mapper = new ObjectMapper();
        try {
            String questionsJson = mapper.writeValueAsString(questions);
            model.addAttribute("questionsJson", questionsJson);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("questionsJson", "[]");
        }

        model.addAttribute("pageTitle", test.getTestTitle());
        model.addAttribute("test", test);
        model.addAttribute("questions", questions);

        return "quizTest";
    }

    /**
     * ✅ 수정: ExamService.submitAndGrade() 사용 (이벤트 발행 포함)
     */
    @PostMapping("/api/submit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitExamApi(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("시험 제출 요청 받음: {}", payload);

            Long userIdx = userDetails.getUserIdx();
            Long testIdx = Long.valueOf(payload.get("testIdx").toString());

            // answers 파싱
            Object answersObj = payload.get("answers");
            Map<Integer, String> answersMap = new HashMap<>();

            if (answersObj instanceof Map) {
                Map<?, ?> rawMap = (Map<?, ?>) answersObj;
                rawMap.forEach((k, v) -> {
                    try {
                        int index = Integer.parseInt(String.valueOf(k));
                        String answer = String.valueOf(v);
                        answersMap.put(index, answer);
                    } catch (Exception e) {
                        log.warn("답안 파싱 실패: key={}, value={}", k, v);
                    }
                });
            }

            log.info("파싱된 답안: testIdx={}, answers={}", testIdx, answersMap);

            // ✅ ExamService로 위임 (이벤트 발행 포함)
            Long resultIdx = examService.submitAndGrade(userIdx, testIdx, answersMap);

            response.put("success", true);
            response.put("resultIdx", resultIdx);
            log.info("제출 완료: resultIdx={}", resultIdx);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("시험 제출 중 오류 발생", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * ✅ 수정: ExamService.getResultDetail() 사용
     */
    @GetMapping("/result/{resultIdx}")
    public String resultPage(@PathVariable Long resultIdx,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             Model model) {
        if (userDetails != null) {
            model.addAttribute("nickname", userDetails.getNickname());
            model.addAttribute("email", userDetails.getEmail());
            model.addAttribute("activeMenu", "examList");
        }

        Long userIdx = userDetails.getUserIdx();

        // ✅ Service 사용
        Map<String, Object> resultData = examService.getResultDetail(resultIdx);
        TestResult result = (TestResult) resultData.get("result");
        List<UserAnswer> userAnswers = (List<UserAnswer>) resultData.get("answers");

        // 권한 확인
        if (!result.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        // 과목별 통계
        Map<String, SubjectStat> subjectStatsMap = new HashMap<>();
        for (UserAnswer ua : userAnswers) {
            String subject = ua.getTestSource().getCategoryLarge();
            if (subject == null || subject.isEmpty()) {
                subject = "기타";
            }

            SubjectStat stat = subjectStatsMap.getOrDefault(subject, new SubjectStat(subject));
            stat.totalCount++;
            if (ua.getIsCorrect()) {
                stat.correctCount++;
            }
            subjectStatsMap.put(subject, stat);
        }

        List<SubjectStat> subjectStats = new ArrayList<>(subjectStatsMap.values());
        subjectStats.forEach(stat -> {
            stat.accuracy = (double) stat.correctCount / stat.totalCount;
        });
        subjectStats.sort((a, b) -> a.subject.compareTo(b.subject));

        List<String> weakSubjects = subjectStats.stream()
                .filter(s -> s.accuracy < 0.6)
                .map(s -> s.subject)
                .collect(Collectors.toList());

        List<String> strongSubjects = subjectStats.stream()
                .filter(s -> s.accuracy >= 0.8)
                .map(s -> s.subject)
                .collect(Collectors.toList());

        DifficultyChange diffChange = calculateDifficultyChange(userIdx, result);

        model.addAttribute("pageTitle", "시험 결과");
        model.addAttribute("activeMenu", "examList");
        model.addAttribute("result", result);
        model.addAttribute("userAnswers", userAnswers);
        model.addAttribute("subjectStats", subjectStats);
        model.addAttribute("weakSubjects", weakSubjects);
        model.addAttribute("strongSubjects", strongSubjects);
        model.addAttribute("previousDifficulty", diffChange.previousLevel);
        model.addAttribute("currentDifficulty", diffChange.currentLevel);
        model.addAttribute("difficultyChange", diffChange.changeType);
        model.addAttribute("passRate", resultData.get("passRate"));

        return "quizResult";
    }

    private DifficultyChange calculateDifficultyChange(Long userIdx, TestResult currentResult) {
        String currentDesc = currentResult.getTest().getTestDesc();
        int currentLevel = extractDifficultyLevel(currentDesc);

        // ✅ Service 사용
        List<TestResult> recentResults = examService.getUserResults(userIdx, 0, 5);

        int previousLevel = currentLevel;
        for (TestResult tr : recentResults) {
            if (!tr.getResultIdx().equals(currentResult.getResultIdx())) {
                String prevDesc = tr.getTest().getTestDesc();
                previousLevel = extractDifficultyLevel(prevDesc);
                break;
            }
        }

        if (recentResults.size() <= 1 || previousLevel == currentLevel) {
            int total = currentResult.getCorrectCount() + currentResult.getWrongCount();
            double accuracy = total > 0 ? (double) currentResult.getCorrectCount() / total : 0;

            if (accuracy >= 0.8) {
                return new DifficultyChange(currentLevel, currentLevel, "up");
            } else if (accuracy < 0.6) {
                return new DifficultyChange(currentLevel, currentLevel, "down");
            } else {
                return new DifficultyChange(currentLevel, currentLevel, "same");
            }
        }

        String changeType = "same";
        if (currentLevel > previousLevel) {
            changeType = "up";
        } else if (currentLevel < previousLevel) {
            changeType = "down";
        }

        return new DifficultyChange(previousLevel, currentLevel, changeType);
    }

    private int extractDifficultyLevel(String testDesc) {
        if (testDesc == null) return 2;

        try {
            if (testDesc.contains("Level ")) {
                int startIdx = testDesc.indexOf("Level ") + 6;
                String levelStr = testDesc.substring(startIdx, startIdx + 1);
                return Integer.parseInt(levelStr);
            }
        } catch (Exception e) {
            log.warn("난이도 파싱 실패: {}", testDesc);
        }

        return 2;
    }

    @GetMapping("/explanation/{resultIdx}")
    public String showExplanation(@PathVariable Long resultIdx,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        model.addAttribute("nickname", userDetails.getNickname());
        model.addAttribute("email", userDetails.getEmail());

        Long userIdx = userDetails.getUserIdx();

        // ✅ Service 사용
        Map<String, Object> resultData = examService.getResultDetail(resultIdx);
        TestResult result = (TestResult) resultData.get("result");
        List<UserAnswer> userAnswers = (List<UserAnswer>) resultData.get("answers");

        if (!result.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        List<ExplanationData> explanationList = new ArrayList<>();
        for (UserAnswer ua : userAnswers) {
            TestSource ts = ua.getTestSource();
            ExplanationData data = new ExplanationData();
            data.question = ts.getQuestion();
            data.questionType = ts.getQuestionType().name();
            data.correctAnswer = ts.getAnswer();
            data.explanation = ts.getExplanation();
            data.userAnswer = ua.getUserAnswer();
            data.isCorrect = ua.getIsCorrect();

            if (QuestionType.MULTIPLE_CHOICE.equals(ts.getQuestionType())) {
                data.options = ts.getOptions();
            }

            explanationList.add(data);
        }

        model.addAttribute("pageTitle", "시험 해설");
        model.addAttribute("activeMenu", "examList");
        model.addAttribute("resultIdx", resultIdx);
        model.addAttribute("explanationData", explanationList);

        return "quizExplanation";
    }

    /**
     * ✅ 수정: ExamService.getUserResults() 사용
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

        Long userIdx = userDetails.getUserIdx();

        // ✅ Service 사용
        List<TestResult> results = examService.getUserResults(userIdx, page, size);

        List<Map<String, Object>> resultList = results.stream()
                .map(result -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("resultIdx", result.getResultIdx());
                    data.put("testTitle", result.getTest().getTestTitle());
                    data.put("testDesc", result.getTest().getTestDesc());
                    data.put("createdAt", result.getCreatedAt());
                    data.put("totalScore", result.getTotalScore());
                    data.put("userScore", result.getUserScore());
                    data.put("correctCount", result.getCorrectCount());
                    data.put("wrongCount", result.getWrongCount());
                    data.put("passed", result.getPassed());

                    int total = result.getCorrectCount() + result.getWrongCount();
                    double accuracy = total > 0 ? (double) result.getCorrectCount() / total * 100 : 0;
                    data.put("accuracy", accuracy);

                    String difficulty = "2";
                    if (result.getTest().getTestDesc() != null && result.getTest().getTestDesc().contains("Level ")) {
                        try {
                            int idx = result.getTest().getTestDesc().indexOf("Level ") + 6;
                            difficulty = result.getTest().getTestDesc().substring(idx, idx + 1);
                        } catch (Exception e) {
                            // 파싱 실패 시 기본값
                        }
                    }
                    data.put("difficulty", difficulty);

                    return data;
                })
                .collect(Collectors.toList());

        model.addAttribute("pageTitle", "내 시험 기록");
        model.addAttribute("activeMenu", "examList");
        model.addAttribute("results", resultList);
        model.addAttribute("currentPage", page);
        model.addAttribute("hasMore", results.size() >= size);

        return "examMyResults";
    }

    /**
     * ✅ 수정: ExamService.getWrongAnswers() 사용
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

        // ✅ Service 사용
        List<UserAnswer> wrongAnswers = examService.getWrongAnswers(userDetails.getUserIdx(), category);

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
        model.addAttribute("activeMenu", "examList");
        model.addAttribute("wrongAnswers", wrongList);
        model.addAttribute("selectedCategory", category);

        return "examWrongAnswers";
    }

    /**
     * ✅ 수정: ExamService.getUserStatistics() 사용
     */
    @GetMapping("/statistics")
    public String statisticsPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        model.addAttribute("nickname", userDetails.getNickname());
        model.addAttribute("email", userDetails.getEmail());

        // ✅ Service 사용
        Map<String, Object> stats = examService.getUserStatistics(userDetails.getUserIdx());

        model.addAttribute("pageTitle", "학습 통계");
        model.addAttribute("activeMenu", "examList");
        model.addAttribute("stats", stats);

        return "examStatistics";
    }

    /**
     * ✅ 수정: ExamService.deleteExam() 사용
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

    // ===== DTO 클래스들 =====

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubjectStat {
        private String subject;
        private int totalCount;
        private int correctCount;
        private double accuracy;

        public SubjectStat(String subject) {
            this.subject = subject;
            this.totalCount = 0;
            this.correctCount = 0;
            this.accuracy = 0.0;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DifficultyChange {
        private int previousLevel;
        private int currentLevel;
        private String changeType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExplanationData {
        private String question;
        private String questionType;
        private String correctAnswer;
        private String explanation;
        private String userAnswer;
        private Boolean isCorrect;
        private List<String> options;
    }

    @Data
    public static class SubmitRequest {
        private Long testIdx;
        private List<AnswerItem> answers;

        @Data
        public static class AnswerItem {
            private Long testSourceIdx;
            private String userAnswer;
        }
    }
}
