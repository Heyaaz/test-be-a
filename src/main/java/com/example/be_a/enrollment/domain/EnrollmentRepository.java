package com.example.be_a.enrollment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    boolean existsByClassIdAndUserId(Long classId, Long userId);
}
