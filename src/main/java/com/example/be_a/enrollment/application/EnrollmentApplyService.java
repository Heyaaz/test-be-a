package com.example.be_a.enrollment.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentApplyService {

    private static final String ENROLLMENT_UNIQUE_CONSTRAINT = "uk_enrollments_class_active_user";

    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentApplyService(
        ClassRepository classRepository,
        EnrollmentRepository enrollmentRepository
    ) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional
    public EnrollmentEntity apply(CurrentUserInfo user, ApplyEnrollmentCommand command) {
        Long classId = command.classId();
        Long userId = user.id();

        validateNotEnrolled(classId, userId);

        int updated = classRepository.tryIncrementEnrolled(classId);
        if (updated == 1) {
            return saveEnrollment(EnrollmentEntity.createPending(classId, userId));
        }

        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new ApiException(ErrorCode.CLASS_NOT_FOUND));

        ensureOpen(classEntity);

        if (!command.waitlist()) {
            throw new ApiException(ErrorCode.CLASS_FULL);
        }

        return saveEnrollment(EnrollmentEntity.createWaiting(classId, userId));
    }

    private void ensureOpen(ClassEntity classEntity) {
        if (classEntity.getStatus() != ClassStatus.OPEN) {
            throw new ApiException(ErrorCode.CLASS_NOT_OPEN);
        }
    }

    private void validateNotEnrolled(Long classId, Long userId) {
        if (enrollmentRepository.existsByClassIdAndUserIdAndStatusNot(
            classId,
            userId,
            EnrollmentStatus.CANCELLED
        )) {
            throw new ApiException(ErrorCode.ALREADY_ENROLLED);
        }
    }

    private EnrollmentEntity saveEnrollment(EnrollmentEntity enrollment) {
        try {
            return enrollmentRepository.saveAndFlush(enrollment);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEnrollmentException(exception)) {
                throw new ApiException(ErrorCode.ALREADY_ENROLLED);
            }
            throw exception;
        }
    }

    private boolean isDuplicateEnrollmentException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException
                && isEnrollmentUniqueConstraint(constraintViolationException.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isEnrollmentUniqueConstraint(String constraintName) {
        if (constraintName == null) {
            return false;
        }
        return constraintName.toLowerCase(Locale.ROOT)
            .endsWith(ENROLLMENT_UNIQUE_CONSTRAINT.toLowerCase(Locale.ROOT));
    }
}
