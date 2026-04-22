package com.example.be_a.class_.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    Page<ClassEntity> findAllByStatus(ClassStatus status, Pageable pageable);
}
