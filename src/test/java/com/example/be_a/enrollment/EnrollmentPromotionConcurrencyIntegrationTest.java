package com.example.be_a.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.example.be_a.class_.application.ClassStatusChangeService;
import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.application.ApplyEnrollmentCommand;
import com.example.be_a.enrollment.application.EnrollmentApplyService;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentPromotionConcurrencyIntegrationTest extends MySqlTestContainerSupport {

    private static final LocalDateTime BASE_REQUESTED_AT = LocalDateTime.of(2026, 4, 23, 10, 0);

    @Autowired
    private EnrollmentApplyService enrollmentApplyService;

    @Autowired
    private EnrollmentCancelService enrollmentCancelService;

    @Autowired
    private ClassStatusChangeService classStatusChangeService;

    @Autowired
    private ClassRepository classRepository;

    @SpyBean
    private ClassRepository classRepositorySpy;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @SpyBean
    private EnrollmentRepository enrollmentRepositorySpy;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(10);
        ensureUser(10L, "creator10@example.com", "Creator Ten", "CREATOR");
        for (long userId = 20L; userId < 40L; userId++) {
            ensureUser(userId, "student" + userId + "@example.com", "Student " + userId, "STUDENT");
        }
        enrollmentRepository.deleteAllInBatch();
        classRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        reset(classRepositorySpy);
        reset(enrollmentRepositorySpy);
    }

    @Test
    void 여러_PENDING_취소가_동시에_발생해도_WAITING은_FIFO_순서대로_승급된다() throws Exception {
        ClassEntity classEntity = saveOpenClass(2, "대기열 FIFO 병렬 경합 강의");
        updateEnrolledCount(classEntity.getId(), 2);
        Long firstPendingId = insertEnrollmentAt(classEntity.getId(), 20L, EnrollmentStatus.PENDING, BASE_REQUESTED_AT.minusMinutes(5));
        Long secondPendingId = insertEnrollmentAt(classEntity.getId(), 21L, EnrollmentStatus.PENDING, BASE_REQUESTED_AT.minusMinutes(4));
        Long firstWaitingId = insertEnrollmentAt(classEntity.getId(), 22L, EnrollmentStatus.WAITING, BASE_REQUESTED_AT.minusMinutes(3));
        Long secondWaitingId = insertEnrollmentAt(classEntity.getId(), 23L, EnrollmentStatus.WAITING, BASE_REQUESTED_AT.minusMinutes(2));
        Long thirdWaitingId = insertEnrollmentAt(classEntity.getId(), 24L, EnrollmentStatus.WAITING, BASE_REQUESTED_AT.minusMinutes(1));
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Queue<String> results = new ConcurrentLinkedQueue<>();

        var firstCancel = executorService.submit(() -> cancelAndRecordWithTransientRetry(firstPendingId, 20L, readyLatch, startLatch, results));
        var secondCancel = executorService.submit(() -> cancelAndRecordWithTransientRetry(secondPendingId, 21L, readyLatch, startLatch, results));

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        firstCancel.get(10, TimeUnit.SECONDS);
        secondCancel.get(10, TimeUnit.SECONDS);

        EnrollmentEntity firstCancelled = enrollmentRepository.findById(firstPendingId).orElseThrow();
        EnrollmentEntity secondCancelled = enrollmentRepository.findById(secondPendingId).orElseThrow();
        EnrollmentEntity firstWaiting = enrollmentRepository.findById(firstWaitingId).orElseThrow();
        EnrollmentEntity secondWaiting = enrollmentRepository.findById(secondWaitingId).orElseThrow();
        EnrollmentEntity thirdWaiting = enrollmentRepository.findById(thirdWaitingId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        // 취소별 승급은 classes 행 X-lock으로 직렬화되므로, 직렬 승급 중에도 FIFO가 유지되어야 한다.
        assertThat(results).containsExactlyInAnyOrder("CANCELLED:20", "CANCELLED:21");
        assertNoUnexpectedConcurrencyErrors(results);
        assertThat(firstCancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(secondCancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(firstWaiting.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(secondWaiting.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(thirdWaiting.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
        assertThat(savedClass.getEnrolledCount()).isEqualTo(2);
        assertActiveCountMatchesEnrolledCount(savedClass, enrollments);
    }

    @Test
    void PENDING_취소와_신규_신청이_경합하면_기존_WAITING이_좌석을_우선_배정받는다() throws Exception {
        ClassEntity classEntity = saveOpenClass(1, "승급 신규 신청 경합 강의");
        updateEnrolledCount(classEntity.getId(), 1);
        Long pendingId = insertEnrollmentAt(classEntity.getId(), 20L, EnrollmentStatus.PENDING, BASE_REQUESTED_AT.minusMinutes(2));
        Long waitingId = insertEnrollmentAt(classEntity.getId(), 21L, EnrollmentStatus.WAITING, BASE_REQUESTED_AT.minusMinutes(1));
        CountDownLatch classLockHeldLatch = new CountDownLatch(1);
        CountDownLatch applyIncrementAttemptedLatch = new CountDownLatch(1);
        AtomicReference<Thread> applyThread = new AtomicReference<>();
        Queue<String> results = new ConcurrentLinkedQueue<>();

        doAnswer(invocation -> {
            Long classId = invocation.getArgument(0);
            if (classEntity.getId().equals(classId)) {
                classLockHeldLatch.countDown();
                assertThat(applyIncrementAttemptedLatch.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return tryPromoteOldestWaitingDirectly(classId);
        }).when(enrollmentRepositorySpy).tryPromoteOldestWaiting(eq(classEntity.getId()));
        doAnswer(invocation -> {
            Long classId = invocation.getArgument(0);
            if (Thread.currentThread() == applyThread.get()) {
                applyIncrementAttemptedLatch.countDown();
            }
            return tryIncrementEnrolledDirectly(classId);
        }).when(classRepositorySpy).tryIncrementEnrolled(eq(classEntity.getId()));

        var cancelTask = executorService.submit(() -> cancelAndRecord(pendingId, 20L, results));
        assertThat(classLockHeldLatch.await(5, TimeUnit.SECONDS)).isTrue();
        var applyTask = executorService.submit(() -> {
            applyThread.set(Thread.currentThread());
            applyAndRecord(classEntity.getId(), 22L, false, results);
        });

        cancelTask.get(10, TimeUnit.SECONDS);
        applyTask.get(10, TimeUnit.SECONDS);

        EnrollmentEntity waiting = enrollmentRepository.findById(waitingId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(results).containsExactlyInAnyOrder("CANCELLED:20", "CLASS_FULL:22");
        assertNoUnexpectedConcurrencyErrors(results);
        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollments).noneMatch(enrollment -> enrollment.getUserId().equals(22L));
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertActiveCountMatchesEnrolledCount(savedClass, enrollments);
    }

    @Test
    void 여러_PENDING_취소보다_WAITING이_적으면_승급된_인원만_정원에_반영된다() throws Exception {
        ClassEntity classEntity = saveOpenClass(2, "대기열 부족 병렬 취소 강의");
        updateEnrolledCount(classEntity.getId(), 2);
        Long firstPendingId = insertEnrollmentAt(classEntity.getId(), 20L, EnrollmentStatus.PENDING, BASE_REQUESTED_AT.minusMinutes(3));
        Long secondPendingId = insertEnrollmentAt(classEntity.getId(), 21L, EnrollmentStatus.PENDING, BASE_REQUESTED_AT.minusMinutes(2));
        Long waitingId = insertEnrollmentAt(classEntity.getId(), 22L, EnrollmentStatus.WAITING, BASE_REQUESTED_AT.minusMinutes(1));
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Queue<String> results = new ConcurrentLinkedQueue<>();

        var firstCancel = executorService.submit(() -> cancelAndRecordWithTransientRetry(firstPendingId, 20L, readyLatch, startLatch, results));
        var secondCancel = executorService.submit(() -> cancelAndRecordWithTransientRetry(secondPendingId, 21L, readyLatch, startLatch, results));

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        firstCancel.get(10, TimeUnit.SECONDS);
        secondCancel.get(10, TimeUnit.SECONDS);

        EnrollmentEntity firstCancelled = enrollmentRepository.findById(firstPendingId).orElseThrow();
        EnrollmentEntity secondCancelled = enrollmentRepository.findById(secondPendingId).orElseThrow();
        EnrollmentEntity promoted = enrollmentRepository.findById(waitingId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(results).containsExactlyInAnyOrder("CANCELLED:20", "CANCELLED:21");
        assertNoUnexpectedConcurrencyErrors(results);
        assertThat(firstCancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(secondCancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertActiveCountMatchesEnrolledCount(savedClass, enrollments);
    }

    @Test
    void 승급_대상_WAITING이_자기_취소하면_다음_WAITING을_승급한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(1, "승급 대상 자기 취소 경합 강의");
        updateEnrolledCount(classEntity.getId(), 1);
        Long pendingId = insertEnrollmentAt(classEntity.getId(), 20L, EnrollmentStatus.PENDING, BASE_REQUESTED_AT.minusMinutes(3));
        Long firstWaitingId = insertEnrollmentAt(classEntity.getId(), 21L, EnrollmentStatus.WAITING, BASE_REQUESTED_AT.minusMinutes(2));
        Long secondWaitingId = insertEnrollmentAt(classEntity.getId(), 22L, EnrollmentStatus.WAITING, BASE_REQUESTED_AT.minusMinutes(1));
        CountDownLatch firstWaitingCancelledLatch = new CountDownLatch(1);
        Queue<String> results = new ConcurrentLinkedQueue<>();

        var waitingCancelTask = executorService.submit(() -> {
            try {
                cancelWaitingDirectlyAndRecord(firstWaitingId, 21L, results);
            } finally {
                firstWaitingCancelledLatch.countDown();
            }
        });
        var pendingCancelTask = executorService.submit(() -> {
            try {
                assertThat(firstWaitingCancelledLatch.await(5, TimeUnit.SECONDS)).isTrue();
                cancelAndRecord(pendingId, 20L, results);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                results.add("ERROR:InterruptedException:20");
            }
        });

        waitingCancelTask.get(10, TimeUnit.SECONDS);
        pendingCancelTask.get(10, TimeUnit.SECONDS);

        EnrollmentEntity cancelledPending = enrollmentRepository.findById(pendingId).orElseThrow();
        EnrollmentEntity cancelledWaiting = enrollmentRepository.findById(firstWaitingId).orElseThrow();
        EnrollmentEntity promotedWaiting = enrollmentRepository.findById(secondWaitingId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(results).containsExactlyInAnyOrder("CANCELLED:20", "CANCELLED:21");
        assertNoUnexpectedConcurrencyErrors(results);
        assertThat(cancelledPending.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(cancelledWaiting.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(promotedWaiting.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertActiveCountMatchesEnrolledCount(savedClass, enrollments);
    }

    @Test
    void CLOSED_전환_중_PENDING_취소가_발생해도_정원_불변식이_유지된다() throws Exception {
        ClassEntity classEntity = saveOpenClass(1, "마감 전환 취소 경합 강의");
        updateEnrolledCount(classEntity.getId(), 1);
        Long pendingId = insertEnrollmentAt(classEntity.getId(), 20L, EnrollmentStatus.PENDING, BASE_REQUESTED_AT.minusMinutes(2));
        Long waitingId = insertEnrollmentAt(classEntity.getId(), 21L, EnrollmentStatus.WAITING, BASE_REQUESTED_AT.minusMinutes(1));
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Queue<String> results = new ConcurrentLinkedQueue<>();

        var closeTask = executorService.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                classStatusChangeService.changeStatus(
                    classEntity.getId(),
                    new CurrentUserInfo(10L, UserRole.CREATOR),
                    ClassStatus.CLOSED
                );
                results.add("CLOSED");
            } catch (Exception exception) {
                results.add(resultOf(exception) + ":close");
            }
        });
        var cancelTask = executorService.submit(() -> cancelAndRecord(pendingId, 20L, readyLatch, startLatch, results));

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        closeTask.get(10, TimeUnit.SECONDS);
        cancelTask.get(10, TimeUnit.SECONDS);

        EnrollmentEntity cancelled = enrollmentRepository.findById(pendingId).orElseThrow();
        EnrollmentEntity waiting = enrollmentRepository.findById(waitingId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        // latch는 시작점만 맞추며, 마감/취소의 최종 classes 갱신은 DB write lock으로 직렬화된다.
        assertThat(results).containsExactlyInAnyOrder("CANCELLED:20", "CLOSED");
        assertNoUnexpectedConcurrencyErrors(results);
        assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(savedClass.getStatus()).isEqualTo(ClassStatus.CLOSED);
        assertThat(waiting.getStatus()).isIn(EnrollmentStatus.WAITING, EnrollmentStatus.PENDING);
        assertThat(savedClass.getEnrolledCount()).isIn(0, 1);
        assertActiveCountMatchesEnrolledCount(savedClass, enrollments);
        if (waiting.getStatus() == EnrollmentStatus.PENDING) {
            assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        } else {
            assertThat(savedClass.getEnrolledCount()).isZero();
        }
    }

    private void cancelAndRecord(
        Long enrollmentId,
        Long userId,
        CountDownLatch readyLatch,
        CountDownLatch startLatch,
        Queue<String> results
    ) {
        readyLatch.countDown();
        try {
            startLatch.await();
            cancelAndRecord(enrollmentId, userId, results);
        } catch (Exception exception) {
            results.add(resultOf(exception) + ":" + userId);
        }
    }

    private void cancelAndRecordWithTransientRetry(
        Long enrollmentId,
        Long userId,
        CountDownLatch readyLatch,
        CountDownLatch startLatch,
        Queue<String> results
    ) {
        readyLatch.countDown();
        try {
            startLatch.await();
            cancelAndRecordWithTransientRetry(enrollmentId, userId, results);
        } catch (Exception exception) {
            results.add(resultOf(exception) + ":" + userId);
        }
    }

    private void cancelAndRecord(Long enrollmentId, Long userId, Queue<String> results) {
        try {
            EnrollmentEntity cancelled = enrollmentCancelService.cancel(
                enrollmentId,
                new CurrentUserInfo(userId, UserRole.STUDENT)
            );
            results.add(cancelled.getStatus().name() + ":" + userId);
        } catch (Exception exception) {
            results.add(resultOf(exception) + ":" + userId);
        }
    }

    private void cancelAndRecordWithTransientRetry(Long enrollmentId, Long userId, Queue<String> results) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                EnrollmentEntity cancelled = enrollmentCancelService.cancel(
                    enrollmentId,
                    new CurrentUserInfo(userId, UserRole.STUDENT)
                );
                results.add(cancelled.getStatus().name() + ":" + userId);
                return;
            } catch (CannotAcquireLockException exception) {
                if (attempt == 2) {
                    results.add(resultOf(exception) + ":" + userId);
                    return;
                }
                sleepBriefly();
            } catch (Exception exception) {
                results.add(resultOf(exception) + ":" + userId);
                return;
            }
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void applyAndRecord(Long classId, Long userId, boolean waitlist, Queue<String> results) {
        try {
            EnrollmentEntity enrollment = enrollmentApplyService.apply(
                new CurrentUserInfo(userId, UserRole.STUDENT),
                new ApplyEnrollmentCommand(classId, waitlist)
            );
            results.add(enrollment.getStatus().name() + ":" + userId);
        } catch (Exception exception) {
            results.add(resultOf(exception) + ":" + userId);
        }
    }

    private void cancelWaitingDirectlyAndRecord(Long enrollmentId, Long userId, Queue<String> results) {
        try {
            int updated = jdbcTemplate.update("""
                UPDATE enrollments
                SET status = ?,
                    cancelled_at = ?,
                    version = version + 1
                WHERE id = ?
                  AND user_id = ?
                  AND status = ?
                """,
                EnrollmentStatus.CANCELLED.name(),
                LocalDateTime.now(),
                enrollmentId,
                userId,
                EnrollmentStatus.WAITING.name()
            );
            if (updated == 1) {
                results.add("CANCELLED:" + userId);
                return;
            }
            results.add("INVALID_STATE_TRANSITION:" + userId);
        } catch (Exception exception) {
            results.add(resultOf(exception) + ":" + userId);
        }
    }

    private int tryPromoteOldestWaitingDirectly(Long classId) {
        return jdbcTemplate.update("""
            UPDATE enrollments e
            JOIN (
                SELECT waiting.id
                FROM (
                    SELECT e2.id
                    FROM enrollments e2
                    JOIN classes c ON e2.class_id = c.id
                    WHERE e2.class_id = ?
                      AND c.status = 'OPEN'
                      AND e2.status = 'WAITING'
                    ORDER BY e2.requested_at ASC, e2.id ASC
                    LIMIT 1
                ) waiting
            ) target ON e.id = target.id
            SET e.status = 'PENDING',
                e.version = e.version + 1
            """, classId);
    }

    private int tryIncrementEnrolledDirectly(Long classId) {
        return jdbcTemplate.update("""
            UPDATE classes
            SET enrolled_count = enrolled_count + 1
            WHERE id = ?
              AND status = 'OPEN'
              AND enrolled_count < capacity
            """, classId);
    }

    private String resultOf(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.getErrorCode().name();
        }
        return "ERROR:" + exception.getClass().getSimpleName();
    }

    private void assertActiveCountMatchesEnrolledCount(ClassEntity classEntity, List<EnrollmentEntity> enrollments) {
        long activeCount = enrollments.stream()
            .filter(enrollment -> classEntity.getId().equals(enrollment.getClassId()))
            .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.PENDING
                || enrollment.getStatus() == EnrollmentStatus.CONFIRMED)
            .count();

        assertThat(activeCount).isEqualTo(classEntity.getEnrolledCount());
    }

    private void assertNoUnexpectedConcurrencyErrors(Queue<String> results) {
        assertThat(results).noneMatch(result -> result.startsWith("ERROR:"));
        assertThat(results).noneMatch(result -> result.startsWith("CONFLICT_RETRY:"));
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
            "SELECT id FROM enrollments WHERE class_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
            Long.class,
            classId,
            userId
        );
    }

}
