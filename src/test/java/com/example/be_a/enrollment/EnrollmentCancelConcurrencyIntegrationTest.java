package com.example.be_a.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.application.EnrollmentCancelService;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.support.MySqlTestContainerSupport;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.domain.UserRole;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentCancelConcurrencyIntegrationTest extends MySqlTestContainerSupport {

    private static final LocalDateTime BASE_REQUESTED_AT = LocalDateTime.of(2026, 4, 23, 10, 0);

    @Autowired
    private EnrollmentCancelService enrollmentCancelService;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @SpyBean
    private EnrollmentRepository enrollmentRepositorySpy;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(2);
        ensureUser(10L, "creator10@example.com", "Creator Ten", "CREATOR");
        ensureUser(20L, "student20@example.com", "Student Twenty", "STUDENT");
        ensureUser(21L, "student21@example.com", "Student Twenty One", "STUDENT");
        ensureUser(22L, "student22@example.com", "Student Twenty Two", "STUDENT");
        enrollmentRepository.deleteAllInBatch();
        classRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        reset(enrollmentRepositorySpy);
    }

    @Test
    void 승급_대상_WAITING이_동시에_자기_취소되면_다음_WAITING이_승급되고_정원_불변식이_유지된다() throws Exception {
        ClassEntity classEntity = saveOpenClass(1, "승급 자기 취소 경합 강의");
        updateEnrolledCount(classEntity.getId(), 1);
        Long pendingEnrollmentId = insertEnrollmentAt(
            classEntity.getId(),
            20L,
            EnrollmentStatus.PENDING,
            BASE_REQUESTED_AT.minusMinutes(3)
        );
        Long cancellingWaitingEnrollmentId = insertEnrollmentAt(
            classEntity.getId(),
            21L,
            EnrollmentStatus.WAITING,
            BASE_REQUESTED_AT.minusMinutes(2)
        );
        Long nextWaitingEnrollmentId = insertEnrollmentAt(
            classEntity.getId(),
            22L,
            EnrollmentStatus.WAITING,
            BASE_REQUESTED_AT.minusMinutes(1)
        );
        CountDownLatch promotionReachedLatch = new CountDownLatch(1);
        CountDownLatch waitingCancelDoneLatch = new CountDownLatch(1);
        Queue<String> results = new ConcurrentLinkedQueue<>();

        doAnswer(invocation -> {
            Long classId = invocation.getArgument(0);
            if (classEntity.getId().equals(classId)) {
                // 승급 native UPDATE 직전에 가장 오래된 WAITING 신청자의 자기 취소를 끼워 넣는다.
                promotionReachedLatch.countDown();
                assertThat(waitingCancelDoneLatch.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return invocation.callRealMethod();
        }).when(enrollmentRepositorySpy).tryPromoteOldestWaiting(eq(classEntity.getId()));

        var pendingCancel = executorService.submit(() -> cancelAndRecord(pendingEnrollmentId, 20L, results));
        assertThat(promotionReachedLatch.await(5, TimeUnit.SECONDS)).isTrue();

        var waitingSelfCancel = executorService.submit(() -> {
            try {
                cancelAndRecord(cancellingWaitingEnrollmentId, 21L, results);
            } finally {
                waitingCancelDoneLatch.countDown();
            }
        });

        pendingCancel.get(10, TimeUnit.SECONDS);
        waitingSelfCancel.get(10, TimeUnit.SECONDS);

        EnrollmentEntity cancelledPending = enrollmentRepository.findById(pendingEnrollmentId).orElseThrow();
        EnrollmentEntity cancelledWaiting = enrollmentRepository.findById(cancellingWaitingEnrollmentId).orElseThrow();
        EnrollmentEntity promotedWaiting = enrollmentRepository.findById(nextWaitingEnrollmentId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(results).containsExactlyInAnyOrder("CANCELLED:20", "CANCELLED:21");
        assertThat(results.stream().filter(result -> result.startsWith("ERROR:")).toList()).isEmpty();
        assertThat(results).doesNotContain("CONFLICT_RETRY:20", "CONFLICT_RETRY:21");
        assertThat(enrollments).hasSize(3);
        assertThat(cancelledPending.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(cancelledWaiting.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(promotedWaiting.getUserId()).isEqualTo(22L);
        assertThat(promotedWaiting.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(countActiveEnrollments(enrollments)).isEqualTo(savedClass.getEnrolledCount());
    }

    private void cancelAndRecord(Long enrollmentId, Long userId, Queue<String> results) {
        try {
            EnrollmentEntity cancelled = enrollmentCancelService.cancel(
                enrollmentId,
                new CurrentUserInfo(userId, UserRole.STUDENT)
            );
            results.add(cancelled.getStatus().name() + ":" + userId);
        } catch (ApiException exception) {
            results.add(exception.getErrorCode().name() + ":" + userId);
        } catch (Exception exception) {
            results.add("ERROR:" + exception.getClass().getSimpleName() + ":" + userId);
        }
    }

    private long countActiveEnrollments(List<EnrollmentEntity> enrollments) {
        return enrollments.stream()
            .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.PENDING
                || enrollment.getStatus() == EnrollmentStatus.CONFIRMED)
            .count();
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private ClassEntity saveOpenClass(int capacity, String title) {
        ClassEntity classEntity = classRepository.save(ClassEntity.createDraft(
            10L,
            title,
            "설명",
            30000,
            capacity,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        ));
        jdbcTemplate.update("UPDATE classes SET status = ? WHERE id = ?", ClassStatus.OPEN.name(), classEntity.getId());
        return classRepository.findById(classEntity.getId()).orElseThrow();
    }

    private void updateEnrolledCount(Long classId, int enrolledCount) {
        jdbcTemplate.update("UPDATE classes SET enrolled_count = ? WHERE id = ?", enrolledCount, classId);
    }

    private Long insertEnrollmentAt(
        Long classId,
        Long userId,
        EnrollmentStatus status,
        LocalDateTime requestedAt
    ) {
        jdbcTemplate.update("""
            INSERT INTO enrollments (class_id, user_id, status, requested_at)
            VALUES (?, ?, ?, ?)
            """,
            classId,
            userId,
            status.name(),
            requestedAt
        );

        return jdbcTemplate.queryForObject(
            "SELECT id FROM enrollments WHERE class_id = ? AND user_id = ?",
            Long.class,
            classId,
            userId
        );
    }
}
