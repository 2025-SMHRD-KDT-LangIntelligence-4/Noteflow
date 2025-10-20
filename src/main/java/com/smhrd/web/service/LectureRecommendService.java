package com.smhrd.web.service;

import com.smhrd.web.dto.LectureDto;
import com.smhrd.web.entity.Lecture;
import com.smhrd.web.repository.LectureSearchRepository;
import com.smhrd.web.repository.LectureTagRepository;
import com.smhrd.web.repository.NoteTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LectureRecommendService {

    private final LectureSearchRepository lectureSearchRepository;
    private final NoteTagRepository noteTagRepository;
    private final LectureTagRepository lectureTagRepository;

    public List<LectureDto> recommendFromNoteTags(Long noteId, int limit) {
        // 1) 노트 태그 로드(소문자/중복 제거)
        List<String> noteTagsLower = noteTagRepository.findTagNamesByNoteIdx(noteId).stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        if (noteTagsLower.isEmpty()) return List.of();

        // 2) Pageable로 LIMIT 적용
        var page = PageRequest.of(0, Math.max(10, limit));

        // 정확 일치 → 부족하면 부분 일치 보강
        List<Lecture> exact = lectureSearchRepository.findByTagNamesExact(noteTagsLower, page);

        LinkedHashSet<Lecture> bag = new LinkedHashSet<>(exact);
        if (bag.size() < limit) {
            for (String kw : noteTagsLower) {
                if (bag.size() >= limit) break;
                bag.addAll(lectureSearchRepository.findByTagNameLikePage(kw, page));
            }
        }

        List<Lecture> hits = bag.stream().limit(limit).toList();
        if (hits.isEmpty()) return List.of();

        // 3) 강의 태그 일괄 로드 → Map<lecIdx, Set<tagLower>>
        List<Long> ids = hits.stream().map(Lecture::getLecIdx).toList();
        Map<Long, Set<String>> lectureTagMap = lectureTagRepository.findTagNamesByLectureIds(ids).stream()
                .collect(Collectors.groupingBy(
                        LectureTagRepository.LectureIdTagName::getLecIdx,
                        Collectors.mapping(LectureTagRepository.LectureIdTagName::getTagName, Collectors.toSet())
                ));

        // 4) 적중률 계산 + DTO 변환
        List<LectureDto> out = new ArrayList<>();
        for (Lecture l : hits) {
            Set<String> lecTags = lectureTagMap.getOrDefault(l.getLecIdx(), Set.of());
            Set<String> matched = new LinkedHashSet<>(lecTags);
            matched.retainAll(noteTagsLower);

            int total = lecTags.size();
            int matchedCount = matched.size();
            double rate = (total == 0) ? 0.0 : ((double) matchedCount / total);

            out.add(LectureDto.builder()
                    .lecIdx(l.getLecIdx())
                    .lecTitle(l.getLecTitle())
                    .lecUrl(l.getLecUrl())
                    .categoryLarge(l.getCategoryLarge())
                    .categoryMedium(l.getCategoryMedium())
                    .categorySmall(l.getCategorySmall())
                    .lectureTags(new ArrayList<>(lecTags))
                    .matchedTags(new ArrayList<>(matched))
                    .matchedCount(matchedCount)
                    .totalTags(total)
                    .hitRate(rate)
                    .build());
        }

        // 적중률 우선 정렬, 동일시 최신 lecIdx
        out.sort(Comparator.<LectureDto>comparingDouble(LectureDto::getHitRate).reversed()
                .thenComparing(LectureDto::getLecIdx).reversed());
        return out;
    }
}
