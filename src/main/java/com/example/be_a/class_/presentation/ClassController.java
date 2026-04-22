package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.ClassCreateService;
import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.global.config.CurrentUser;
import com.example.be_a.user.application.CurrentUserInfo;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassCreateService classCreateService;

    public ClassController(ClassCreateService classCreateService) {
        this.classCreateService = classCreateService;
    }

    @PostMapping
    public ResponseEntity<CreateClassResponse> create(
        @CurrentUser CurrentUserInfo user,
        @Valid @RequestBody CreateClassRequest request
    ) {
        ClassEntity createdClass = classCreateService.create(user, request.toCommand());
        CreateClassResponse response = CreateClassResponse.from(createdClass);

        return ResponseEntity.created(URI.create("/api/classes/" + response.id()))
            .body(response);
    }
}
