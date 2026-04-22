package com.example.be_a.class_;

import static org.assertj.core.api.Assertions.assertThat;

import org.hamcrest.Matchers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClassCreateIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassRepository classRepository;

    @BeforeEach
    void setUp() {
        classRepository.deleteAllInBatch();
    }

    @Test
    void 크리에이터가_강의를_생성하면_DRAFT_상태로_저장된다() throws Exception {
        mockMvc.perform(post("/api/classes")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Spring Boot 입문",
                      "description": "백엔드 기본기",
                      "price": 30000,
                      "capacity": 30,
                      "startDate": "2026-05-01",
                      "endDate": "2026-05-31"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", Matchers.matchesPattern("/api/classes/\\d+")))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.creatorId").value(1))
            .andExpect(jsonPath("$.title").value("Spring Boot 입문"))
            .andExpect(jsonPath("$.price").value(30000))
            .andExpect(jsonPath("$.capacity").value(30))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        ClassEntity savedClass = classRepository.findAll().get(0);
        assertThat(savedClass.getCreatorId()).isEqualTo(1L);
        assertThat(savedClass.getTitle()).isEqualTo("Spring Boot 입문");
        assertThat(savedClass.getDescription()).isEqualTo("백엔드 기본기");
        assertThat(savedClass.getPrice()).isEqualTo(30000);
        assertThat(savedClass.getCapacity()).isEqualTo(30);
        assertThat(savedClass.getEnrolledCount()).isZero();
        assertThat(savedClass.getStatus()).isEqualTo(ClassStatus.DRAFT);
    }

    @Test
    void 학생이_강의를_생성하면_CREATOR_ONLY를_반환한다() throws Exception {
        mockMvc.perform(post("/api/classes")
                .header("X-User-Id", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CREATOR_ONLY"));
    }

    @Test
    void 제목이_비어_있으면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(post("/api/classes")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": " ",
                      "description": "백엔드 기본기",
                      "price": 30000,
                      "capacity": 30,
                      "startDate": "2026-05-01",
                      "endDate": "2026-05-31"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void 가격이_음수면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(post("/api/classes")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Spring Boot 입문",
                      "description": "백엔드 기본기",
                      "price": -1,
                      "capacity": 30,
                      "startDate": "2026-05-01",
                      "endDate": "2026-05-31"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
    }

    @Test
    void 정원이_0_이하면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(post("/api/classes")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Spring Boot 입문",
                      "description": "백엔드 기본기",
                      "price": 30000,
                      "capacity": 0,
                      "startDate": "2026-05-01",
                      "endDate": "2026-05-31"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("capacity"));
    }

    @Test
    void 종료일이_시작일보다_빠르면_VALIDATION_ERROR를_반환한다() throws Exception {
        mockMvc.perform(post("/api/classes")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Spring Boot 입문",
                      "description": "백엔드 기본기",
                      "price": 30000,
                      "capacity": 30,
                      "startDate": "2026-05-31",
                      "endDate": "2026-05-01"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("endDate"));
    }

    private String validRequest() {
        return """
            {
              "title": "Spring Boot 입문",
              "description": "백엔드 기본기",
              "price": 30000,
              "capacity": 30,
              "startDate": "2026-05-01",
              "endDate": "2026-05-31"
            }
            """;
    }
}
