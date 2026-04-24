package com.example.be_a.enrollment.presentation;

import com.example.be_a.enrollment.application.EnrollmentApplyService;
import com.example.be_a.enrollment.application.EnrollmentCancelService;
import com.example.be_a.enrollment.application.EnrollmentConfirmService;
import com.example.be_a.enrollment.application.EnrollmentReadService;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.config.CurrentUser;
import com.example.be_a.global.support.PageResponse;
import com.example.be_a.user.application.CurrentUserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Enrollments", description = "수강 신청, 결제 확정, 취소, 내 신청 목록 API")
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

    @Operation(summary = "내 수강 신청 목록 조회", description = "요청 사용자의 수강 신청 목록을 최신순으로 조회합니다.")
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = "요청 사용자 ID", example = "2")
    @ApiResponse(responseCode = "200", description = "내 신청 목록 조회 성공")
    @GetMapping("/me")
    public PageResponse<EnrollmentResponse> listMine(
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user,
        @Parameter(description = "수강 신청 상태 필터", example = "PENDING")
        @RequestParam(required = false) EnrollmentStatus status,
        @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
        @Parameter(description = "페이지 크기, 최대 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        return PageResponse.of(enrollmentReadService.listMine(
            user,
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        ).map(EnrollmentResponse::from));
    }

    @Operation(summary = "수강 신청", description = "OPEN 상태 강의에 신청합니다. 정원이 가득 찬 경우 waitlist=true면 WAITING으로 저장하고, false면 CLASS_FULL을 반환합니다.")
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = "요청 사용자 ID", example = "2")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "수강 신청 성공"),
        @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "강의 미오픈, 정원 초과, 이미 활성 신청 존재")
    })
    @PostMapping
    public ResponseEntity<EnrollmentResponse> apply(
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user,
        @Valid @RequestBody ApplyEnrollmentRequest request
    ) {
        EnrollmentResponse response = EnrollmentResponse.from(enrollmentApplyService.apply(user, request.toCommand()));

        return ResponseEntity.created(URI.create("/api/enrollments/" + response.id()))
            .body(response);
    }

    @Operation(summary = "결제 확정", description = "PENDING 상태의 신청을 CONFIRMED로 전환합니다.")
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = "요청 사용자 ID", example = "2")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 확정 성공"),
        @ApiResponse(responseCode = "403", description = "신청 소유자가 아님"),
        @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "허용되지 않는 상태 전이 또는 동시 수정 충돌")
    })
    @PostMapping("/{enrollmentId}/confirm")
    public EnrollmentResponse confirm(
        @Parameter(description = "수강 신청 ID", example = "10")
        @PathVariable Long enrollmentId,
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user
    ) {
        return EnrollmentResponse.from(enrollmentConfirmService.confirm(enrollmentId, user));
    }

    @Operation(summary = "수강 취소", description = "WAITING/PENDING/CONFIRMED 신청을 CANCELLED로 전환합니다. PENDING/CONFIRMED 취소 시 좌석을 복구하고 대기열 승급을 시도합니다.")
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = "요청 사용자 ID", example = "2")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수강 취소 성공"),
        @ApiResponse(responseCode = "403", description = "신청 소유자가 아님"),
        @ApiResponse(responseCode = "404", description = "신청을 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "취소 기간 만료, 허용되지 않는 상태 전이 또는 동시 수정 충돌")
    })
    @PostMapping("/{enrollmentId}/cancel")
    public EnrollmentResponse cancel(
        @Parameter(description = "수강 신청 ID", example = "10")
        @PathVariable Long enrollmentId,
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user
    ) {
        return EnrollmentResponse.from(enrollmentCancelService.cancel(enrollmentId, user));
    }
}
