package com.example.be_a.class_.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.infrastructure.EnrollmentCountRepository;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassDeleteService {

    private final ClassRepository classRepository;
    private final UserAuthorizationService userAuthorizationService;
    private final EnrollmentCountRepository enrollmentCountRepository;

    public ClassDeleteService(
        ClassRepository classRepository,
        UserAuthorizationService userAuthorizationService,
        EnrollmentCountRepository enrollmentCountRepository
    ) {
        this.classRepository = classRepository;
        this.userAuthorizationService = userAuthorizationService;
        this.enrollmentCountRepository = enrollmentCountRepository;
    }

    @Transactional
    public void delete(Long classId, CurrentUserInfo user) {
        userAuthorizationService.requireCreator(user);

        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new ApiException(ErrorCode.CLASS_NOT_FOUND));

        userAuthorizationService.requireOwner(user, classEntity.getCreatorId());

        if (classEntity.getStatus() != ClassStatus.DRAFT) {
            throw new ApiException(ErrorCode.CLASS_NOT_DRAFT);
        }

        if (enrollmentCountRepository.existsByClassId(classId)) {
            throw new ApiException(ErrorCode.CLASS_DELETE_NOT_ALLOWED);
        }

        try {
            classRepository.delete(classEntity);
            classRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.CLASS_DELETE_NOT_ALLOWED);
        }
    }
}
