package com.example.be_a.enrollment.application;

import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentCancelService {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final UserAuthorizationService userAuthorizationService;
    private final Clock clock;

    public EnrollmentCancelService(
        EnrollmentRepository enrollmentRepository,
        ClassRepository classRepository,
        UserAuthorizationService userAuthorizationService,
        Clock clock
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.classRepository = classRepository;
        this.userAuthorizationService = userAuthorizationService;
        this.clock = clock;
    }

    @Transactional
    public EnrollmentEntity cancel(Long enrollmentId, CurrentUserInfo user) {
        EnrollmentEntity enrollment = loadOwnedEnrollment(enrollmentId, user);
        EnrollmentStatus previousStatus = enrollment.getStatus();

        enrollment.cancel(LocalDateTime.now(clock));
        saveEnrollment(enrollment);

        if (previousStatus == EnrollmentStatus.WAITING) {
            return enrollment;
        }

        restoreCapacity(enrollment.getClassId());
        return enrollment;
    }

    private EnrollmentEntity loadOwnedEnrollment(Long enrollmentId, CurrentUserInfo user) {
        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENROLLMENT_NOT_FOUND));

        userAuthorizationService.requireOwner(user, enrollment.getUserId());
        return enrollment;
    }

    private void saveEnrollment(EnrollmentEntity enrollment) {
        try {
            enrollmentRepository.saveAndFlush(enrollment);
        } catch (OptimisticLockingFailureException exception) {
            throw new ApiException(ErrorCode.CONFLICT_RETRY);
        }
    }

    private void restoreCapacity(Long classId) {
        if (classRepository.tryDecrementEnrolled(classId) == 0) {
            throw new IllegalStateException("수강 인원 복구에 실패했습니다. classId=" + classId);
        }
    }
}
