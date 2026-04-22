package com.example.be_a.enrollment.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentConfirmService {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final UserAuthorizationService userAuthorizationService;

    public EnrollmentConfirmService(
        EnrollmentRepository enrollmentRepository,
        ClassRepository classRepository,
        UserAuthorizationService userAuthorizationService
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.classRepository = classRepository;
        this.userAuthorizationService = userAuthorizationService;
    }

    @Transactional
    public EnrollmentEntity confirm(Long enrollmentId, CurrentUserInfo user) {
        EnrollmentEntity enrollment = loadOwnedEnrollment(enrollmentId, user);
        ensureClassConfirmable(loadClass(enrollment.getClassId()));

        enrollment.confirm(LocalDateTime.now());
        return enrollment;
    }

    private EnrollmentEntity loadOwnedEnrollment(Long enrollmentId, CurrentUserInfo user) {
        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENROLLMENT_NOT_FOUND));

        userAuthorizationService.requireOwner(user, enrollment.getUserId());
        return enrollment;
    }

    private ClassEntity loadClass(Long classId) {
        return classRepository.findById(classId)
            .orElseThrow(() -> new ApiException(ErrorCode.CLASS_NOT_FOUND));
    }

    private void ensureClassConfirmable(ClassEntity classEntity) {
        if (classEntity.getStatus() != ClassStatus.OPEN) {
            throw new ApiException(ErrorCode.CLASS_NOT_OPEN);
        }
    }
}
