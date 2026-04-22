package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.ClassCreateService;
import com.example.be_a.class_.application.ClassReadService;
import com.example.be_a.class_.application.ClassStatusChangeService;
import com.example.be_a.class_.application.ClassUpdateService;
import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.global.config.CurrentUser;
import com.example.be_a.global.support.PageResponse;
import com.example.be_a.user.application.CurrentUserInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassCreateService classCreateService;
    private final ClassReadService classReadService;
    private final ClassUpdateService classUpdateService;
    private final ClassStatusChangeService classStatusChangeService;

    public ClassController(
        ClassCreateService classCreateService,
        ClassReadService classReadService,
        ClassUpdateService classUpdateService,
        ClassStatusChangeService classStatusChangeService
    ) {
        this.classCreateService = classCreateService;
        this.classReadService = classReadService;
        this.classUpdateService = classUpdateService;
        this.classStatusChangeService = classStatusChangeService;
    }

    @GetMapping
    public PageResponse<ClassSummaryResponse> list(
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size는 100 이하여야 합니다.") int size,
        @RequestParam(required = false) ClassStatus status
    ) {
        return PageResponse.of(classReadService.list(
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        ).map(ClassSummaryResponse::from));
    }

    @GetMapping("/{classId}")
    public ClassDetailResponse get(@PathVariable Long classId) {
        return ClassDetailResponse.from(classReadService.get(classId));
    }

    @PatchMapping("/{classId}")
    public ClassDetailResponse update(
        @PathVariable Long classId,
        @CurrentUser CurrentUserInfo user,
        @Valid @RequestBody UpdateClassRequest request
    ) {
        return ClassDetailResponse.from(classUpdateService.update(classId, user, request.toCommand()));
    }

    @PostMapping("/{classId}/status")
    public ChangeClassStatusResponse changeStatus(
        @PathVariable Long classId,
        @CurrentUser CurrentUserInfo user,
        @Valid @RequestBody ChangeClassStatusRequest request
    ) {
        return ChangeClassStatusResponse.from(
            classStatusChangeService.changeStatus(classId, user, request.targetStatus())
        );
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
