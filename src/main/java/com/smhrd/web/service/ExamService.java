package com.smhrd.web.service;

import com.smhrd.web.controller.ExamController;
import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamService {

    private final TestSourceRepository testSourceRepository;
    private final TestRepository testRepository;
    private final TestItemRepository testItemRepository;
    private final TestResultRepository testResultRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final UserRepository userRepository;

    /**
     * 키워드 기반 시험 생성 (적응형 난이도 + 자동 문제 개수)
     */
    @Transactional
    public Test createExamFromKeywords(List<String> keywords, String title, Long userIdx,
                                       String difficulty, Integer questionCount, Integer scorePerQuestion,
                                       Boolean adaptiveDifficulty) {

        log.info("키워드 기반 시험 생성: keywords={}, adaptive={}", keywords, adaptiveDifficulty);

        // 적응형 난이도 계산
        String targetDifficulty = difficulty;
        if (adaptiveDifficulty != null && adaptiveDifficulty && userIdx != null) {
            targetDifficulty = calculateAdaptiveDifficulty(userIdx, keywords);
            log.info("적응형 난이도 계산 결과: Level {}", targetDifficulty);
        }

        // 키워드별로 문제 균등 배분
        int questionsPerKeyword = questionCount / keywords.size();
        int remainder = questionCount % keywords.size();

        List<TestSource> allQuestions = new ArrayList<>();

        for (int i = 0; i < keywords.size(); i++) {
            String keyword = keywords.get(i);
            int count = questionsPerKeyword + (i < remainder ? 1 : 0); // 나머지 분배

            // 키워드별 문제 조회
            List<TestSource> keywordQuestions = findQuestionsByKeyword(keyword, targetDifficulty);

            if (keywordQuestions.isEmpty()) {
                log.warn("키워드 '{}' 문제 없음, 난이도 무시하고 재시도", keyword);
                keywordQuestions = findQuestionsByKeyword(keyword, null);
            }

            if (keywordQuestions.isEmpty()) {
                log.warn("키워드 '{}' 문제 없음 (스킵)", keyword);
                continue;
            }

            // 랜덤 선택
            Collections.shuffle(keywordQuestions);
            List<TestSource> selected = keywordQuestions.stream()
                    .limit(count)
                    .collect(Collectors.toList());

            allQuestions.addAll(selected);
            log.info("키워드 '{}': {}개 문제 선택", keyword, selected.size());
        }

        if (allQuestions.isEmpty()) {
            throw new IllegalStateException("키워드 '" + String.join(", ", keywords) + "'와 일치하는 문제가 없습니다.");
        }

        // Test 생성
        String testDesc = "키워드: " + String.join(", ", keywords);
        if (adaptiveDifficulty != null && adaptiveDifficulty && targetDifficulty != null) {
            testDesc += " (적응형 난이도: Level " + targetDifficulty + ")";
        }

        Test test = Test.builder()
                .testTitle(title)
                .testDesc(testDesc)
                .build();

        test = testRepository.save(test);

        // TestItem 생성
        int sequence = 1;
        int defaultScore = scorePerQuestion != null ? scorePerQuestion : 1;

        for (TestSource question : allQuestions) {
            TestItem item = TestItem.builder()
                    .test(test)
                    .testSource(question)
                    .sequence(sequence++)
                    .score(defaultScore)
                    .build();

            testItemRepository.save(item);
        }

        log.info("시험 생성 완료: testIdx={}, 문항 수={}", test.getTestIdx(), allQuestions.size());

        return test;
    }

    /**
     * 적응형 난이도 계산
     */
    private String calculateAdaptiveDifficulty(Long userIdx, List<String> keywords) {
        // 최근 시험 결과 조회 (최대 5개)
        Page<TestResult> recentPage = testResultRepository.findRecentResultsByUser(
                userIdx, PageRequest.of(0, 5));

        List<TestResult> recentResults = recentPage.getContent();

        if (recentResults.isEmpty()) {
            log.info("첫 시험 → 기본 난이도 Level 2");
            return "2"; // 첫 시험은 Level 2부터 시작
        }

        // 평균 정답률 계산
        double avgCorrectRate = recentResults.stream()
                .mapToDouble(r -> {
                    int total = r.getCorrectCount() + r.getWrongCount();
                    return total > 0 ? (double) r.getCorrectCount() / total : 0;
                })
                .average()
                .orElse(0.5);

        // 최근 난이도 추출
        String lastDifficulty = "2";
        TestResult lastResult = recentResults.get(0);
        String desc = lastResult.getTest().getTestDesc();

        if (desc != null && desc.contains("Level ")) {
            try {
                int startIdx = desc.indexOf("Level ") + 6;
                lastDifficulty = desc.substring(startIdx, startIdx + 1);
            } catch (Exception e) {
                log.warn("난이도 파싱 실패, 기본값 사용");
                lastDifficulty = "2";
            }
        }

        int currentLevel = Integer.parseInt(lastDifficulty);

        // 난이도 조절
        if (avgCorrectRate >= 0.8) {
            currentLevel = Math.min(5, currentLevel + 1);
            log.info("정답률 {}% → 난이도 상승: Level {}", (int)(avgCorrectRate * 100), currentLevel);
        } else if (avgCorrectRate < 0.6) {
            currentLevel = Math.max(1, currentLevel - 1);
            log.info("정답률 {}% → 난이도 하락: Level {}", (int)(avgCorrectRate * 100), currentLevel);
        } else {
            log.info("정답률 {}% → 난이도 유지: Level {}", (int)(avgCorrectRate * 100), currentLevel);
        }

        return String.valueOf(currentLevel);
    }

    /**
     * 키워드로 문제 검색
     */
    private List<TestSource> findQuestionsByKeyword(String keyword, String difficulty) {
        List<TestSource> questions = new ArrayList<>();

        // 1. 카테고리 대분류
        if (difficulty != null && !difficulty.isEmpty()) {
            questions.addAll(testSourceRepository.findByCategoryLargeAndDifficulty(keyword, difficulty));
        } else {
            questions.addAll(testSourceRepository.findByCategoryLarge(keyword));
        }

        // 2. 카테고리 중분류
        if (questions.isEmpty()) {
            questions = testSourceRepository.findAll().stream()
                    .filter(ts -> keyword.equals(ts.getCategoryMedium()))
                    .filter(ts -> difficulty == null || difficulty.isEmpty() || difficulty.equals(ts.getDifficulty()))
                    .collect(Collectors.toList());
        }

        // 3. 키워드 검색
        if (questions.isEmpty()) {
            List<TestSource> searchResults = testSourceRepository.searchByQuestionKeyword(keyword);
            if (difficulty != null && !difficulty.isEmpty()) {
                questions = searchResults.stream()
                        .filter(ts -> difficulty.equals(ts.getDifficulty()))
                        .collect(Collectors.toList());
            } else {
                questions = searchResults;
            }
        }

        return questions;
    }

    /**
     * 시험 조회 (문항 포함)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExamWithQuestions(Long testIdx) {
        Test test = testRepository.findById(testIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시험입니다."));

        List<TestItem> items = testItemRepository.findByTestTestIdxOrderBySequenceAsc(testIdx);

        Map<String, Object> result = new HashMap<>();
        result.put("test", test);
        result.put("items", items);
        result.put("totalScore", testItemRepository.sumScoreByTestIdx(testIdx));
        result.put("questionCount", items.size());

        return result;
    }

    /**
     * 시험 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public Page<Test> getExamList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return testRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
    
    /**
     * 시험 채점
     */
    @Transactional
    public TestResult gradeExam(Long userIdx, Long testIdx, List<ExamController.SubmitRequest.AnswerItem> answers) {
        
        User user = userRepository.findById(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        Test test = testRepository.findById(testIdx)
                .orElseThrow(() -> new IllegalArgumentException("시험을 찾을 수 없습니다."));
        
        // TestItem 조회
        List<TestItem> testItems = testItemRepository.findByTestOrderBySequenceAsc(test);
        
        int correctCount = 0;
        int wrongCount = 0;
        int totalScore = 0;
        int userScore = 0;
        
        // 채점 및 UserAnswer 저장을 위한 임시 결과 생성
        TestResult tempResult = TestResult.builder()
                .user(user)
                .test(test)
                .totalScore(0)
                .userScore(0)
                .correctCount(0)
                .wrongCount(0)
                .build();
        
        TestResult savedResult = testResultRepository.save(tempResult);
        
        // 각 문제 채점
        for (TestItem item : testItems) {
            TestSource source = item.getTestSource();
            totalScore += item.getScore();
            
            // 사용자 답안 찾기
            String userAnswer = answers.stream()
                    .filter(a -> a.getTestSourceIdx().equals(source.getTestSourceIdx()))
                    .map(ExamController.SubmitRequest.AnswerItem::getUserAnswer)
                    .findFirst()
                    .orElse(null);
            
            // 정답 체크
            boolean isCorrect = checkAnswer(source.getAnswer(), userAnswer);
            
            if (isCorrect) {
                correctCount++;
                userScore += item.getScore();
            } else {
                wrongCount++;
            }
            
            // UserAnswer 저장
            UserAnswer userAnswerEntity = UserAnswer.builder()
                    .result(savedResult)
                    .testSource(source)
                    .userAnswer(userAnswer)
                    .isCorrect(isCorrect)
                    .build();
            
            userAnswerRepository.save(userAnswerEntity);
        }
        
        // 결과 업데이트
        savedResult.setTotalScore(totalScore);
        savedResult.setUserScore(userScore);
        savedResult.setCorrectCount(correctCount);
        savedResult.setWrongCount(wrongCount);
        
        return testResultRepository.save(savedResult);
    }

    /**
     * 정답 체크
     */
    private boolean checkAnswer(String correctAnswer, String userAnswer) {
        if (userAnswer == null || userAnswer.trim().isEmpty()) {
            return false;
        }
        
        // 공백 제거 후 대소문자 무시하고 비교
        String correct = correctAnswer.trim().toLowerCase();
        String user = userAnswer.trim().toLowerCase();
        
        return correct.equals(user);
    }

    /**
     * 시험 제출 및 채점
     */
    @Transactional
    public TestResult submitExam(Long testIdx, Long userIdx, Map<Long, String> answers,
                                 LocalDateTime startTime, LocalDateTime endTime) {

        log.info("시험 제출: testIdx={}, userIdx={}, 답안 수={}", testIdx, userIdx, answers.size());

        Test test = testRepository.findById(testIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시험입니다."));

        User user = userRepository.findById(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        List<TestItem> items = testItemRepository.findByTestTestIdxOrderBySequenceAsc(testIdx);

        // 채점
        int totalScore = testItemRepository.sumScoreByTestIdx(testIdx);
        int userScore = 0;
        int correctCount = 0;
        int wrongCount = 0;

        List<UserAnswer> userAnswers = new ArrayList<>();

        for (TestItem item : items) {
            Long sourceIdx = item.getTestSource().getTestSourceIdx();
            String userAnswer = answers.get(sourceIdx);
            String correctAnswer = item.getTestSource().getAnswer();

            boolean isCorrect = checkAnswer(userAnswer, correctAnswer);

            if (isCorrect) {
                userScore += item.getScore();
                correctCount++;
            } else {
                wrongCount++;
            }

            UserAnswer ua = UserAnswer.builder()
                    .testSource(item.getTestSource())
                    .userAnswer(userAnswer)
                    .isCorrect(isCorrect)
                    .build();

            userAnswers.add(ua);
        }

        // TestResult 저장
        int duration = (int) java.time.Duration.between(startTime, endTime).toMinutes();
        boolean passed = (double) userScore / totalScore >= 0.6; // 60% 합격 기준

        TestResult result = TestResult.builder()
                .user(user)
                .test(test)
                .totalScore(totalScore)
                .userScore(userScore)
                .correctCount(correctCount)
                .wrongCount(wrongCount)
                .testDuration(duration)
                .passed(passed)
                .startTime(startTime)
                .endTime(endTime)
                .build();

        result = testResultRepository.save(result);

        // UserAnswer 저장
        for (UserAnswer ua : userAnswers) {
            ua.setResult(result);
            userAnswerRepository.save(ua);
        }

        log.info("채점 완료: resultIdx={}, 점수={}/{}, 합격={}",
                result.getResultIdx(), userScore, totalScore, passed);

        return result;
    }

    

    /**
     * 사용자 시험 결과 조회
     */
    @Transactional(readOnly = true)
    public List<TestResult> getUserResults(Long userIdx, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TestResult> results = testResultRepository.findByUserUserIdxOrderByCreatedAtDesc(userIdx, pageable);
        return results.getContent();
    }

    /**
     * 시험 결과 상세 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getResultDetail(Long resultIdx) {
        TestResult result = testResultRepository.findById(resultIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 결과입니다."));

        List<UserAnswer> answers = userAnswerRepository.findByResultResultIdxOrderByTestSourceTestSourceIdxAsc(resultIdx);

        Map<String, Object> data = new HashMap<>();
        data.put("result", result);
        data.put("answers", answers);
        data.put("passRate", String.format("%.1f", (double) result.getUserScore() / result.getTotalScore() * 100));

        return data;
    }

    /**
     * 오답노트 조회
     */
    @Transactional(readOnly = true)
    public List<UserAnswer> getWrongAnswers(Long userIdx, String categoryLarge) {
        if (categoryLarge != null && !categoryLarge.isEmpty()) {
            return userAnswerRepository.findWrongAnswersByUserAndCategory(userIdx, categoryLarge);
        }
        return userAnswerRepository.findWrongAnswersByUser(userIdx);
    }

    /**
     * 사용자 통계
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserStatistics(Long userIdx) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalTests", testResultRepository.countByUserUserIdx(userIdx));
        stats.put("passedTests", testResultRepository.countByUserUserIdxAndPassedTrue(userIdx));
        stats.put("averageScore", testResultRepository.findAverageScoreByUser(userIdx));

        return stats;
    }

    /**
     * 시험 삭제
     */
    @Transactional
    public void deleteExam(Long testIdx) {
        if (!testRepository.existsById(testIdx)) {
            throw new IllegalArgumentException("존재하지 않는 시험입니다.");
        }

        testRepository.deleteById(testIdx);
        log.info("시험 삭제 완료: testIdx={}", testIdx);
    }

    /**
     * 카테고리별 문제 개수 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getQuestionCountByCategory() {
        List<TestSource> allSources = testSourceRepository.findAll();

        return allSources.stream()
                .filter(s -> s.getCategoryLarge() != null)
                .collect(Collectors.groupingBy(
                        TestSource::getCategoryLarge,
                        Collectors.counting()
                ));
    }

    /**
     * 시험 제출 및 채점
     */
    @Transactional
    public Long submitAndGrade(Long userIdx, Long testIdx, Map<Integer, String> answers) {
        
        User user = userRepository.findById(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        Test test = testRepository.findById(testIdx)
                .orElseThrow(() -> new IllegalArgumentException("시험을 찾을 수 없습니다."));
        
        // TestItem 조회
        List<TestItem> testItems = testItemRepository.findByTestOrderBySequenceAsc(test);
        
        int correctCount = 0;
        int wrongCount = 0;
        int totalScore = 0;
        int userScore = 0;
        
        // 시작 시간: Test 생성 시간 사용
        LocalDateTime startTime = test.getCreatedAt();
        // 종료 시간: 현재 시간
        LocalDateTime endTime = LocalDateTime.now();
        
        // 결과 생성 (필수 필드 모두 포함)
        TestResult result = TestResult.builder()
                .user(user)
                .test(test)
                .totalScore(0)
                .userScore(0)
                .correctCount(0)
                .wrongCount(0)
                .passed(false)
                .startTime(startTime) // ← Test 생성 시간
                .endTime(endTime)     // ← 제출 시간
                .build();
        
        TestResult savedResult = testResultRepository.save(result);
        
        // 각 문제 채점
        for (int i = 0; i < testItems.size(); i++) {
            TestItem item = testItems.get(i);
            TestSource source = item.getTestSource();
            totalScore += item.getScore();
            
            String userAnswer = answers.get(i);
            boolean isCorrect = checkAnswer(source.getAnswer(), userAnswer);
            
            if (isCorrect) {
                correctCount++;
                userScore += item.getScore();
            } else {
                wrongCount++;
            }
            
            UserAnswer userAnswerEntity = UserAnswer.builder()
                    .result(savedResult)
                    .testSource(source)
                    .userAnswer(userAnswer)
                    .isCorrect(isCorrect)
                    .build();
            
            userAnswerRepository.save(userAnswerEntity);
        }
        
        // 합격 여부 (60% 이상)
        boolean passed = totalScore > 0 && ((double) userScore / totalScore) >= 0.6;
        
        // 결과 업데이트
        savedResult.setTotalScore(totalScore);
        savedResult.setUserScore(userScore);
        savedResult.setCorrectCount(correctCount);
        savedResult.setWrongCount(wrongCount);
        savedResult.setPassed(passed);
        
        testResultRepository.save(savedResult);
        
        log.info("채점 완료: resultIdx={}, 정답={}/{}, 합격={}, 소요시간={}분", 
                savedResult.getResultIdx(), correctCount, testItems.size(), passed,
                java.time.Duration.between(startTime, endTime).toMinutes());
        
        return savedResult.getResultIdx();
    }

    


    
}
