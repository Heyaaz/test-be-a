package com.example.be_a.enrollment.presentation;

import com.example.be_a.enrollment.application.EnrollmentApplyService;
import com.example.be_a.enrollment.application.EnrollmentCancelService;
import com.example.be_a.enrollment.application.EnrollmentConfirmService;
import com.example.be_a.enrollment.application.EnrollmentReadService;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentApplyService enrollmentApplyService;
    private final EnrollmentConfirmService enrollmentConfirmService;
    private final EnrollmentCancelService enrollmentCancelService;
    private final EnrollmentReadService enrollmentReadService;

    public EnrollmentController(
        EnrollmentApplyService enrollmentApplyService,
        EnrollmentConfirmService enrollmentConfirmService,
        EnrollmentCancelService enrollmentCancelService,
        EnrollmentReadService enrollmentReadService
    ) {
        this.enrollmentApplyService = enrollmentApplyService;
        this.enrollmentConfirmService = enrollmentConfirmService;
        this.enrollmentCancelService = enrollmentCancelService;
        this.enrollmentReadService = enrollmentReadService;
    }

    @GetMapping("/me")
    public PageResponse<EnrollmentResponse> listMine(
        @CurrentUser CurrentUserInfo user,
        @RequestParam(required = false) EnrollmentStatus status,
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        return PageResponse.of(enrollmentReadService.listMine(
            user,
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        ).map(EnrollmentResponse::from));
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> apply(
        @CurrentUser CurrentUserInfo user,
        @Valid @RequestBody ApplyEnrollmentRequest request
    ) {
        EnrollmentResponse response = EnrollmentResponse.from(enrollmentApplyService.apply(user, request.toCommand()));

        return ResponseEntity.created(URI.create("/api/enrollments/" + response.id()))
            .body(response);
    }

    @PostMapping("/{enrollmentId}/confirm")
    public EnrollmentResponse confirm(
        @PathVariable Long enrollmentId,
        @CurrentUser CurrentUserInfo user
    ) {
        return EnrollmentResponse.from(enrollmentConfirmService.confirm(enrollmentId, user));
    }

    @PostMapping("/{enrollmentId}/cancel")
    public EnrollmentResponse cancel(
        @PathVariable Long enrollmentId,
        @CurrentUser CurrentUserInfo user
    ) {
        return EnrollmentResponse.from(enrollmentCancelService.cancel(enrollmentId, user));
    }
}
