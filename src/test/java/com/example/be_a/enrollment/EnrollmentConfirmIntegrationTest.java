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
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnrollmentConfirmIntegrationTest extends MySqlTestContainerSupport {

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
    void PENDING_수강_신청은_결제_확정할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.PENDING);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirm", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(enrollmentId))
            .andExpect(jsonPath("$.classId").value(classEntity.getId()))
            .andExpect(jsonPath("$.userId").value(20))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.confirmedAt").exists());

        EnrollmentEntity enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(enrollment.getConfirmedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_수강_신청은_ENROLLMENT_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirm", 99999L)
                .header("X-User-Id", "20"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    @Test
    void WAITING_수강_신청은_결제_확정할_수_없다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.WAITING);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirm", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void CANCELLED_수강_신청은_결제_확정할_수_없다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.CANCELLED);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirm", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void 다른_사용자의_수강_신청은_결제_확정할_수_없다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.PENDING);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirm", enrollmentId)
                .header("X-User-Id", "21"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void CLOSED_강의의_수강_신청은_결제_확정할_수_없다() throws Exception {
        ClassEntity classEntity = saveOpenClass();
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.PENDING);
        updateClassStatus(classEntity.getId(), ClassStatus.CLOSED);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirm", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_OPEN"));
    }

    @Test
    void DRAFT_강의의_수강_신청은_결제_확정할_수_없다() throws Exception {
        ClassEntity classEntity = saveDraftClass();
        Long enrollmentId = insertEnrollment(classEntity.getId(), 20L, EnrollmentStatus.PENDING);

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/confirm", enrollmentId)
                .header("X-User-Id", "20"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_OPEN"));
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private ClassEntity saveOpenClass() {
        ClassEntity classEntity = saveDraftClass();
        updateClassStatus(classEntity.getId(), ClassStatus.OPEN);
        return classRepository.findById(classEntity.getId()).orElseThrow();
    }

    private ClassEntity saveDraftClass() {
        return classRepository.save(ClassEntity.createDraft(
            10L,
            "결제 확정 강의",
            "설명",
            30000,
            30,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        ));
    }

    private void updateClassStatus(Long classId, ClassStatus status) {
        jdbcTemplate.update("UPDATE classes SET status = ? WHERE id = ?", status.name(), classId);
    }

    private Long insertEnrollment(Long classId, Long userId, EnrollmentStatus status) {
        jdbcTemplate.update("""
            INSERT INTO enrollments (class_id, user_id, status, requested_at, cancelled_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            classId,
            userId,
            status.name(),
            LocalDateTime.of(2026, 4, 23, 10, 0),
            status == EnrollmentStatus.CANCELLED ? LocalDateTime.of(2026, 4, 23, 11, 0) : null
        );

        return jdbcTemplate.queryForObject(
            "SELECT id FROM enrollments WHERE class_id = ? AND user_id = ?",
            Long.class,
            classId,
            userId
        );
    }
}
