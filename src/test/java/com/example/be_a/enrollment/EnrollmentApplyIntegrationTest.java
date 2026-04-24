package com.example.be_a.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.List;
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
class EnrollmentApplyIntegrationTest extends MySqlTestContainerSupport {

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
        ensureUser(22L, "student22@example.com", "Student Twenty Two", "STUDENT");
        enrollmentRepository.deleteAllInBatch();
        classRepository.deleteAllInBatch();
    }

    @Test
    void 정원이_남아_있으면_PENDING_상태로_수강_신청할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 1, "신청 가능한 강의");

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), false)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/enrollments/\\d+")))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.classId").value(classEntity.getId()))
            .andExpect(jsonPath("$.userId").value(20))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.requestedAt").exists());

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollments).hasSize(1);
        assertThat(enrollments.get(0).getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    void 이미_신청한_강의면_ALREADY_ENROLLED를_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 2, "중복 신청 강의");
        apply(classEntity.getId(), 20L, false);

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), false)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollmentRepository.findAll()).hasSize(1);
    }

    @Test
    void CANCELLED_이력이_있는_사용자는_같은_강의를_다시_신청할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 1, "재신청 가능 강의");
        apply(classEntity.getId(), 20L, false);
        cancel(latestEnrollmentId(classEntity.getId(), 20L), 20L);

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), false)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/enrollments/\\d+")))
            .andExpect(jsonPath("$.classId").value(classEntity.getId()))
            .andExpect(jsonPath("$.userId").value(20))
            .andExpect(jsonPath("$.status").value("PENDING"));

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollments).hasSize(2);
        assertThat(enrollments)
            .extracting(EnrollmentEntity::getStatus)
            .containsExactlyInAnyOrder(EnrollmentStatus.CANCELLED, EnrollmentStatus.PENDING);
    }

    @Test
    void WAITING_취소_이력이_있는_사용자는_같은_강의를_다시_신청할_수_있다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 1, "대기 취소 후 재신청 강의");
        apply(classEntity.getId(), 20L, false);
        apply(classEntity.getId(), 21L, true);
        cancel(latestEnrollmentId(classEntity.getId(), 21L), 21L);

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), true)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/enrollments/\\d+")))
            .andExpect(jsonPath("$.classId").value(classEntity.getId()))
            .andExpect(jsonPath("$.userId").value(21))
            .andExpect(jsonPath("$.status").value("WAITING"));

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollments).hasSize(3);
        assertThat(enrollments)
            .extracting(EnrollmentEntity::getStatus)
            .containsExactlyInAnyOrder(
                EnrollmentStatus.PENDING,
                EnrollmentStatus.CANCELLED,
                EnrollmentStatus.WAITING
            );
    }

    @Test
    void 같은_사용자가_여러_번_취소_재신청해도_활성_신청은_하나만_유지된다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 1, "반복 재신청 강의");
        apply(classEntity.getId(), 20L, false);
        cancel(latestEnrollmentId(classEntity.getId(), 20L), 20L);
        apply(classEntity.getId(), 20L, false);
        cancel(latestEnrollmentId(classEntity.getId(), 20L), 20L);

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), false)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollments).hasSize(3);
        assertThat(enrollments)
            .extracting(EnrollmentEntity::getStatus)
            .containsExactlyInAnyOrder(
                EnrollmentStatus.CANCELLED,
                EnrollmentStatus.CANCELLED,
                EnrollmentStatus.PENDING
            );
    }

    @Test
    void DRAFT_강의는_CLASS_NOT_OPEN을_반환한다() throws Exception {
        ClassEntity classEntity = saveDraftClass(10L, 1, "초안 강의");

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), false)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_OPEN"));
    }

    @Test
    void CLOSED_강의는_CLASS_NOT_OPEN을_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 1, "마감 강의");
        updateStatus(classEntity.getId(), ClassStatus.CLOSED);

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), false)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_NOT_OPEN"));
    }

    @Test
    void 정원이_가득_찼고_대기열을_사용하지_않으면_CLASS_FULL을_반환한다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 1, "정원 마감 강의");
        apply(classEntity.getId(), 20L, false);

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), false)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLASS_FULL"));

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollmentRepository.findAll()).hasSize(1);
    }

    @Test
    void 정원이_가득_찼고_대기열을_사용하면_WAITING으로_저장된다() throws Exception {
        ClassEntity classEntity = saveOpenClass(10L, 1, "대기열 강의");
        apply(classEntity.getId(), 20L, false);

        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classEntity.getId(), true)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern("/api/enrollments/\\d+")))
            .andExpect(jsonPath("$.classId").value(classEntity.getId()))
            .andExpect(jsonPath("$.userId").value(21))
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.requestedAt").exists());

        ClassEntity savedClass = classRepository.findById(classEntity.getId()).orElseThrow();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findAll();

        assertThat(savedClass.getEnrolledCount()).isEqualTo(1);
        assertThat(enrollments).hasSize(2);
        assertThat(enrollments)
            .extracting(EnrollmentEntity::getStatus)
            .containsExactlyInAnyOrder(EnrollmentStatus.PENDING, EnrollmentStatus.WAITING);
    }

    @Test
    void classId가_없으면_VALIDATION_ERROR와_fieldErrors를_반환한다() throws Exception {
        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", "20")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "waitlist": true
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("classId"));
    }

    private void apply(Long classId, Long userId, boolean waitlist) throws Exception {
        mockMvc.perform(post("/api/enrollments")
                .header("X-User-Id", String.valueOf(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(classId, waitlist)))
            .andExpect(status().isCreated());
    }

    private void cancel(Long enrollmentId, Long userId) throws Exception {
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", String.valueOf(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private Long latestEnrollmentId(Long classId, Long userId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM enrollments WHERE class_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
            Long.class,
            classId,
            userId
        );
    }

    private String requestBody(Long classId, boolean waitlist) {
        return """
            {
              "classId": %d,
              "waitlist": %s
            }
            """.formatted(classId, waitlist);
    }

    private void ensureUser(Long id, String email, String name, String role) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, name, role)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE email = VALUES(email), name = VALUES(name), role = VALUES(role)
            """, id, email, name, role);
    }

    private ClassEntity saveDraftClass(Long creatorId, int capacity, String title) {
        return classRepository.save(ClassEntity.createDraft(
            creatorId,
            title,
            "설명",
            30000,
            capacity,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        ));
    }

    private ClassEntity saveOpenClass(Long creatorId, int capacity, String title) {
        ClassEntity classEntity = saveDraftClass(creatorId, capacity, title);
        updateStatus(classEntity.getId(), ClassStatus.OPEN);
        return classRepository.findById(classEntity.getId()).orElseThrow();
    }

    private void updateStatus(Long classId, ClassStatus status) {
        jdbcTemplate.update("UPDATE classes SET status = ? WHERE id = ?", status.name(), classId);
    }
}
