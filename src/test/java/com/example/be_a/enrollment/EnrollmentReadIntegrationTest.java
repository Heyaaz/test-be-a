package com.example.be_a.enrollment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
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
class EnrollmentReadIntegrationTest extends MySqlTestContainerSupport {

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
    void 내_수강_신청_목록을_조회할_수_있다() throws Exception {
        Long waitingId = insertEnrollment(20L, EnrollmentStatus.WAITING);
        Long pendingId = insertEnrollment(20L, EnrollmentStatus.PENDING);
        Long confirmedId = insertEnrollment(20L, EnrollmentStatus.CONFIRMED);
        Long cancelledId = insertEnrollment(20L, EnrollmentStatus.CANCELLED);
        insertEnrollment(21L, EnrollmentStatus.PENDING);

        mockMvc.perform(get("/api/enrollments/me")
                .header("X-User-Id", "20")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(4))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.items[0].id").value(cancelledId))
            .andExpect(jsonPath("$.items[1].id").value(confirmedId))
            .andExpect(jsonPath("$.items[2].id").value(pendingId))
            .andExpect(jsonPath("$.items[3].id").value(waitingId))
            .andExpect(jsonPath("$.items[0].userId").value(20))
            .andExpect(jsonPath("$.items[1].userId").value(20))
            .andExpect(jsonPath("$.items[2].userId").value(20))
            .andExpect(jsonPath("$.items[3].userId").value(20))
            .andExpect(jsonPath("$.items[3].confirmedAt").doesNotExist())
            .andExpect(jsonPath("$.items[3].cancelledAt").doesNotExist());
    }

    @Test
    void 상태로_내_수강_신청_목록을_필터링할_수_있다() throws Exception {
        insertEnrollment(20L, EnrollmentStatus.WAITING);
        Long pendingId = insertEnrollment(20L, EnrollmentStatus.PENDING);
        insertEnrollment(20L, EnrollmentStatus.CONFIRMED);
        insertEnrollment(21L, EnrollmentStatus.PENDING);

        mockMvc.perform(get("/api/enrollments/me")
                .header("X-User-Id", "20")
                .param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(pendingId))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andExpect(jsonPath("$.items[0].userId").value(20));
    }

    @Test
    void 내_수강_신청_목록은_페이지네이션을_지원한다() throws Exception {
        insertEnrollment(20L, EnrollmentStatus.WAITING);
        insertEnrollment(20L, EnrollmentStatus.PENDING);
        insertEnrollment(20L, EnrollmentStatus.CONFIRMED);

        mockMvc.perform(get("/api/enrollments/me")
                .header("X-User-Id", "20")
                .param("page", "0")
                .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void size가_100을_초과하면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(get("/api/enrollments/me")
                .header("X-User-Id", "20")
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void page가_음수면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(get("/api/enrollments/me")
                .header("X-User-Id", "20")
                .param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 존재하지_않는_상태로_필터링하면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(get("/api/enrollments/me")
                .header("X-User-Id", "20")
                .param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 빈_상태값은_전체_조회로_처리한다() throws Exception {
        Long enrollmentId = insertEnrollment(20L, EnrollmentStatus.PENDING);

        mockMvc.perform(get("/api/enrollments/me")
                .header("X-User-Id", "20")
                .param("status", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].id").value(enrollmentId));
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private Long insertEnrollment(Long userId, EnrollmentStatus status) {
        ClassEntity classEntity = saveOpenClass("내 신청 목록 강의 " + userId + " " + status);
        return insertEnrollment(classEntity.getId(), userId, status);
    }

    private Long insertEnrollment(Long classId, Long userId, EnrollmentStatus status) {
        jdbcTemplate.update("""
            INSERT INTO enrollments (class_id, user_id, status, requested_at, confirmed_at, cancelled_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            classId,
            userId,
            status.name(),
            FIXED_NOW.minusDays(3),
            status == EnrollmentStatus.CONFIRMED ? FIXED_NOW.minusDays(2) : null,
            status == EnrollmentStatus.CANCELLED ? FIXED_NOW.minusDays(1) : null
        );

        return jdbcTemplate.queryForObject(
            "SELECT id FROM enrollments WHERE class_id = ? AND user_id = ?",
            Long.class,
            classId,
            userId
        );
    }

    private ClassEntity saveOpenClass(String title) {
        ClassEntity classEntity = classRepository.save(ClassEntity.createDraft(
            10L,
            title,
            "설명",
            30000,
            30,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        ));
        jdbcTemplate.update("UPDATE classes SET status = ? WHERE id = ?", ClassStatus.OPEN.name(), classEntity.getId());
        return classRepository.findById(classEntity.getId()).orElseThrow();
    }
}
