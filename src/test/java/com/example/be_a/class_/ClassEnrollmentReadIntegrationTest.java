package com.example.be_a.class_;

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
class ClassEnrollmentReadIntegrationTest extends MySqlTestContainerSupport {

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
        ensureUser(20L, "creator20@example.com", "Creator Twenty", "CREATOR");
        ensureUser(30L, "student30@example.com", "Student Thirty", "STUDENT");
        ensureUser(31L, "student31@example.com", "Student Thirty One", "STUDENT");
        ensureUser(32L, "student32@example.com", "Student Thirty Two", "STUDENT");
        enrollmentRepository.deleteAllInBatch();
        classRepository.deleteAllInBatch();
    }

    @Test
    void 본인_강의의_수강생_목록을_조회할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, "크리에이터 강의");
        Long waitingId = insertEnrollmentAt(classEntity.getId(), 30L, EnrollmentStatus.WAITING, FIXED_NOW.minusMinutes(3));
        Long confirmedId = insertEnrollmentAt(classEntity.getId(), 31L, EnrollmentStatus.CONFIRMED, FIXED_NOW.minusMinutes(2));
        Long cancelledId = insertEnrollmentAt(classEntity.getId(), 32L, EnrollmentStatus.CANCELLED, FIXED_NOW.minusMinutes(1));

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].enrollmentId").value(waitingId))
            .andExpect(jsonPath("$.items[0].userId").value(30))
            .andExpect(jsonPath("$.items[0].userName").value("Student Thirty"))
            .andExpect(jsonPath("$.items[0].status").value("WAITING"))
            .andExpect(jsonPath("$.items[0].confirmedAt").doesNotExist())
            .andExpect(jsonPath("$.items[0].cancelledAt").doesNotExist())
            .andExpect(jsonPath("$.items[1].enrollmentId").value(confirmedId))
            .andExpect(jsonPath("$.items[1].userName").value("Student Thirty One"))
            .andExpect(jsonPath("$.items[1].confirmedAt").exists())
            .andExpect(jsonPath("$.items[2].enrollmentId").value(cancelledId))
            .andExpect(jsonPath("$.items[2].userName").value("Student Thirty Two"))
            .andExpect(jsonPath("$.items[2].cancelledAt").exists());
    }

    @Test
    void 같은_사용자가_취소_후_재신청하면_최신_수강_신청만_목록에_노출한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, "재신청 목록 강의");
        insertEnrollmentAt(classEntity.getId(), 30L, EnrollmentStatus.CANCELLED, FIXED_NOW.minusMinutes(2));
        Long reappliedId = insertEnrollmentAt(
            classEntity.getId(),
            30L,
            EnrollmentStatus.PENDING,
            FIXED_NOW.minusMinutes(1)
        );

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].enrollmentId").value(reappliedId))
            .andExpect(jsonPath("$.items[0].userId").value(30))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"));

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10")
                .param("status", "CANCELLED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10")
                .param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].enrollmentId").value(reappliedId));
    }

    @Test
    void 다른_크리에이터_강의는_FORBIDDEN을_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(20L, "다른 사람 강의");

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 학생은_CREATOR_ONLY를_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, "학생 접근 강의");

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "30"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CREATOR_ONLY"));
    }

    @Test
    void 존재하지_않는_강의면_CLASS_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/api/classes/{classId}/enrollments", 99999L)
                .header("X-User-Id", "10"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void 상태로_수강생_목록을_필터링할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, "필터 강의");
        Long pendingId = insertEnrollmentAt(classEntity.getId(), 30L, EnrollmentStatus.PENDING, FIXED_NOW.minusMinutes(2));
        insertEnrollmentAt(classEntity.getId(), 31L, EnrollmentStatus.CONFIRMED, FIXED_NOW.minusMinutes(1));

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10")
                .param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].enrollmentId").value(pendingId))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void 수강생_목록은_페이지네이션을_지원한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, "페이지 강의");
        insertEnrollmentAt(classEntity.getId(), 30L, EnrollmentStatus.WAITING, FIXED_NOW.minusMinutes(3));
        Long secondId = insertEnrollmentAt(classEntity.getId(), 31L, EnrollmentStatus.PENDING, FIXED_NOW.minusMinutes(2));
        insertEnrollmentAt(classEntity.getId(), 32L, EnrollmentStatus.CONFIRMED, FIXED_NOW.minusMinutes(1));

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10")
                .param("page", "1")
                .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.items[0].enrollmentId").value(secondId));
    }

    @Test
    void size가_100을_초과하면_VALIDATION_ERROR를_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, "검증 강의");

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10")
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void page가_음수면_VALIDATION_ERROR를_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, "검증 강의");

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10")
                .param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 존재하지_않는_상태로_필터링하면_VALIDATION_ERROR를_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, "검증 강의");

        mockMvc.perform(get("/api/classes/{classId}/enrollments", classEntity.getId())
                .header("X-User-Id", "10")
                .param("status", "UNKNOWN"))
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

    private ClassEntity saveOpenClass(Long creatorId, String title) {
        ClassEntity classEntity = classRepository.save(ClassEntity.createDraft(
            creatorId,
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

    private Long insertEnrollmentAt(Long classId, Long userId, EnrollmentStatus status, LocalDateTime requestedAt) {
        jdbcTemplate.update("""
            INSERT INTO enrollments (class_id, user_id, status, requested_at, confirmed_at, cancelled_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            classId,
            userId,
            status.name(),
            requestedAt,
            status == EnrollmentStatus.CONFIRMED ? FIXED_NOW.minusMinutes(1) : null,
            status == EnrollmentStatus.CANCELLED ? FIXED_NOW : null
        );

        return jdbcTemplate.queryForObject(
            "SELECT id FROM enrollments WHERE class_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
            Long.class,
            classId,
            userId
        );
    }
}
