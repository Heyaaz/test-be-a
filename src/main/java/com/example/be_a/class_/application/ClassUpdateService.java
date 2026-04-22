package com.example.be_a.class_.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.class_.domain.UpdateClassCommand;
import com.example.be_a.enrollment.infrastructure.EnrollmentCountRepository;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ApiValidationException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassUpdateService {

    private final ClassRepository classRepository;
    private final UserAuthorizationService userAuthorizationService;
    private final EnrollmentCountRepository enrollmentCountRepository;

    public ClassUpdateService(
        ClassRepository classRepository,
        UserAuthorizationService userAuthorizationService,
        EnrollmentCountRepository enrollmentCountRepository
    ) {
        this.classRepository = classRepository;
        this.userAuthorizationService = userAuthorizationService;
        this.enrollmentCountRepository = enrollmentCountRepository;
    }

    @Transactional
    public ClassDetailView update(Long classId, CurrentUserInfo user, UpdateClassCommand command) {
        userAuthorizationService.requireCreator(user);

        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new ApiException(ErrorCode.CLASS_NOT_FOUND));

        userAuthorizationService.requireOwner(user, classEntity.getCreatorId());

        if (classEntity.getStatus() == ClassStatus.CLOSED) {
            throw new ApiException(ErrorCode.CLASS_UPDATE_NOT_ALLOWED);
        }

        updateClass(classEntity, command);

        long waitingCount = enrollmentCountRepository.countWaitingByClassIds(List.of(classId))
            .getOrDefault(classId, 0L);
        return ClassDetailView.from(classEntity, waitingCount);
    }

    private void updateClass(ClassEntity classEntity, UpdateClassCommand command) {
        validateDateRange(classEntity, command);
        validateCapacity(classEntity, command);
        classEntity.applyUpdate(command);
    }

    private void validateDateRange(ClassEntity classEntity, UpdateClassCommand command) {
        LocalDate startDate = command.hasStartDate() ? command.startDate() : classEntity.getStartDate();
        LocalDate endDate = command.hasEndDate() ? command.endDate() : classEntity.getEndDate();

        if (endDate.isBefore(startDate)) {
            throw ApiValidationException.of("endDate", "종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private void validateCapacity(ClassEntity classEntity, UpdateClassCommand command) {
        if (!command.hasCapacity()) {
            return;
        }
        if (command.capacity() < classEntity.getEnrolledCount()) {
            throw ApiValidationException.of("capacity", "정원은 현재 신청 인원보다 작을 수 없습니다.");
        }
    }
}
