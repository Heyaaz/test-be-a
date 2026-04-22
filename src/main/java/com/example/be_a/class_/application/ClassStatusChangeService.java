package com.example.be_a.class_.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassStatusChangeService {

    private final ClassRepository classRepository;
    private final UserAuthorizationService userAuthorizationService;

    public ClassStatusChangeService(
        ClassRepository classRepository,
        UserAuthorizationService userAuthorizationService
    ) {
        this.classRepository = classRepository;
        this.userAuthorizationService = userAuthorizationService;
    }

    @Transactional
    public ClassEntity changeStatus(Long classId, CurrentUserInfo user, ClassStatus targetStatus) {
        userAuthorizationService.requireCreator(user);

        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new ApiException(ErrorCode.CLASS_NOT_FOUND));

        userAuthorizationService.requireOwner(user, classEntity.getCreatorId());
        classEntity.changeStatus(targetStatus);
        return classEntity;
    }
}
