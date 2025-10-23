package com.smhrd.web.service;

import com.smhrd.web.dto.ScheduleRequestDto;
import com.smhrd.web.entity.TempSchedule;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.TempScheduleRepository;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class TempScheduleService {

    private final TempScheduleRepository tempRepo;
    private final UserRepository userRepo;
    public TempSchedule updateDraft(Long tempId, Long userIdx, ScheduleRequestDto dto) {
        TempSchedule t = tempRepo.findById(tempId)
                .orElseThrow(() -> new IllegalArgumentException("임시 일정이 존재하지 않습니다."));
        if (!t.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        t.setTitle(Optional.ofNullable(dto.getTitle()).orElse("(제목 없음)"));
        t.setDescription(dto.getDescription());
        t.setStartTime(dto.getStartTime());
        t.setEndTime(dto.getEndTime());
        t.setColorTag(dto.getColorTag());
        t.setEmoji(dto.getEmoji());
        t.setAlarmTime(dto.getAlarmTime());
        t.setAlertType(dto.getAlertType());
        t.setCustomAlertValue(dto.getCustomAlertValue());
        t.setCustomAlertUnit(dto.getCustomAlertUnit());
        t.setLocation(dto.getLocation());
        t.setMapLat(dto.getMapLat());
        t.setMapLng(dto.getMapLng());
        t.setHighlightType(dto.getHighlightType());
        t.setCategory(dto.getCategory());
        t.setAttachmentPath(dto.getAttachmentPath());
        t.setAttachmentList(dto.getAttachmentList());
        t.setIsAllDay(Boolean.TRUE.equals(dto.getIsAllDay()));
        t.setUpdatedAt(LocalDateTime.now());
        return tempRepo.save(t);
    }
    public TempSchedule saveDraft(Long userIdx, ScheduleRequestDto dto) {
        User user = userRepo.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        TempSchedule t = TempSchedule.builder()
                .user(user)
                .title(Optional.ofNullable(dto.getTitle()).orElse("(제목 없음)"))
                .description(dto.getDescription())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .colorTag(dto.getColorTag())
                .emoji(dto.getEmoji())
                .alarmTime(dto.getAlarmTime())
                .alertType(dto.getAlertType())
                .customAlertValue(dto.getCustomAlertValue())
                .customAlertUnit(dto.getCustomAlertUnit())
                .location(dto.getLocation())
                .mapLat(dto.getMapLat())
                .mapLng(dto.getMapLng())
                .highlightType(dto.getHighlightType())
                .category(dto.getCategory())
                .attachmentPath(dto.getAttachmentPath())
                .attachmentList(dto.getAttachmentList())
                .isAllDay(Boolean.TRUE.equals(dto.getIsAllDay()))
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return tempRepo.save(t);
    }

    public List<TempSchedule> listDrafts(Long userIdx) {
        return tempRepo.findByUser_UserIdxAndIsDeletedFalseOrderByUpdatedAtDesc(userIdx);
    }

    public Optional<TempSchedule> getDraft(Long tempId, Long userIdx) {
        return tempRepo.findById(tempId)
                .filter(t -> t.getUser().getUserIdx().equals(userIdx))
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()));
    }

    public void deleteDraft(Long tempId, Long userIdx) {
        TempSchedule t = tempRepo.findById(tempId)
                .orElseThrow(() -> new IllegalArgumentException("임시 일정이 존재하지 않습니다."));
        if (!t.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        t.setIsDeleted(true);
        t.setUpdatedAt(LocalDateTime.now());
        tempRepo.save(t);
    }

    public Map<LocalDate, Long> countByDate(Long userIdx, LocalDateTime from, LocalDateTime to) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : tempRepo.countGroupByDate(userIdx, from, to)) {
            // row[0] = java.sql.Date or LocalDate; row[1] = Long
            LocalDate d = (row[0] instanceof java.sql.Date)
                    ? ((java.sql.Date) row[0]).toLocalDate()
                    : (LocalDate) row[0];
            Long cnt = (row[1] instanceof Long) ? (Long) row[1] : ((Number) row[1]).longValue();
            map.put(d, cnt);
        }
        return map;
    }
}
