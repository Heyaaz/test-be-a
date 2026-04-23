package com.example.be_a.enrollment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    boolean existsByClassIdAndUserId(Long classId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE enrollments e
        JOIN (
            SELECT waiting.id
            FROM (
                SELECT e2.id
                FROM enrollments e2
                JOIN classes c ON e2.class_id = c.id
                WHERE e2.class_id = :classId
                  AND c.status = 'OPEN'
                  AND e2.status = 'WAITING'
                ORDER BY e2.requested_at ASC, e2.id ASC
                LIMIT 1
            ) waiting
        ) target ON e.id = target.id
        SET e.status = 'PENDING',
            e.version = e.version + 1
        """, nativeQuery = true)
    int tryPromoteOldestWaiting(@Param("classId") Long classId);
}
