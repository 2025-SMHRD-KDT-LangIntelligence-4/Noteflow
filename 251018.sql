-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------
-- -----------------------------------------------------
-- Schema sc_25K_LI4_p3_2
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Schema sc_25K_LI4_p3_2
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `sc_25K_LI4_p3_2` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci ;
USE `sc_25K_LI4_p3_2` ;

-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`SSAEGIM`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`SSAEGIM` (
  `id` INT NOT NULL,
  `pw` VARCHAR(50) NULL DEFAULT NULL,
  `email` VARCHAR(50) NULL DEFAULT NULL,
  `name` VARCHAR(50) NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`users`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`users` (
  `user_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 고유 식별자',
  `user_id` VARCHAR(255) NOT NULL COMMENT '로그인용 아이디 (중복 불가)',
  `user_pw` VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
  `user_role` ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT '사용자 권한 (USER/ADMIN)',
  `email` VARCHAR(255) NOT NULL COMMENT '사용자 이메일',
  `last_login` TIMESTAMP NULL DEFAULT NULL COMMENT '마지막 접속 일시',
  `mailing_agreed` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '메일링 동의 여부',
  `nickname` VARCHAR(50) NULL DEFAULT NULL COMMENT '사용자 닉네임',
  `profile_image` VARCHAR(255) NULL DEFAULT NULL COMMENT '프로필 이미지 경로',
  `bio` TEXT NULL DEFAULT NULL COMMENT '자기소개',
  `interest_area` VARCHAR(500) NULL DEFAULT NULL COMMENT '관심 학습 분야',
  `learning_area` VARCHAR(500) NULL DEFAULT NULL COMMENT '현재 학습 영역',
  `is_suspended` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '사용자 정지 여부',
  `suspend_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '정지 사유',
  `suspend_start_date` TIMESTAMP NULL DEFAULT NULL COMMENT '정지 시작일',
  `suspend_end_date` TIMESTAMP NULL DEFAULT NULL COMMENT '정지 종료일',
  `warning_count` INT NOT NULL DEFAULT '0' COMMENT '경고 횟수',
  `attachment_count` INT NOT NULL DEFAULT '0' COMMENT '첨부파일 개수',
  `login_count` INT NOT NULL DEFAULT '0' COMMENT '총 로그인 횟수',
  `note_count` INT NOT NULL DEFAULT '0' COMMENT '작성한 노트 수',
  `test_count` INT NOT NULL DEFAULT '0' COMMENT '응시한 시험 수',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '계정 생성 일시',
  PRIMARY KEY (`user_idx`),
  UNIQUE INDEX `uq_user_id` (`user_id` ASC) VISIBLE,
  UNIQUE INDEX `uq_email` (`email` ASC) VISIBLE,
  INDEX `idx_users_created_at` (`created_at` ASC) VISIBLE,
  INDEX `idx_users_role` (`user_role` ASC) VISIBLE)
ENGINE = InnoDB
AUTO_INCREMENT = 13
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`admin_logs`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`admin_logs` (
  `log_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '로그 고유 ID',
  `admin_idx` BIGINT NOT NULL COMMENT '관리자 사용자 ID',
  `action_type` VARCHAR(50) NOT NULL COMMENT '수행 작업 유형',
  `target_type` VARCHAR(20) NOT NULL COMMENT '대상 유형',
  `target_id` BIGINT NOT NULL COMMENT '대상 ID',
  `detail` TEXT NULL DEFAULT NULL COMMENT '작업 세부 정보',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '이력 생성 일시',
  PRIMARY KEY (`log_id`),
  INDEX `idx_al_admin` (`admin_idx` ASC) VISIBLE,
  CONSTRAINT `fk_al_admin`
    FOREIGN KEY (`admin_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`note_folders`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`note_folders` (
  `folder_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '폴더 ID',
  `user_idx` BIGINT NOT NULL,
  `parent_folder_id` BIGINT NULL DEFAULT NULL COMMENT '상위 폴더 ID',
  `folder_name` VARCHAR(255) NOT NULL COMMENT '폴더 이름',
  `sort_order` INT NOT NULL DEFAULT '0' COMMENT '정렬 순서',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  PRIMARY KEY (`folder_id`),
  UNIQUE INDEX `uq_note_folder_user_parent_name` (`user_idx` ASC, `parent_folder_id` ASC, `folder_name` ASC) VISIBLE,
  INDEX `idx_nf_parent` (`parent_folder_id` ASC) VISIBLE,
  CONSTRAINT `fk_nf_parent`
    FOREIGN KEY (`parent_folder_id`)
    REFERENCES `sc_25K_LI4_p3_2`.`note_folders` (`folder_id`)
    ON DELETE SET NULL)
ENGINE = InnoDB
AUTO_INCREMENT = 73
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`notes`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`notes` (
  `note_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '노트 고유 식별자',
  `user_idx` BIGINT NOT NULL COMMENT '작성자 식별자',
  `folder_id` BIGINT NULL DEFAULT NULL COMMENT '폴더 ID',
  `source_id` VARCHAR(100) NULL DEFAULT NULL COMMENT 'GridFS 원본 파일 ID',
  `title` VARCHAR(255) NOT NULL COMMENT '노트 제목',
  `content` TEXT NOT NULL COMMENT '노트 본문',
  `is_public` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '공개 여부',
  `view_count` INT NOT NULL DEFAULT '0' COMMENT '조회수',
  `like_count` INT NOT NULL DEFAULT '0' COMMENT '좋아요 수',
  `comment_count` INT NOT NULL DEFAULT '0' COMMENT '댓글 수',
  `report_count` INT NOT NULL DEFAULT '0' COMMENT '신고 횟수',
  `status` ENUM('ACTIVE', 'BLOCKED', 'DELETED') NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
  `blocked_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '차단 사유',
  `blocked_at` TIMESTAMP NULL DEFAULT NULL COMMENT '차단 일시',
  `ai_prompt_id` VARCHAR(100) NULL DEFAULT NULL COMMENT '사용한 프롬프트 식별자',
  `ai_generation_time_ms` INT NULL DEFAULT NULL COMMENT 'AI 응답 생성 소요 시간(밀리초)',
  `ai_satisfaction_score` TINYINT NULL DEFAULT NULL COMMENT 'AI 만족도(1-5점)',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '노트 작성 일시',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '노트 수정 일시',
  PRIMARY KEY (`note_idx`),
  INDEX `idx_notes_user` (`user_idx` ASC) VISIBLE,
  INDEX `idx_notes_user_created` (`user_idx` ASC, `created_at` ASC) VISIBLE,
  INDEX `idx_notes_status_public_created` (`status` ASC, `is_public` ASC, `created_at` ASC) VISIBLE,
  INDEX `idx_notes_ai_prompt` (`ai_prompt_id` ASC) VISIBLE,
  INDEX `idx_notes_folder` (`folder_id` ASC) VISIBLE,
  INDEX `idx_notes_source` (`source_id` ASC) VISIBLE,
  INDEX `idx_notes_user_folder` (`user_idx` ASC, `folder_id` ASC) VISIBLE,
  CONSTRAINT `fk_notes_folder`
    FOREIGN KEY (`folder_id`)
    REFERENCES `sc_25K_LI4_p3_2`.`note_folders` (`folder_id`)
    ON DELETE SET NULL,
  CONSTRAINT `fk_notes_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  CONSTRAINT `notes_ibfk_1`
    FOREIGN KEY (`folder_id`)
    REFERENCES `sc_25K_LI4_p3_2`.`note_folders` (`folder_id`)
    ON DELETE SET NULL)
ENGINE = InnoDB
AUTO_INCREMENT = 49
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`attachments`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`attachments` (
  `attachment_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '첨부파일 ID',
  `note_idx` BIGINT NOT NULL COMMENT '노트 ID',
  `original_filename` VARCHAR(255) NOT NULL COMMENT '원본 파일명',
  `stored_filename` VARCHAR(255) NOT NULL COMMENT '저장 파일명',
  `file_extension` VARCHAR(10) NOT NULL COMMENT '파일 확장자',
  `file_size` BIGINT NOT NULL COMMENT '파일 크기(바이트)',
  `mime_type` VARCHAR(100) NOT NULL COMMENT 'MIME 타입',
  `mongo_doc_id` VARCHAR(50) NOT NULL COMMENT 'MongoDB 문서 ID',
  `upload_path` VARCHAR(500) NULL DEFAULT NULL COMMENT '저장 경로',
  `download_count` INT NOT NULL DEFAULT '0' COMMENT '다운로드 횟수',
  `status` ENUM('ACTIVE', 'DELETED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
  `expires_at` TIMESTAMP NULL DEFAULT NULL COMMENT '만료일',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업로드 일시',
  `deleted_at` TIMESTAMP NULL DEFAULT NULL COMMENT '삭제 일시',
  PRIMARY KEY (`attachment_idx`),
  UNIQUE INDEX `uq_att_mongo` (`mongo_doc_id` ASC) VISIBLE,
  INDEX `idx_att_note` (`note_idx` ASC) VISIBLE,
  INDEX `idx_att_ex` (`expires_at` ASC, `status` ASC) VISIBLE,
  INDEX `idx_att_created` (`created_at` ASC) VISIBLE,
  CONSTRAINT `fk_att_note`
    FOREIGN KEY (`note_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`notes` (`note_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 5
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`bookmarks`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`bookmarks` (
  `bookmark_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '즐겨찾기 ID',
  `user_idx` BIGINT NOT NULL COMMENT '사용자 ID',
  `target_type` VARCHAR(20) NOT NULL COMMENT '대상 유형',
  `target_id` BIGINT NOT NULL COMMENT '대상 ID',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  PRIMARY KEY (`bookmark_id`),
  UNIQUE INDEX `uq_bm` (`user_idx` ASC, `target_type` ASC, `target_id` ASC) VISIBLE,
  INDEX `idx_bm_user` (`user_idx` ASC) VISIBLE,
  CONSTRAINT `fk_bm_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`category_hierarchy`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`category_hierarchy` (
  `category_id` BIGINT NOT NULL AUTO_INCREMENT,
  `large_category` VARCHAR(50) NOT NULL,
  `medium_category` VARCHAR(100) NOT NULL,
  `small_category` VARCHAR(150) NOT NULL,
  `example_tag` VARCHAR(200) NOT NULL,
  `keywords` TEXT NULL DEFAULT NULL,
  `created_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  `confidence_score` DOUBLE NULL DEFAULT '0',
  PRIMARY KEY (`category_id`),
  UNIQUE INDEX `uk_category_hierarchy` (`large_category` ASC, `medium_category` ASC, `small_category` ASC) VISIBLE,
  INDEX `idx_keywords` (`keywords`(255) ASC) VISIBLE,
  INDEX `idx_large_category` (`large_category` ASC) VISIBLE,
  FULLTEXT INDEX `idx_keywords_fulltext` (`keywords`) VISIBLE)
ENGINE = InnoDB
AUTO_INCREMENT = 261
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`chats`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`chats` (
  `chat_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '대화 고유 식별자',
  `user_idx` BIGINT NOT NULL COMMENT '사용자 식별자',
  `session_id` VARCHAR(100) NULL DEFAULT NULL COMMENT '대화 세션 ID',
  `question` TEXT NOT NULL COMMENT '사용자 질문',
  `answer` TEXT NOT NULL COMMENT 'AI 응답',
  `response_time_ms` INT NULL DEFAULT NULL COMMENT '응답 시간(밀리초)',
  `rating` TINYINT NULL DEFAULT NULL COMMENT '사용자 평가(1-5)',
  `feedback` TEXT NULL DEFAULT NULL COMMENT '사용자 피드백',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '대화 발생 일시',
  PRIMARY KEY (`chat_idx`),
  INDEX `idx_chats_user` (`user_idx` ASC) VISIBLE,
  INDEX `idx_chats_session` (`session_id` ASC) VISIBLE,
  INDEX `idx_chats_created` (`created_at` ASC) VISIBLE,
  CONSTRAINT `fk_chats_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`documents`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`documents` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `content` TEXT NULL DEFAULT NULL,
  `file_name` VARCHAR(255) NULL DEFAULT NULL,
  `file_path` VARCHAR(255) NULL DEFAULT NULL,
  `upload_time` DATETIME(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`download_logs`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`download_logs` (
  `log_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '로그 ID',
  `attachment_idx` BIGINT NOT NULL COMMENT '첨부파일 ID',
  `user_idx` BIGINT NOT NULL COMMENT '사용자 ID',
  `download_ip` VARCHAR(45) NULL DEFAULT NULL COMMENT '다운로드 IP',
  `user_agent` VARCHAR(500) NULL DEFAULT NULL COMMENT '브라우저 정보',
  `downloaded_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '다운로드 일시',
  PRIMARY KEY (`log_idx`),
  INDEX `idx_dl_att` (`attachment_idx` ASC) VISIBLE,
  INDEX `idx_dl_user` (`user_idx` ASC) VISIBLE,
  INDEX `idx_dl_att_time` (`attachment_idx` ASC, `downloaded_at` ASC) VISIBLE,
  INDEX `idx_dl_user_time` (`user_idx` ASC, `downloaded_at` ASC) VISIBLE,
  CONSTRAINT `fk_dl_att`
    FOREIGN KEY (`attachment_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`attachments` (`attachment_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_dl_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`file_folder`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`file_folder` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(255) NOT NULL,
  `mongodb_folder_id` VARCHAR(45) NULL DEFAULT NULL,
  `folder_name` VARCHAR(255) NOT NULL,
  `parent_id` BIGINT NULL DEFAULT NULL,
  `parent_mongodb_id` VARCHAR(45) NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `fk_file_folder_parent` (`parent_id` ASC) VISIBLE,
  INDEX `idx_file_folder_user_parent` (`user_id` ASC, `parent_id` ASC) VISIBLE,
  CONSTRAINT `fk_file_folder_parent`
    FOREIGN KEY (`parent_id`)
    REFERENCES `sc_25K_LI4_p3_2`.`file_folder` (`id`)
    ON DELETE SET NULL
    ON UPDATE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 3
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`learning_statistics`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`learning_statistics` (
  `stat_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '통계 ID',
  `user_idx` BIGINT NOT NULL COMMENT '사용자 ID',
  `stat_date` DATE NOT NULL COMMENT '통계 날짜',
  `notes_created` INT NOT NULL DEFAULT '0' COMMENT '노트 작성 수',
  `tests_taken` INT NOT NULL DEFAULT '0' COMMENT '응시 시험 수',
  `study_minutes` INT NOT NULL DEFAULT '0' COMMENT '학습 시간(분)',
  `ai_questions` INT NOT NULL DEFAULT '0' COMMENT 'AI 질문 수',
  PRIMARY KEY (`stat_id`),
  UNIQUE INDEX `uq_ls_user_date` (`user_idx` ASC, `stat_date` ASC) VISIBLE,
  INDEX `idx_ls_user` (`user_idx` ASC) VISIBLE,
  CONSTRAINT `fk_ls_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`lecture`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`lecture` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `category` VARCHAR(255) NULL DEFAULT NULL,
  `title` VARCHAR(255) NOT NULL,
  `url` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`lectures`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`lectures` (
  `lec_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '강의 고유 식별자',
  `lec_title` VARCHAR(255) NOT NULL COMMENT '강의 제목',
  `lec_url` VARCHAR(500) NOT NULL COMMENT '강의 링크 URL',
  `category_large` VARCHAR(100) NOT NULL COMMENT '대분류 카테고리',
  `category_medium` VARCHAR(100) NOT NULL COMMENT '중분류 카테고리',
  `category_small` VARCHAR(100) NOT NULL COMMENT '소분류 카테고리',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '강의 등록 일시',
  PRIMARY KEY (`lec_idx`),
  INDEX `idx_lectures_cat` (`category_large` ASC, `category_medium` ASC, `category_small` ASC) VISIBLE,
  INDEX `idx_lectures_created` (`created_at` ASC) VISIBLE)
ENGINE = InnoDB
AUTO_INCREMENT = 15
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`note_comments`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`note_comments` (
  `comment_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '댓글 고유 ID',
  `note_idx` BIGINT NOT NULL COMMENT '대상 노트 ID',
  `user_idx` BIGINT NOT NULL COMMENT '댓글 작성자 ID',
  `content` TEXT NOT NULL COMMENT '댓글 내용',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '댓글 작성 일시',
  PRIMARY KEY (`comment_id`),
  INDEX `idx_nc_note` (`note_idx` ASC) VISIBLE,
  INDEX `idx_nc_user` (`user_idx` ASC) VISIBLE,
  INDEX `idx_nc_created` (`created_at` ASC) VISIBLE,
  CONSTRAINT `fk_nc_note`
    FOREIGN KEY (`note_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`notes` (`note_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_nc_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`note_likes`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`note_likes` (
  `like_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '좋아요 ID',
  `note_idx` BIGINT NOT NULL COMMENT '노트 ID',
  `user_idx` BIGINT NOT NULL COMMENT '사용자 ID',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '좋아요 일시',
  PRIMARY KEY (`like_id`),
  UNIQUE INDEX `uq_nl` (`note_idx` ASC, `user_idx` ASC) VISIBLE,
  INDEX `idx_nl_user` (`user_idx` ASC) VISIBLE,
  CONSTRAINT `fk_nl_note`
    FOREIGN KEY (`note_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`notes` (`note_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_nl_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`note_reports`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`note_reports` (
  `report_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '신고 ID',
  `note_idx` BIGINT NOT NULL COMMENT '신고된 노트 ID',
  `reporter_id` BIGINT NOT NULL COMMENT '신고자 ID',
  `reported_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신고 일시',
  PRIMARY KEY (`report_id`),
  INDEX `idx_nr_note` (`note_idx` ASC) VISIBLE,
  INDEX `idx_nr_reporter` (`reporter_id` ASC) VISIBLE,
  CONSTRAINT `fk_nr_note`
    FOREIGN KEY (`note_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`notes` (`note_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_nr_user`
    FOREIGN KEY (`reporter_id`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`tags`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`tags` (
  `tag_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '태그 고유 식별자',
  `name` VARCHAR(100) NOT NULL COMMENT '태그 이름',
  `usage_count` INT NOT NULL DEFAULT '0' COMMENT '사용 횟수',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '태그 생성 일시',
  PRIMARY KEY (`tag_idx`),
  UNIQUE INDEX `uq_tags_name` (`name` ASC) VISIBLE,
  INDEX `idx_tags_usage` (`usage_count` ASC) VISIBLE)
ENGINE = InnoDB
AUTO_INCREMENT = 360
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`note_tags`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`note_tags` (
  `note_tag_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '관계 고유 식별자',
  `note_idx` BIGINT NOT NULL COMMENT '노트 식별자',
  `tag_idx` BIGINT NOT NULL COMMENT '태그 식별자',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '관계 생성 일시',
  PRIMARY KEY (`note_tag_idx`),
  UNIQUE INDEX `uq_nt` (`note_idx` ASC, `tag_idx` ASC) VISIBLE,
  INDEX `idx_nt_note` (`note_idx` ASC) VISIBLE,
  INDEX `idx_nt_tag` (`tag_idx` ASC) VISIBLE,
  CONSTRAINT `fk_nt_note`
    FOREIGN KEY (`note_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`notes` (`note_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  CONSTRAINT `fk_nt_tag`
    FOREIGN KEY (`tag_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`tags` (`tag_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`note_views`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`note_views` (
  `view_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '조회 기록 ID',
  `note_idx` BIGINT NOT NULL COMMENT '조회된 노트 ID',
  `viewer_id` BIGINT NOT NULL COMMENT '조회자 사용자 ID',
  `viewed_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '조회 일시',
  PRIMARY KEY (`view_id`),
  INDEX `idx_fv_note` (`note_idx` ASC) VISIBLE,
  INDEX `idx_fv_viewer` (`viewer_id` ASC) VISIBLE,
  INDEX `idx_fv_viewed_at` (`viewed_at` ASC) VISIBLE,
  CONSTRAINT `fk_fv_note`
    FOREIGN KEY (`note_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`notes` (`note_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_fv_user`
    FOREIGN KEY (`viewer_id`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`problem`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`problem` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `answer` TEXT NULL DEFAULT NULL,
  `category` VARCHAR(255) NULL DEFAULT NULL,
  `question` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`prompts`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`prompts` (
  `prompt_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '프롬프트 고유 ID',
  `title` VARCHAR(255) NOT NULL COMMENT '프롬프트 제목',
  `content` TEXT NOT NULL COMMENT '프롬프트 내용',
  `example_output` TEXT NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  PRIMARY KEY (`prompt_id`),
  INDEX `idx_prompts_created` (`created_at` ASC) VISIBLE)
ENGINE = InnoDB
AUTO_INCREMENT = 17
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`provided_infos`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`provided_infos` (
  `info_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '정보 ID',
  `exam_name` VARCHAR(255) NOT NULL COMMENT '시험 이름',
  `exam_date` DATE NOT NULL COMMENT '시험 날짜',
  `registration_start` DATE NULL DEFAULT NULL COMMENT '접수 시작일',
  `registration_end` DATE NULL DEFAULT NULL COMMENT '접수 종료일',
  `category_large` VARCHAR(100) NOT NULL COMMENT '대분류 카테고리',
  `category_medium` VARCHAR(100) NOT NULL COMMENT '중분류 카테고리',
  `category_small` VARCHAR(100) NOT NULL COMMENT '소분류 카테고리',
  `organizer` VARCHAR(200) NULL DEFAULT NULL COMMENT '주관 기관',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '정보 등록 일시',
  PRIMARY KEY (`info_idx`),
  INDEX `idx_pi_date` (`exam_date` ASC) VISIBLE,
  INDEX `idx_pi_cat` (`category_large` ASC, `category_medium` ASC) VISIBLE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`schedule`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`schedule` (
  `schedule_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_idx` BIGINT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NULL DEFAULT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `color_tag` VARCHAR(20) NULL DEFAULT '#3788d8',
  `emoji` VARCHAR(50) NULL DEFAULT NULL,
  `alarm_time` DATETIME NULL DEFAULT NULL,
  `alert_type` VARCHAR(100) NULL DEFAULT NULL,
  `custom_alert_value` INT NULL DEFAULT NULL,
  `custom_alert_unit` ENUM('minute', 'hour', 'day', 'week') NULL DEFAULT NULL,
  `location` VARCHAR(255) NULL DEFAULT NULL,
  `map_lat` DECIMAL(10,7) NULL DEFAULT NULL,
  `map_lng` DECIMAL(10,7) NULL DEFAULT NULL,
  `highlight_type` ENUM('none', 'star', 'highlight', 'emoji') NULL DEFAULT 'none',
  `category` VARCHAR(100) NULL DEFAULT NULL,
  `attachment_path` VARCHAR(255) NULL DEFAULT NULL,
  `attachment_list` JSON NULL DEFAULT NULL,
  `is_all_day` TINYINT(1) NULL DEFAULT '0',
  `is_deleted` TINYINT(1) NULL DEFAULT '0',
  `created_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`schedule_id`),
  INDEX `fk_schedule_user` (`user_idx` ASC) VISIBLE,
  CONSTRAINT `fk_schedule_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
AUTO_INCREMENT = 11
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`system_notices`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`system_notices` (
  `notice_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '공지 ID',
  `title` VARCHAR(255) NOT NULL COMMENT '제목',
  `content` TEXT NOT NULL COMMENT '내용',
  `notice_type` ENUM('GENERAL', 'EVENT', 'MAINTENANCE') NOT NULL DEFAULT 'GENERAL' COMMENT '공지 유형',
  `is_active` TINYINT(1) NOT NULL DEFAULT '1' COMMENT '활성 여부',
  `start_date` TIMESTAMP NULL DEFAULT NULL COMMENT '시작일',
  `end_date` TIMESTAMP NULL DEFAULT NULL COMMENT '종료일',
  `created_by` BIGINT NULL DEFAULT NULL COMMENT '작성자 ID',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 일시',
  PRIMARY KEY (`notice_id`),
  INDEX `idx_sn_active` (`is_active` ASC) VISIBLE,
  INDEX `idx_sn_dates` (`start_date` ASC, `end_date` ASC) VISIBLE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`test_sources`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`test_sources` (
  `test_source_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '문제 소스 ID',
  `question` TEXT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NULL DEFAULT NULL,
  `answer` TEXT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NULL DEFAULT NULL,
  `explanation` TEXT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NULL DEFAULT NULL,
  `difficulty` VARCHAR(10) NULL DEFAULT NULL,
  `category_large` VARCHAR(100) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NULL DEFAULT NULL,
  `category_medium` VARCHAR(100) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NULL DEFAULT NULL,
  `category_small` VARCHAR(100) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NULL DEFAULT NULL,
  `question_type` ENUM('MULTIPLE_CHOICE', 'FILL_BLANK', 'CONCEPT', 'SUBJECTIVE') NULL DEFAULT NULL,
  `options` TEXT NULL DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 일시',
  PRIMARY KEY (`test_source_idx`),
  INDEX `idx_ts_cat` (`category_large` ASC, `category_medium` ASC, `category_small` ASC) VISIBLE,
  INDEX `idx_ts_diff` (`difficulty` ASC) VISIBLE)
ENGINE = InnoDB
AUTO_INCREMENT = 8495
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`tests`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`tests` (
  `test_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '시험 ID',
  `test_title` VARCHAR(255) NOT NULL COMMENT '시험 제목',
  `test_desc` TEXT NULL DEFAULT NULL COMMENT '시험 설명',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  PRIMARY KEY (`test_idx`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`test_items`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`test_items` (
  `item_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '문항 ID',
  `test_idx` BIGINT NOT NULL COMMENT '시험 ID',
  `test_source_idx` BIGINT NOT NULL COMMENT '문제 소스 ID',
  `sequence` INT NOT NULL COMMENT '문항 순서',
  `score` INT NOT NULL DEFAULT '1' COMMENT '배점',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '구성 일시',
  PRIMARY KEY (`item_idx`),
  UNIQUE INDEX `uq_ti` (`test_idx` ASC, `test_source_idx` ASC) VISIBLE,
  INDEX `idx_ti_test` (`test_idx` ASC) VISIBLE,
  INDEX `idx_ti_source` (`test_source_idx` ASC) VISIBLE,
  CONSTRAINT `fk_ti_source`
    FOREIGN KEY (`test_source_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`test_sources` (`test_source_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  CONSTRAINT `fk_ti_test`
    FOREIGN KEY (`test_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`tests` (`test_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`test_results`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`test_results` (
  `result_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '결과 ID',
  `user_idx` BIGINT NOT NULL COMMENT '사용자 ID',
  `test_idx` BIGINT NOT NULL COMMENT '시험 ID',
  `total_score` INT NOT NULL COMMENT '총점',
  `user_score` INT NOT NULL COMMENT '취득 점수',
  `correct_count` INT NOT NULL COMMENT '정답 수',
  `wrong_count` INT NOT NULL COMMENT '오답 수',
  `test_duration` INT NULL DEFAULT NULL COMMENT '소요 시간(분)',
  `passed` TINYINT(1) NOT NULL COMMENT '합격 여부',
  `start_time` TIMESTAMP NOT NULL COMMENT '시작 시간',
  `end_time` TIMESTAMP NOT NULL COMMENT '종료 시간',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '저장 일시',
  PRIMARY KEY (`result_idx`),
  INDEX `idx_tr_user` (`user_idx` ASC) VISIBLE,
  INDEX `idx_tr_test` (`test_idx` ASC) VISIBLE,
  INDEX `idx_tr_created` (`created_at` ASC) VISIBLE,
  CONSTRAINT `fk_tr_test`
    FOREIGN KEY (`test_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`tests` (`test_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE,
  CONSTRAINT `fk_tr_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`test_summaries`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`test_summaries` (
  `test_id` BIGINT NOT NULL AUTO_INCREMENT,
  `test_type` VARCHAR(10) NOT NULL,
  `prompt_title` VARCHAR(255) NULL DEFAULT NULL,
  `original_content` LONGTEXT NULL DEFAULT NULL,
  `ai_summary` LONGTEXT NULL DEFAULT NULL,
  `file_name` VARCHAR(255) NULL DEFAULT NULL,
  `file_size` BIGINT NULL DEFAULT NULL,
  `processing_time_ms` BIGINT NULL DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL,
  `error_message` TEXT NULL DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `original_prompt` TEXT NULL DEFAULT NULL,
  PRIMARY KEY (`test_id`))
ENGINE = InnoDB
AUTO_INCREMENT = 182
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`user_answers`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`user_answers` (
  `answer_idx` BIGINT NOT NULL AUTO_INCREMENT COMMENT '답안 ID',
  `result_idx` BIGINT NOT NULL COMMENT '결과 ID',
  `test_source_idx` BIGINT NOT NULL COMMENT '문제 소스 ID',
  `user_answer` VARCHAR(500) NULL DEFAULT NULL COMMENT '사용자 답안',
  `is_correct` TINYINT(1) NOT NULL COMMENT '정답 여부',
  `response_time` INT NULL DEFAULT NULL COMMENT '응답 시간(초)',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '제출 일시',
  PRIMARY KEY (`answer_idx`),
  INDEX `idx_ua_result` (`result_idx` ASC) VISIBLE,
  INDEX `idx_ua_source` (`test_source_idx` ASC) VISIBLE,
  CONSTRAINT `fk_ua_result`
    FOREIGN KEY (`result_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`test_results` (`result_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_ua_source`
    FOREIGN KEY (`test_source_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`test_sources` (`test_source_idx`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`user_favorite_prompts`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`user_favorite_prompts` (
  `fav_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '즐겨찾기 고유 ID',
  `user_idx` BIGINT NOT NULL COMMENT '사용자 ID',
  `prompt_id` BIGINT NOT NULL COMMENT '프롬프트 ID',
  `favorited_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '즐겨찾기 일시',
  PRIMARY KEY (`fav_id`),
  UNIQUE INDEX `uq_user_prompt` (`user_idx` ASC, `prompt_id` ASC) VISIBLE,
  INDEX `idx_ufp_user` (`user_idx` ASC) VISIBLE,
  INDEX `idx_ufp_prompt` (`prompt_id` ASC) VISIBLE,
  CONSTRAINT `fk_ufp_prompt`
    FOREIGN KEY (`prompt_id`)
    REFERENCES `sc_25K_LI4_p3_2`.`prompts` (`prompt_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_ufp_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


-- -----------------------------------------------------
-- Table `sc_25K_LI4_p3_2`.`user_notifications`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `sc_25K_LI4_p3_2`.`user_notifications` (
  `notification_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '알림 설정 고유 식별자',
  `user_idx` BIGINT NOT NULL COMMENT '사용자 식별자',
  `notification_type` VARCHAR(50) NOT NULL COMMENT '알림 유형 (ALERT, REMINDER 등)',
  `target_type` VARCHAR(20) NOT NULL COMMENT '대상 유형 (NOTE, LECTURE, TEST 등)',
  `target_id` BIGINT NOT NULL COMMENT '대상 ID',
  `is_enabled` TINYINT(1) NOT NULL DEFAULT '1' COMMENT '알림 활성 여부',
  `settings` JSON NULL DEFAULT NULL COMMENT '추가 알림 설정 정보',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '설정 생성 일시',
  PRIMARY KEY (`notification_id`),
  INDEX `idx_un_user` (`user_idx` ASC) VISIBLE,
  CONSTRAINT `fk_un_user`
    FOREIGN KEY (`user_idx`)
    REFERENCES `sc_25K_LI4_p3_2`.`users` (`user_idx`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_0900_ai_ci;


SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
