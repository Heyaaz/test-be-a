package com.example.be_a.enrollment.application;

import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.user.application.CurrentUserInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EnrollmentReadService {

    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentReadService(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    public Page<EnrollmentEntity> listMine(CurrentUserInfo user, EnrollmentStatus status, Pageable pageable) {
        return status == null
            ? enrollmentRepository.findAllByUserId(user.id(), pageable)
            : enrollmentRepository.findAllByUserIdAndStatus(user.id(), status, pageable);
    }
}
