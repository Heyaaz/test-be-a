package com.example.be_a.class_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.support.MySqlTestContainerSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClassStatusChangeIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ensureUser(10L, "creator10@example.com", "Creator Ten", "CREATOR");
        jdbcTemplate.update("DELETE FROM enrollments");
        classRepository.deleteAllInBatch();
    }

    @Test
    void DRAFT에서_OPEN으로_상태를_변경할_수_있다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "상태 전이 강의");

        mockMvc.perform(post("/api/classes/{classId}/status", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetStatus": "OPEN"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(classEntity.getId()))
            .andExpect(jsonPath("$.status").value("OPEN"));

        ClassEntity changedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        assertThat(changedClass.getStatus()).isEqualTo(ClassStatus.OPEN);
    }

    @Test
    void OPEN에서_CLOSED로_상태를_변경할_수_있다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "상태 전이 강의");
        updateStatus(classEntity.getId(), ClassStatus.OPEN);

        mockMvc.perform(post("/api/classes/{classId}/status", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetStatus": "CLOSED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(classEntity.getId()))
            .andExpect(jsonPath("$.status").value("CLOSED"));

        ClassEntity changedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        assertThat(changedClass.getStatus()).isEqualTo(ClassStatus.CLOSED);
    }

    @Test
    void 학생이_강의_상태를_변경하면_CREATOR_ONLY를_반환한다() throws Exception {
        ensureUser(11L, "student11@example.com", "Student Eleven", "STUDENT");
        ClassEntity classEntity = saveClass(10L, "상태 전이 강의");

        mockMvc.perform(post("/api/classes/{classId}/status", classEntity.getId())
                .header("X-User-Id", "11")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetStatus": "OPEN"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CREATOR_ONLY"));
    }

    @Test
    void 다른_크리에이터가_강의_상태를_변경하면_FORBIDDEN을_반환한다() throws Exception {
        ensureUser(20L, "creator20@example.com", "Creator Twenty", "CREATOR");
        ClassEntity classEntity = saveClass(10L, "상태 전이 강의");

        mockMvc.perform(post("/api/classes/{classId}/status", classEntity.getId())
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetStatus": "OPEN"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void DRAFT에서_CLOSED로_상태를_변경하면_INVALID_STATE_TRANSITION을_반환한다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "상태 전이 강의");

        mockMvc.perform(post("/api/classes/{classId}/status", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetStatus": "CLOSED"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void CLOSED에서_OPEN으로_상태를_변경하면_INVALID_STATE_TRANSITION을_반환한다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "상태 전이 강의");
        updateStatus(classEntity.getId(), ClassStatus.CLOSED);

        mockMvc.perform(post("/api/classes/{classId}/status", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetStatus": "OPEN"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void 잘못된_enum_값이면_VALIDATION_ERROR를_반환한다() throws Exception {
        ClassEntity classEntity = saveClass(10L, "상태 전이 강의");

        mockMvc.perform(post("/api/classes/{classId}/status", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetStatus": "OPENED"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
}
