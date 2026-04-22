package com.example.be_a.class_.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.infrastructure.EnrollmentCountRepository;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClassReadService {

    private final ClassRepository classRepository;
    private final EnrollmentCountRepository enrollmentCountRepository;

    public ClassReadService(ClassRepository classRepository, EnrollmentCountRepository enrollmentCountRepository) {
        this.classRepository = classRepository;
        this.enrollmentCountRepository = enrollmentCountRepository;
    }

    public Page<ClassSummaryView> list(ClassStatus status, Pageable pageable) {
        Page<ClassEntity> classPage = status == null
            ? classRepository.findAll(pageable)
            : classRepository.findAllByStatus(status, pageable);

        List<Long> classIds = classPage.getContent().stream()
            .map(ClassEntity::getId)
            .toList();
        Map<Long, Long> waitingCounts = enrollmentCountRepository.countWaitingByClassIds(classIds);

        return classPage.map(classEntity -> ClassSummaryView.from(
            classEntity,
            waitingCounts.getOrDefault(classEntity.getId(), 0L)
        ));
    }

    public ClassDetailView get(Long classId) {
        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new ApiException(ErrorCode.CLASS_NOT_FOUND));

        long waitingCount = enrollmentCountRepository.countWaitingByClassIds(List.of(classId))
            .getOrDefault(classId, 0L);

        return ClassDetailView.from(classEntity, waitingCount);
    }
}
