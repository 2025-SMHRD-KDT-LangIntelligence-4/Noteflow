package com.smhrd.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.*;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.repository.NoteFolderRepository;
import com.smhrd.web.repository.NoteTagRepository;
import com.smhrd.web.repository.TestItemRepository;
import com.smhrd.web.repository.TestResultRepository;
import com.smhrd.web.repository.UserAnswerRepository;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.ExamService;
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
    private final TestResultRepository testResultRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final TestItemRepository testItemRepository;
    
    
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

     // 🔹 여기가 문제 데이터를 가공하는 부분
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

        // ✅ 여기서 랜덤 20문제 선택
        Collections.shuffle(questions);
        if (questions.size() > 20) {
            questions = questions.subList(0, 20);
        }

        // JSON 변환 후 Thymeleaf에 담기
        ObjectMapper mapper = new ObjectMapper();
        try {
            String questionsJson = mapper.writeValueAsString(questions);
            model.addAttribute("questionsJson", questionsJson);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("questionsJson", "[]"); // 실패 시 빈 배열
        }

        model.addAttribute("pageTitle", test.getTestTitle());
        model.addAttribute("test", test);
        model.addAttribute("questions", questions); // 참고용
        
        return "quizTest";
    }

    /**
     * 시험 제출 API
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
            
            // startTime, endTime 파싱
            String startTimeStr = (String) payload.get("startTime");
            String endTimeStr = (String) payload.get("endTime");
            
            LocalDateTime startTime = startTimeStr != null ? 
                    LocalDateTime.parse(startTimeStr.substring(0, 19)) : LocalDateTime.now().minusMinutes(30);
            LocalDateTime endTime = endTimeStr != null ? 
                    LocalDateTime.parse(endTimeStr.substring(0, 19)) : LocalDateTime.now();
            
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
            
            log.info("파싱된 답안: testIdx={}, answers={}, start={}, end={}", 
                    testIdx, answersMap, startTime, endTime);
            
            // examService로 채점 처리 위임
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
        
        Long userIdx = userDetails.getUserIdx();
        
        // 기존 방식으로 결과 조회
        Map<String, Object> resultData = examService.getResultDetail(resultIdx);
        TestResult result = (TestResult) resultData.get("result");
        List<UserAnswer> userAnswers = (List<UserAnswer>) resultData.get("answers");
        
        // 권한 확인
        if (!result.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }
        
        // ===== 과목별 통계 계산 =====
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
        
        // 정답률 계산
        List<SubjectStat> subjectStats = new ArrayList<>(subjectStatsMap.values());
        subjectStats.forEach(stat -> {
            stat.accuracy = (double) stat.correctCount / stat.totalCount;
        });
        subjectStats.sort((a, b) -> a.subject.compareTo(b.subject));
        
        // ===== 취약과목 & 우수과목 =====
        List<String> weakSubjects = subjectStats.stream()
                .filter(s -> s.accuracy < 0.6)
                .map(s -> s.subject)
                .collect(Collectors.toList());
        
        List<String> strongSubjects = subjectStats.stream()
                .filter(s -> s.accuracy >= 0.8)
                .map(s -> s.subject)
                .collect(Collectors.toList());
        
        // ===== 난이도 변화 계산 =====
        DifficultyChange diffChange = calculateDifficultyChange(userIdx, result);
        
        // ===== Model에 데이터 추가 =====
        model.addAttribute("pageTitle", "시험 결과");
        model.addAttribute("activeMenu", "exam");
        model.addAttribute("result", result);
        model.addAttribute("userAnswers", userAnswers);
        model.addAttribute("subjectStats", subjectStats);
        model.addAttribute("weakSubjects", weakSubjects);
        model.addAttribute("strongSubjects", strongSubjects);
        model.addAttribute("previousDifficulty", diffChange.previousLevel);
        model.addAttribute("currentDifficulty", diffChange.currentLevel);
        model.addAttribute("difficultyChange", diffChange.changeType);
        model.addAttribute("passRate", resultData.get("passRate"));
        
        return "quizResult"; // ← 새 페이지로 변경
    }
    
    /**
     * 난이도 변화 계산
     */
    private DifficultyChange calculateDifficultyChange(Long userIdx, TestResult currentResult) {
        String currentDesc = currentResult.getTest().getTestDesc();
        int currentLevel = extractDifficultyLevel(currentDesc);
        
        // examService의 기존 메서드 활용
        List<TestResult> recentResults = examService.getUserResults(userIdx, 0, 5);
        
        // 현재 결과 제외하고 이전 결과 찾기
        int previousLevel = currentLevel; // ← 기본값을 현재 레벨로 변경
        
        for (TestResult tr : recentResults) {
            if (!tr.getResultIdx().equals(currentResult.getResultIdx())) {
                String prevDesc = tr.getTest().getTestDesc();
                previousLevel = extractDifficultyLevel(prevDesc);
                break;
            }
        }
        
        // 첫 시험인 경우 (이전 결과 없음)
        if (recentResults.size() <= 1 || previousLevel == currentLevel) {
            // 현재 정답률로 메시지 결정
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

    /**
     * TestDesc에서 난이도 레벨 추출
     */
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

    
    /**
     * 시험 해설 페이지 (URL: /exam/explanation/{resultIdx})
     */
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
        
        // 기존 방식으로 결과 조회
        Map<String, Object> resultData = examService.getResultDetail(resultIdx);
        TestResult result = (TestResult) resultData.get("result");
        List<UserAnswer> userAnswers = (List<UserAnswer>) resultData.get("answers");
        
        // 권한 확인
        if (!result.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }
        
        // 해설 데이터 구성
        List<ExplanationData> explanationList = new ArrayList<>();
        
        for (UserAnswer ua : userAnswers) {
            TestSource ts = ua.getTestSource();
            
            ExplanationData data = new ExplanationData();
            data.question = ts.getQuestion();
            data.questionType = ts.getQuestionType().name(); // ← Enum을 String으로 변환
            data.correctAnswer = ts.getAnswer();
            data.explanation = ts.getExplanation();
            data.userAnswer = ua.getUserAnswer();
            data.isCorrect = ua.getIsCorrect();
            
            // 객관식인 경우 options 파싱
            if (QuestionType.MULTIPLE_CHOICE.equals(ts.getQuestionType())) { // ← Enum 비교
                data.options = ts.getOptions(); // ← getOptions() 메서드 사용
            }
            
            explanationList.add(data);
        }
        
        model.addAttribute("pageTitle", "시험 해설");
        model.addAttribute("activeMenu", "exam");
        model.addAttribute("resultIdx", resultIdx);
        model.addAttribute("explanationData", explanationList);
        
        return "quizExplanation";
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
    
 // ===== 내부 DTO 클래스 (ExamController 클래스 안에 추가) =====

    /**
     * 과목별 통계 DTO
     */
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

    /**
     * 난이도 변화 DTO
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DifficultyChange {
        private int previousLevel;
        private int currentLevel;
        private String changeType; // "up", "down", "same"
    }

    /**
     * 해설 데이터 DTO
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExplanationData {
        private String question;
        private String questionType; // ← enum String으로 변경
        private String correctAnswer;
        private String explanation;
        private String userAnswer;
        private Boolean isCorrect;
        private List<String> options; // 객관식 보기
    }
}
