package com.example.be_a.class_.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.enrollment.application.ClassEnrollmentSummaryView;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClassEnrollmentReadService {

    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserAuthorizationService userAuthorizationService;

    public ClassEnrollmentReadService(
        ClassRepository classRepository,
        EnrollmentRepository enrollmentRepository,
        UserAuthorizationService userAuthorizationService
    ) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userAuthorizationService = userAuthorizationService;
    }

    public Page<ClassEnrollmentSummaryView> listByClass(
        Long classId,
        CurrentUserInfo user,
        EnrollmentStatus status,
        Pageable pageable
    ) {
        userAuthorizationService.requireCreator(user);

        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new ApiException(ErrorCode.CLASS_NOT_FOUND));

        userAuthorizationService.requireOwner(user, classEntity.getCreatorId());

        return status == null
            ? enrollmentRepository.findClassEnrollmentsByClassId(classId, pageable)
            : enrollmentRepository.findClassEnrollmentsByClassIdAndStatus(classId, status, pageable);
    }
}
