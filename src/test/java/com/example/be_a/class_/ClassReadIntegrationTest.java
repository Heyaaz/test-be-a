package com.example.be_a.class_;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.support.MySqlTestContainerSupport;
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
class ClassReadIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ensureUser(10L, "creator10@example.com", "Creator Ten", "CREATOR");
        ensureUser(11L, "student11@example.com", "Student Eleven", "STUDENT");
        ensureUser(12L, "student12@example.com", "Student Twelve", "STUDENT");
        jdbcTemplate.update("DELETE FROM enrollments");
        classRepository.deleteAllInBatch();
    }

    @Test
    void 강의_목록을_조회할_수_있다() throws Exception {
        ClassEntity firstClass = saveClass("Spring Boot 입문");
        ClassEntity secondClass = saveClass("JPA 심화");
        updateEnrolledCount(firstClass.getId(), 2);
        insertWaitingEnrollment(firstClass.getId(), 11L);
        insertWaitingEnrollment(firstClass.getId(), 12L);

        mockMvc.perform(get("/api/classes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.items[0].id").value(secondClass.getId()))
            .andExpect(jsonPath("$.items[0].title").value("JPA 심화"))
            .andExpect(jsonPath("$.items[1].id").value(firstClass.getId()))
            .andExpect(jsonPath("$.items[1].enrolledCount").value(2))
            .andExpect(jsonPath("$.items[1].waitingCount").value(2))
            .andExpect(jsonPath("$.items[1].status").value("DRAFT"));
    }

    @Test
    void 상태로_필터링한_강의_목록을_조회할_수_있다() throws Exception {
        ClassEntity openClass = saveClass("Open 강의");
        saveClass("Draft 강의");
        updateStatus(openClass.getId(), ClassStatus.OPEN);

        mockMvc.perform(get("/api/classes").param("status", "OPEN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(openClass.getId()))
            .andExpect(jsonPath("$.items[0].status").value("OPEN"));
    }

    @Test
    void 페이지와_사이즈를_적용한_강의_목록을_조회할_수_있다() throws Exception {
        saveClass("강의 1");
        ClassEntity secondClass = saveClass("강의 2");
        saveClass("강의 3");

        mockMvc.perform(get("/api/classes")
                .param("page", "1")
                .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.items[0].id").value(secondClass.getId()));
    }

    @Test
    void 사이즈가_100을_초과하면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(get("/api/classes").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 페이지가_음수면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(get("/api/classes").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 사이즈가_0이면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(get("/api/classes").param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }


    @Test
    void 강의_상세를_조회할_수_있다() throws Exception {
        ClassEntity classEntity = saveClass("Spring Boot 입문");
        updateEnrolledCount(classEntity.getId(), 3);
        updateStatus(classEntity.getId(), ClassStatus.OPEN);
        insertWaitingEnrollment(classEntity.getId(), 11L);
        insertWaitingEnrollment(classEntity.getId(), 12L);

        mockMvc.perform(get("/api/classes/{classId}", classEntity.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(classEntity.getId()))
            .andExpect(jsonPath("$.creatorId").value(10))
            .andExpect(jsonPath("$.title").value("Spring Boot 입문"))
            .andExpect(jsonPath("$.description").value("백엔드 기본기"))
            .andExpect(jsonPath("$.price").value(30000))
            .andExpect(jsonPath("$.capacity").value(30))
            .andExpect(jsonPath("$.enrolledCount").value(3))
            .andExpect(jsonPath("$.waitingCount").value(2))
            .andExpect(jsonPath("$.startDate").value("2026-05-01"))
            .andExpect(jsonPath("$.endDate").value("2026-05-31"))
            .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void 존재하지_않는_강의면_CLASS_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/api/classes/{classId}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private ClassEntity saveClass(String title) {
        return classRepository.save(ClassEntity.createDraft(
            10L,
            title,
            "백엔드 기본기",
            30000,
            30,
            java.time.LocalDate.of(2026, 5, 1),
            java.time.LocalDate.of(2026, 5, 31)
        ));
    }

    private void updateStatus(Long classId, ClassStatus status) {
        jdbcTemplate.update("UPDATE classes SET status = ? WHERE id = ?", status.name(), classId);
    }

    private void updateEnrolledCount(Long classId, int enrolledCount) {
        jdbcTemplate.update("UPDATE classes SET enrolled_count = ? WHERE id = ?", enrolledCount, classId);
    }

    private void insertWaitingEnrollment(Long classId, Long userId) {
        jdbcTemplate.update(
            "INSERT INTO enrollments (class_id, user_id, status) VALUES (?, ?, ?)",
            classId,
            userId,
            "WAITING"
        );
    }
}
