package com.example.be_a.enrollment.domain;

import com.example.be_a.enrollment.application.ClassEnrollmentSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    boolean existsByClassIdAndUserIdAndStatusNot(Long classId, Long userId, EnrollmentStatus status);

    Page<EnrollmentEntity> findAllByUserId(Long userId, Pageable pageable);

    Page<EnrollmentEntity> findAllByUserIdAndStatus(Long userId, EnrollmentStatus status, Pageable pageable);

    @Query(
        value = """
            SELECT new com.example.be_a.enrollment.application.ClassEnrollmentSummaryView(
                e.id,
                u.id,
                u.name,
                e.status,
                e.requestedAt,
                e.confirmedAt,
                e.cancelledAt
            )
            FROM EnrollmentEntity e
            JOIN UserEntity u ON e.userId = u.id
            WHERE e.classId = :classId
              AND NOT EXISTS (
                  SELECT 1
                  FROM EnrollmentEntity newer
                  WHERE newer.classId = e.classId
                    AND newer.userId = e.userId
                    AND (
                        newer.requestedAt > e.requestedAt
                        OR (newer.requestedAt = e.requestedAt AND newer.id > e.id)
                    )
              )
            """,
        countQuery = """
            SELECT COUNT(e)
            FROM EnrollmentEntity e
            WHERE e.classId = :classId
              AND NOT EXISTS (
                  SELECT 1
                  FROM EnrollmentEntity newer
                  WHERE newer.classId = e.classId
                    AND newer.userId = e.userId
                    AND (
                        newer.requestedAt > e.requestedAt
                        OR (newer.requestedAt = e.requestedAt AND newer.id > e.id)
                    )
              )
            """
    )
    Page<ClassEnrollmentSummaryView> findClassEnrollmentsByClassId(
        @Param("classId") Long classId,
        Pageable pageable
    );

    @Query(
        value = """
            SELECT new com.example.be_a.enrollment.application.ClassEnrollmentSummaryView(
                e.id,
                u.id,
                u.name,
                e.status,
                e.requestedAt,
                e.confirmedAt,
                e.cancelledAt
            )
            FROM EnrollmentEntity e
            JOIN UserEntity u ON e.userId = u.id
            WHERE e.classId = :classId
              AND e.status = :status
              AND NOT EXISTS (
                  SELECT 1
                  FROM EnrollmentEntity newer
                  WHERE newer.classId = e.classId
                    AND newer.userId = e.userId
                    AND (
                        newer.requestedAt > e.requestedAt
                        OR (newer.requestedAt = e.requestedAt AND newer.id > e.id)
                    )
              )
            """,
        countQuery = """
            SELECT COUNT(e)
            FROM EnrollmentEntity e
            WHERE e.classId = :classId
              AND e.status = :status
              AND NOT EXISTS (
                  SELECT 1
                  FROM EnrollmentEntity newer
                  WHERE newer.classId = e.classId
                    AND newer.userId = e.userId
                    AND (
                        newer.requestedAt > e.requestedAt
                        OR (newer.requestedAt = e.requestedAt AND newer.id > e.id)
                    )
              )
            """
    )
    Page<ClassEnrollmentSummaryView> findClassEnrollmentsByClassIdAndStatus(
        @Param("classId") Long classId,
        @Param("status") EnrollmentStatus status,
        Pageable pageable
    );

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
