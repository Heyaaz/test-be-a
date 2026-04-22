package com.example.be_a.class_.application;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassCreateService {

    private final ClassRepository classRepository;
    private final UserAuthorizationService userAuthorizationService;

    public ClassCreateService(ClassRepository classRepository, UserAuthorizationService userAuthorizationService) {
        this.classRepository = classRepository;
        this.userAuthorizationService = userAuthorizationService;
    }

    @Transactional
    public ClassEntity create(CurrentUserInfo user, CreateClassCommand command) {
        userAuthorizationService.requireCreator(user);

        ClassEntity classEntity = ClassEntity.createDraft(
            user.id(),
            command.title(),
            command.description(),
            command.price(),
            command.capacity(),
            command.startDate(),
            command.endDate()
        );

        return classRepository.save(classEntity);
    }
}
