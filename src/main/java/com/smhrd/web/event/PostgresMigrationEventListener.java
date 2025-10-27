package com.smhrd.web.event;

import com.smhrd.web.entity.Note;
import com.smhrd.web.entity.TestResult;
import com.smhrd.web.entity.UserAnswer;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.repository.TestResultRepository;
import com.smhrd.web.repository.UserAnswerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresMigrationEventListener {

    private final NoteRepository noteRepository;
    private final TestResultRepository testResultRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final RestTemplate restTemplate;

    @Autowired
    @Qualifier("postgresNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate postgresTemplate;

    @Autowired
    @Qualifier("mysqlNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate mysqlTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNoteSaved(NoteSavedEvent event) {
        log.info("🔄 [자동 마이그레이션] 노트 {} 마이그레이션 시작", event.getNoteIdx());

        try {
            Note note = noteRepository.findById(event.getNoteIdx())
                    .orElseThrow(() -> new IllegalArgumentException("노트를 찾을 수 없습니다: " + event.getNoteIdx()));

            List<Double> embedding = generateEmbedding(note.getContent());
            String vectorString = formatVector(embedding);

            String folderPath = getFolderPath(note.getFolderId());
            String[] tags = getNoteTags(note);

            String sql = """
                INSERT INTO user_notes 
                (note_idx, user_idx, title, content, embedding, folder_id, folder_path, tags, created_at)
                VALUES 
                (:noteIdx, :userIdx, :title, :content, CAST(:embedding AS vector), 
                 :folderId, :folderPath, :tags, :createdAt)
                ON CONFLICT (note_idx) DO UPDATE SET
                    title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    embedding = CAST(:embedding AS vector),
                    folder_id = EXCLUDED.folder_id,
                    folder_path = EXCLUDED.folder_path,
                    tags = EXCLUDED.tags,
                    updated_at = CURRENT_TIMESTAMP
                """;

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("noteIdx", note.getNoteIdx())
                    .addValue("userIdx", note.getUser().getUserIdx())
                    .addValue("title", note.getTitle())
                    .addValue("content", note.getContent())
                    .addValue("embedding", vectorString)
                    .addValue("folderId", note.getFolderId())
                    .addValue("folderPath", folderPath)
                    .addValue("tags", tags)
                    .addValue("createdAt", note.getCreatedAt());

            postgresTemplate.update(sql, params);
            log.info("✅ [자동 마이그레이션] 노트 {} 마이그레이션 완료", event.getNoteIdx());

        } catch (Exception e) {
            log.error("❌ [자동 마이그레이션] 노트 {} 마이그레이션 실패: {}", event.getNoteIdx(), e.getMessage(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleExamResultSaved(ExamResultSavedEvent event) {
        log.info("🔄 [자동 마이그레이션] 시험 결과 {} 마이그레이션 시작", event.getResultIdx());

        try {
            TestResult result = testResultRepository.findById(event.getResultIdx())
                    .orElseThrow(() -> new IllegalArgumentException("시험 결과를 찾을 수 없습니다: " + event.getResultIdx()));

            List<UserAnswer> answers = userAnswerRepository.findByResultResultIdxOrderByTestSourceTestSourceIdxAsc(event.getResultIdx());

            String resultSql = """
                INSERT INTO exam_results 
                (result_idx, user_idx, test_idx, total_score, user_score, correct_count, wrong_count, 
                 test_duration, passed, start_time, end_time, created_at)
                VALUES 
                (:resultIdx, :userIdx, :testIdx, :totalScore, :userScore, :correctCount, :wrongCount,
                 :testDuration, :passed, :startTime, :endTime, :createdAt)
                ON CONFLICT (result_idx) DO UPDATE SET
                    total_score = EXCLUDED.total_score,
                    user_score = EXCLUDED.user_score,
                    correct_count = EXCLUDED.correct_count,
                    wrong_count = EXCLUDED.wrong_count,
                    passed = EXCLUDED.passed,
                    updated_at = CURRENT_TIMESTAMP
                """;

            MapSqlParameterSource resultParams = new MapSqlParameterSource()
                    .addValue("resultIdx", result.getResultIdx())
                    .addValue("userIdx", result.getUser().getUserIdx())
                    .addValue("testIdx", result.getTest().getTestIdx())
                    .addValue("totalScore", result.getTotalScore())
                    .addValue("userScore", result.getUserScore())
                    .addValue("correctCount", result.getCorrectCount())
                    .addValue("wrongCount", result.getWrongCount())
                    .addValue("testDuration", result.getTestDuration())
                    .addValue("passed", result.getPassed())
                    .addValue("startTime", result.getStartTime())
                    .addValue("endTime", result.getEndTime())
                    .addValue("createdAt", result.getCreatedAt());

            postgresTemplate.update(resultSql, resultParams);

            for (UserAnswer answer : answers) {
                if (!answer.getIsCorrect()) {
                    migrateWrongAnswer(result, answer);
                }
            }

            log.info("✅ [자동 마이그레이션] 시험 결과 {} 마이그레이션 완료 (오답 {}개)",
                    event.getResultIdx(), result.getWrongCount());

        } catch (Exception e) {
            log.error("❌ [자동 마이그레이션] 시험 결과 {} 마이그레이션 실패: {}",
                    event.getResultIdx(), e.getMessage(), e);
        }
    }

    private void migrateWrongAnswer(TestResult result, UserAnswer answer) {
        try {
            String question = answer.getTestSource().getQuestion();
            String correctAnswer = answer.getTestSource().getAnswer();
            String userAnswer = answer.getUserAnswer();
            String category = answer.getTestSource().getCategoryLarge();

            String wrongNoteContent = String.format("""
                [틀린 문제]
                %s
                
                [정답]: %s
                [내 답]: %s
                
                [카테고리]: %s
                [시험]: %s
                [시험 날짜]: %s
                """,
                    question, correctAnswer, userAnswer,
                    category, result.getTest().getTestTitle(), result.getCreatedAt());

            List<Double> embedding = generateEmbedding(wrongNoteContent);
            String vectorString = formatVector(embedding);

            String sql = """
                INSERT INTO wrong_answer_notes 
                (user_idx, result_idx, test_source_idx, question, correct_answer, user_answer, 
                 category, content, embedding, created_at)
                VALUES 
                (:userIdx, :resultIdx, :testSourceIdx, :question, :correctAnswer, :userAnswer,
                 :category, :content, CAST(:embedding AS vector), :createdAt)
                ON CONFLICT (result_idx, test_source_idx) DO UPDATE SET
                    content = EXCLUDED.content,
                    embedding = CAST(:embedding AS vector),
                    updated_at = CURRENT_TIMESTAMP
                """;

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("userIdx", result.getUser().getUserIdx())
                    .addValue("resultIdx", result.getResultIdx())
                    .addValue("testSourceIdx", answer.getTestSource().getTestSourceIdx())
                    .addValue("question", question)
                    .addValue("correctAnswer", correctAnswer)
                    .addValue("userAnswer", userAnswer)
                    .addValue("category", category)
                    .addValue("content", wrongNoteContent)
                    .addValue("embedding", vectorString)
                    .addValue("createdAt", result.getCreatedAt());

            postgresTemplate.update(sql, params);

        } catch (Exception e) {
            log.warn("⚠️ 오답 노트 마이그레이션 실패: {}", e.getMessage());
        }
    }

    /**
     * ✅ 수정: 폴더 경로 조회
     */
    private String getFolderPath(Long folderId) {
        if (folderId == null) {
            return "/";
        }

        try {
            String sql = "SELECT folder_name, parent_folder_id FROM tb_note_folder WHERE folder_idx = :folderId";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("folderId", folderId);

            Map<String, Object> folder = mysqlTemplate.queryForMap(sql, params);
            String folderName = (String) folder.get("folder_name");

            Object parentIdObj = folder.get("parent_folder_id");
            Long parentFolderId = null;
            if (parentIdObj != null) {
                if (parentIdObj instanceof Long) {
                    parentFolderId = (Long) parentIdObj;
                } else if (parentIdObj instanceof Number) {
                    parentFolderId = ((Number) parentIdObj).longValue();
                }
            }

            if (parentFolderId == null) {
                return "/" + folderName;
            } else {
                String parentPath = getFolderPath(parentFolderId);
                return parentPath + "/" + folderName;
            }

        } catch (Exception e) {
            log.warn("⚠️ 폴더 경로 조회 실패 (folderId={}): {}", folderId, e.getMessage());
            return "/";
        }
    }

    private String[] getNoteTags(Note note) {
        if (note.getNoteTags() == null || note.getNoteTags().isEmpty()) {
            return new String[0];
        }

        try {
            return note.getNoteTags().stream()
                    .map(noteTag -> noteTag.getTag().getName())
                    .toArray(String[]::new);
        } catch (Exception e) {
            log.warn("⚠️ 태그 추출 실패 (noteIdx={}): {}", note.getNoteIdx(), e.getMessage());
            return new String[0];
        }
    }

    private List<Double> generateEmbedding(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return Collections.nCopies(1024, 0.0);
            }

            Map<String, Object> response = restTemplate.postForObject(
                    "http://ssaegim.tplinkdns.com:8081/embed",
                    Map.of("texts", List.of(content.trim())),
                    Map.class
            );

            if (response == null || !response.containsKey("embeddings")) {
                throw new RuntimeException("임베딩 응답 null");
            }

            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            return embeddings.get(0);

        } catch (Exception e) {
            log.error("❌ 임베딩 생성 실패: {}", e.getMessage());
            return Collections.nCopies(1024, 0.0);
        }
    }

    private String formatVector(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(d -> String.format("%.15f", d))
                .collect(Collectors.joining(",")) + "]";
    }
}
