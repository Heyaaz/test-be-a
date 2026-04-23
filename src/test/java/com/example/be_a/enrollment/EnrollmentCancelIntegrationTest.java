package com.example.be_a.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.support.MySqlTestContainerSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EnrollmentCancelIntegrationTest.FixedClockConfig.class)
class EnrollmentCancelIntegrationTest extends MySqlTestContainerSupport {

    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 4, 23, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ensureUser(10L, "creator10@example.com", "Creator Ten", "CREATOR");
        ensureUser(20L, "student20@example.com", "Student Twenty", "STUDENT");
        ensureUser(21L, "student21@example.com", "Student Twenty One", "STUDENT");
        enrollmentRepository.deleteAllInBatch();
        classRepository.deleteAllInBatch();
    }

    @Test
    void WAITING_수강_신청은_정원_변화_없이_취소할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.WAITING, null, null);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(enrollmentId))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").exists());

        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollment.getCancelledAt()).isNotNull();
        assertThat(savedClass.getEnrolledCount()).isZero();
    }

    @Test
    void PENDING_수강_신청은_정원을_복구하며_취소할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        updateEnrolledCount(classEntity.getId(), 1);
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.PENDING, null, null);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(enrollmentId))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").exists());

        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(savedClass.getEnrolledCount()).isZero();
    }

    @Test
    void CONFIRMED_상태고_7일_이내면_취소할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        updateEnrolledCount(classEntity.getId(), 1);
        Long enrollmentId = insertEnrollment(
            classEntity.getId(),
            20L,
            EnrollmentStatus.CONFIRMED,
            FIXED_NOW.minusDays(6),
            null
        );

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(enrollmentId))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").exists());

        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(savedClass.getEnrolledCount()).isZero();
    }

    @Test
    void CONFIRMED_상태고_정확히_7일째면_취소할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        updateEnrolledCount(classEntity.getId(), 1);
        Long enrollmentId = insertEnrollment(
            classEntity.getId(),
            20L,
            EnrollmentStatus.CONFIRMED,
            FIXED_NOW.minusDays(7),
            null
        );

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(enrollmentId))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").exists());

        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(savedClass.getEnrolledCount()).isZero();
    }

    @Test
    void CONFIRMED_상태고_7일이_지나면_CANCEL_PERIOD_EXPIRED를_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        updateEnrolledCount(classEntity.getId(), 1);
        Long enrollmentId = insertEnrollment(
            classEntity.getId(),
            20L,
            EnrollmentStatus.CONFIRMED,
            FIXED_NOW.minusDays(8),
            null
        );

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CANCEL_PERIOD_EXPIRED"));

        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
    }

    @Test
    void 이미_CANCELLED_상태면_INVALID_STATE_TRANSITION을_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        Long enrollmentId = insertEnrollment(
            classEntity.getId(),
            20L,
            EnrollmentStatus.CANCELLED,
            null,
            FIXED_NOW.minusDays(1)
        );

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void CLOSED_강의의_CONFIRMED_수강도_취소할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        updateEnrolledCount(classEntity.getId(), 1);
        Long enrollmentId = insertEnrollment(
            classEntity.getId(),
            20L,
            EnrollmentStatus.CONFIRMED,
            FIXED_NOW.minusDays(3),
            null
        );
        updateStatus(classEntity.getId(), ClassStatus.CLOSED);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(enrollmentId))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").exists());

        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(savedClass.getEnrolledCount()).isZero();
    }

    @Test
    void 다른_사용자의_수강_신청은_취소할_수_없다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        updateEnrolledCount(classEntity.getId(), 1);
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.PENDING, null, null);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", "21"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 존재하지_않는_수강_신청은_ENROLLMENT_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", 99999L)
                .header("X-User-Id", "20"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private ClassEntity saveOpenClass() {
        ClassEntity classEntity = classRepository.save(ClassEntity.createDraft(
            10L,
            "수강 취소 강의",
            "설명",
            30000,
            30,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        ));
        updateStatus(classEntity.getId(), ClassStatus.OPEN);
        return classRepository.findById(classEntity.getId()).orElseThrow();
    }

    private void updateStatus(Long classId, ClassStatus status) {
        jdbcTemplate.update("UPDATE classes SET status = ? WHERE id = ?", status.name(), classId);
    }

    private void updateEnrolledCount(Long classId, int enrolledCount) {
        jdbcTemplate.update("UPDATE classes SET enrolled_count = ? WHERE id = ?", enrolledCount, classId);
    }

    private Long insertEnrollment(
        Long classId,
        Long userId,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt
    ) {
        jdbcTemplate.update("""
            INSERT INTO enrollments (class_id, user_id, status, requested_at, confirmed_at, cancelled_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            classId,
            userId,
            status.name(),
            FIXED_NOW.minusDays(3),
            confirmedAt,
            cancelledAt
        );

        return jdbcTemplate.queryForObject(
            "SELECT id FROM enrollments WHERE class_id = ? AND user_id = ?",
            Long.class,
            classId,
            userId
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW.atZone(TEST_ZONE).toInstant(), TEST_ZONE);
        }
    }
}
