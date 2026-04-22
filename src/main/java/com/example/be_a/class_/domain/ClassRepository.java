package com.example.be_a.class_.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    Page<ClassEntity> findAllByStatus(ClassStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE classes
        SET enrolled_count = enrolled_count + 1
        WHERE id = :id
          AND status = 'OPEN'
          AND enrolled_count < capacity
        """, nativeQuery = true)
    int tryIncrementEnrolled(@Param("id") Long id);
}
