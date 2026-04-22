package com.example.be_a.class_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ClassUpdateIntegrationTest extends MySqlTestContainerSupport {

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
        jdbcTemplate.update("DELETE FROM enrollments");
        classRepository.deleteAllInBatch();
    }

    @Test
    void DRAFT_강의는_전체_필드를_수정할_수_있다() throws Exception {
        ClassEntity classEntity = saveDraftClass(10L, "기존 제목");

        mockMvc.perform(patch("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "수정된 제목",
                      "description": "수정된 설명",
                      "price": 45000,
                      "capacity": 40,
                      "startDate": "2026-06-01",
                      "endDate": "2026-06-30"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(classEntity.getId()))
            .andExpect(jsonPath("$.title").value("수정된 제목"))
            .andExpect(jsonPath("$.description").value("수정된 설명"))
            .andExpect(jsonPath("$.price").value(45000))
            .andExpect(jsonPath("$.capacity").value(40))
            .andExpect(jsonPath("$.startDate").value("2026-06-01"))
            .andExpect(jsonPath("$.endDate").value("2026-06-30"))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        ClassEntity updatedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        assertThat(updatedClass.getTitle()).isEqualTo("수정된 제목");
        assertThat(updatedClass.getDescription()).isEqualTo("수정된 설명");
        assertThat(updatedClass.getPrice()).isEqualTo(45000);
        assertThat(updatedClass.getCapacity()).isEqualTo(40);
        assertThat(updatedClass.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(updatedClass.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void 요청에_없는_필드는_기존_값을_유지한다() throws Exception {
        ClassEntity classEntity = saveDraftClass(10L, "기존 제목");

        mockMvc.perform(patch("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "부분 수정 제목"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("부분 수정 제목"))
            .andExpect(jsonPath("$.description").value("기존 설명"))
            .andExpect(jsonPath("$.price").value(30000))
            .andExpect(jsonPath("$.capacity").value(30))
            .andExpect(jsonPath("$.startDate").value("2026-05-01"))
            .andExpect(jsonPath("$.endDate").value("2026-05-31"));

        ClassEntity updatedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        assertThat(updatedClass.getTitle()).isEqualTo("부분 수정 제목");
        assertThat(updatedClass.getDescription()).isEqualTo("기존 설명");
        assertThat(updatedClass.getPrice()).isEqualTo(30000);
        assertThat(updatedClass.getCapacity()).isEqualTo(30);
        assertThat(updatedClass.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(updatedClass.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void OPEN_강의도_전체_필드를_수정할_수_있다() throws Exception {
        ClassEntity classEntity = saveDraftClass(10L, "기존 제목");
        updateStatus(classEntity.getId(), ClassStatus.OPEN);

        mockMvc.perform(patch("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "열린 강의 제목",
                      "description": "열린 강의 설명",
                      "price": 50000,
                      "capacity": 50,
                      "startDate": "2026-06-10",
                      "endDate": "2026-06-20"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("열린 강의 제목"))
            .andExpect(jsonPath("$.description").value("열린 강의 설명"))
            .andExpect(jsonPath("$.price").value(50000))
            .andExpect(jsonPath("$.capacity").value(50))
            .andExpect(jsonPath("$.startDate").value("2026-06-10"))
            .andExpect(jsonPath("$.endDate").value("2026-06-20"))
            .andExpect(jsonPath("$.status").value("OPEN"));

        ClassEntity updatedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        assertThat(updatedClass.getTitle()).isEqualTo("열린 강의 제목");
        assertThat(updatedClass.getDescription()).isEqualTo("열린 강의 설명");
        assertThat(updatedClass.getPrice()).isEqualTo(50000);
        assertThat(updatedClass.getCapacity()).isEqualTo(50);
        assertThat(updatedClass.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(updatedClass.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 20));
    }

    @Test
    void 현재_신청_인원보다_작은_정원으로는_수정할_수_없다() throws Exception {
        ClassEntity classEntity = saveDraftClass(10L, "기존 제목");
        updateStatus(classEntity.getId(), ClassStatus.OPEN);
        updateEnrolledCount(classEntity.getId(), 3);

        mockMvc.perform(patch("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "capacity": 2
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("capacity"));
    }

    @Test
    void CLOSED_강의는_수정할_수_없다() throws Exception {
        ClassEntity classEntity = saveDraftClass(10L, "기존 제목");
        updateStatus(classEntity.getId(), ClassStatus.CLOSED);

        mockMvc.perform(patch("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "수정 시도"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_UPDATE_NOT_ALLOWED"));
    }

    @Test
    void 다른_크리에이터의_강의는_수정할_수_없다() throws Exception {
        ClassEntity classEntity = saveDraftClass(10L, "기존 제목");

        mockMvc.perform(patch("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "수정 시도"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void null을_입력하면_VALIDATION_ERROR를_반환한다() throws Exception {
        ClassEntity classEntity = saveDraftClass(10L, "기존 제목");

        mockMvc.perform(patch("/api/classes/{classId}", classEntity.getId())
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private ClassEntity saveDraftClass(Long creatorId, String title) {
        return classRepository.save(ClassEntity.createDraft(
            creatorId,
            title,
            "기존 설명",
            30000,
            30,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        ));
    }

    private void updateStatus(Long classId, ClassStatus status) {
        jdbcTemplate.update("UPDATE classes SET status = ? WHERE id = ?", status.name(), classId);
    }

    private void updateEnrolledCount(Long classId, int enrolledCount) {
        jdbcTemplate.update("UPDATE classes SET enrolled_count = ? WHERE id = ?", enrolledCount, classId);
    }
}
