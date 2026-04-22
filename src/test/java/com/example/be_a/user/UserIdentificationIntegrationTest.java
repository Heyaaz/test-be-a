package com.example.be_a.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.be_a.global.config.CurrentUser;
import com.example.be_a.support.MySqlTestContainerSupport;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserIdentificationIntegrationTest.TestUserController.class)
class UserIdentificationIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 헤더가_없으면_MISSING_USER_ID를_반환한다() throws Exception {
        mockMvc.perform(get("/test/users/me"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MISSING_USER_ID"))
            .andExpect(jsonPath("$.message").value("X-User-Id 헤더가 필요합니다."));
    }

    @Test
    void 헤더가_숫자가_아니면_INVALID_USER_ID를_반환한다() throws Exception {
        mockMvc.perform(get("/test/users/me").header("X-User-Id", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_USER_ID"))
            .andExpect(jsonPath("$.message").value("X-User-Id 헤더 형식이 올바르지 않습니다."));
    }

    @Test
    void 존재하지_않는_사용자면_USER_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/test/users/me").header("X-User-Id", "999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
    }

    @Test
    void 학생이_크리에이터_전용_API에_접근하면_CREATOR_ONLY를_반환한다() throws Exception {
        mockMvc.perform(post("/test/users/creator-only").header("X-User-Id", "2"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CREATOR_ONLY"))
            .andExpect(jsonPath("$.message").value("크리에이터만 접근할 수 있습니다."));
    }

    @Test
    void 요청_사용자가_리소스_소유자가_아니면_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(post("/test/users/ownership/3").header("X-User-Id", "1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    void 크리에이터면_크리에이터_전용_API에_접근할_수_있다() throws Exception {
        mockMvc.perform(post("/test/users/creator-only").header("X-User-Id", "1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void 요청_사용자가_리소스_소유자면_접근할_수_있다() throws Exception {
        mockMvc.perform(post("/test/users/ownership/1").header("X-User-Id", "1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void 헤더가_정상이면_현재_사용자_정보를_주입한다() throws Exception {
        mockMvc.perform(get("/test/users/me").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.role").value("CREATOR"));
    }

    @RestController
    @RequestMapping("/test/users")
    static class TestUserController {

        private final UserAuthorizationService userAuthorizationService;

        TestUserController(UserAuthorizationService userAuthorizationService) {
            this.userAuthorizationService = userAuthorizationService;
        }

        @GetMapping("/me")
        ResponseEntity<UserSummary> me(@CurrentUser CurrentUserInfo user) {
            return ResponseEntity.ok(new UserSummary(user.id(), user.role().name()));
        }

        @PostMapping("/creator-only")
        ResponseEntity<Void> creatorOnly(@CurrentUser CurrentUserInfo user) {
            userAuthorizationService.requireCreator(user);
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/ownership/{ownerId}")
        ResponseEntity<Void> ownership(@CurrentUser CurrentUserInfo user, @PathVariable Long ownerId) {
            userAuthorizationService.requireOwner(user, ownerId);
            return ResponseEntity.noContent().build();
        }
    }

    record UserSummary(Long id, String role) {
    }
}
