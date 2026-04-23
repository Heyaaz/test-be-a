package com.example.be_a.enrollment.presentation;

import com.example.be_a.enrollment.application.EnrollmentApplyService;
import com.example.be_a.enrollment.application.EnrollmentCancelService;
import com.example.be_a.enrollment.application.EnrollmentConfirmService;
import com.example.be_a.global.config.CurrentUser;
import com.example.be_a.user.application.CurrentUserInfo;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentApplyService enrollmentApplyService;
    private final EnrollmentConfirmService enrollmentConfirmService;
    private final EnrollmentCancelService enrollmentCancelService;

    public EnrollmentController(
        EnrollmentApplyService enrollmentApplyService,
        EnrollmentConfirmService enrollmentConfirmService,
        EnrollmentCancelService enrollmentCancelService
    ) {
        this.enrollmentApplyService = enrollmentApplyService;
        this.enrollmentConfirmService = enrollmentConfirmService;
        this.enrollmentCancelService = enrollmentCancelService;
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
