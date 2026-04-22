package com.example.be_a.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.application.ApplyEnrollmentCommand;
import com.example.be_a.enrollment.application.EnrollmentApplyService;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.support.MySqlTestContainerSupport;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.domain.UserRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
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
class EnrollmentApplyConcurrencyIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private EnrollmentApplyService enrollmentApplyService;

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
        executorService = Executors.newFixedThreadPool(10);
        ensureUser(10L, "creator10@example.com", "Creator Ten", "CREATOR");
        for (long userId = 20L; userId < 30L; userId++) {
            ensureUser(userId, "student" + userId + "@example.com", "Student " + userId, "STUDENT");
        }
        enrollmentRepository.deleteAllInBatch();
        classRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        reset(enrollmentRepositorySpy);
    }

    @Test
    void 정원_1명_강의에_10명이_동시_신청하면_1명만_PENDING_성공하고_9명은_CLASS_FULL을_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 1, "동시 신청 강의");
        Queue<String> results = new ConcurrentLinkedQueue<>();
        CountDownLatch readyLatch = new CountDownLatch(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);

        for (long userId = 20L; userId < 30L; userId++) {
            long requestUserId = userId;
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    enrollmentApplyService.apply(
                        new CurrentUserInfo(requestUserId, UserRole.STUDENT),
                        new ApplyEnrollmentCommand(classEntity.getId(), false)
                    );
                    results.add("PENDING");
                } catch (ApiException exception) {
                    results.add(exception.getErrorCode().name());
                } catch (Exception exception) {
                    results.add("ERROR:" + exception.getClass().getSimpleName());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

        long successCount = results.stream().filter("PENDING"::equals).count();
        long classFullCount = results.stream().filter("CLASS_FULL"::equals).count();

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(results).hasSize(10);
        assertThat(successCount).isEqualTo(1);
        assertThat(classFullCount).isEqualTo(9);
        assertThat(results.stream().filter(result -> result.startsWith("ERROR:"))).isEmpty();
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollments).hasSize(1);
        assertThat(enrollments.get(0).getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    void 같은_사용자가_동시에_두_번_신청하면_실제_UNIQUE_위반이_ALREADY_ENROLLED로_정규화된다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 10, "중복 신청 경합 강의");
        Queue<String> results = new ConcurrentLinkedQueue<>();
        CountDownLatch existsReadyLatch = new CountDownLatch(2);
        CountDownLatch existsReleaseLatch = new CountDownLatch(1);

        doAnswer(invocation -> {
            Long classId = invocation.getArgument(0);
            Long userId = invocation.getArgument(1);
            if (classEntity.getId().equals(classId) && Long.valueOf(20L).equals(userId)) {
                existsReadyLatch.countDown();
                assertThat(existsReleaseLatch.await(5, TimeUnit.SECONDS)).isTrue();
                return false;
            }
            return invocation.callRealMethod();
        }).when(enrollmentRepositorySpy).existsByClassIdAndUserId(eq(classEntity.getId()), eq(20L));

        Callable<Void> applyTask = () -> {
            try {
                enrollmentApplyService.apply(
                    new CurrentUserInfo(20L, UserRole.STUDENT),
                    new ApplyEnrollmentCommand(classEntity.getId(), false)
                );
                results.add("PENDING");
            } catch (ApiException exception) {
                results.add(exception.getErrorCode().name());
            }
            return null;
        };

        var first = executorService.submit(applyTask);
        var second = executorService.submit(applyTask);

        assertThat(existsReadyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        existsReleaseLatch.countDown();

        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);

        long successCount = results.stream().filter("PENDING"::equals).count();
        long duplicateCount = results.stream().filter("ALREADY_ENROLLED"::equals).count();

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(results).hasSize(2);
        assertThat(successCount).isEqualTo(1);
        assertThat(duplicateCount).isEqualTo(1);
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollments).hasSize(1);
        assertThat(enrollments.get(0).getUserId()).isEqualTo(20L);
        assertThat(enrollments.get(0).getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private ClassEntity saveOpenClass(Long creatorId, int capacity, String title) {
        ClassEntity classEntity = classRepository.save(ClassEntity.createDraft(
            creatorId,
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
}
