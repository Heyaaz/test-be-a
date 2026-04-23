package com.example.be_a.class_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import java.time.LocalDateTime;
import com.example.be_a.support.MySqlTestContainerSupport;
import java.time.LocalDate;
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
class ClassDeleteIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ensureUser(10L, "creator10@example.com", "Creator Ten", "CREATOR");
        ensureUser(20L, "creator20@example.com", "Creator Twenty", "CREATOR");
        ensureUser(30L, "student30@example.com", "Student Thirty", "STUDENT");
        jdbcTemplate.update("DELETE FROM enrollments");
        classRepository.deleteAllInBatch();
    }

    @Test
    void DRAFT_강의는_삭제할_수_있다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "삭제 대상 강의");

        mockMvc.perform(delete("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10"))
            .andExpect(status().isNoContent());

        assertThat(classRepository.findById(classEntity.getId())).isEmpty();
    }

    @Test
    void OPEN_강의는_삭제할_수_없다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "삭제 대상 강의");
        updateStatus(classEntity.getId(), ClassStatus.OPEN);

        mockMvc.perform(delete("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_DRAFT"));
    }

    @Test
    void CLOSED_강의는_삭제할_수_없다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "삭제 대상 강의");
        updateStatus(classEntity.getId(), ClassStatus.CLOSED);

        mockMvc.perform(delete("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_DRAFT"));
    }

    @Test
    void 다른_크리에이터의_강의는_삭제할_수_없다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "삭제 대상 강의");

        mockMvc.perform(delete("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "20"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 학생은_강의를_삭제할_수_없다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "삭제 대상 강의");

        mockMvc.perform(delete("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "30"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CREATOR_ONLY"));
    }

    @Test
    void 존재하지_않는_강의는_CLASS_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/classes/{classId}", 99999L)
                .header("X-User-Id", "10"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void enrollment가_존재하면_강의를_삭제할_수_없다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "삭제 대상 강의");
        insertEnrollment(classEntity.getId(), 30L, EnrollmentStatus.CANCELLED);

        mockMvc.perform(delete("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_DELETE_NOT_ALLOWED"));
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private ClassEntity saveClass(Long creatorId, String title) {
        return classRepository.save(ClassEntity.createDraft(
            creatorId,
            title,
            "설명",
            30000,
            30,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        ));
    }

    private void updateStatus(Long classId, ClassStatus status) {
        jdbcTemplate.update("UPDATE classes SET status = ? WHERE id = ?", status.name(), classId);
    }

    private void insertEnrollment(Long classId, Long userId, EnrollmentStatus status) {
        jdbcTemplate.update("""
            INSERT INTO enrollments (class_id, user_id, status, confirmed_at, cancelled_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            classId,
            userId,
            status.name(),
            status == EnrollmentStatus.CONFIRMED ? LocalDateTime.of(2026, 4, 23, 10, 0) : null,
            status == EnrollmentStatus.CANCELLED ? LocalDateTime.of(2026, 4, 23, 11, 0) : null
        );
    }
}
