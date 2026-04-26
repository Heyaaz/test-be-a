package com.example.be_a.class_.presentation;

import com.example.be_a.class_.application.ClassCreateService;
import com.example.be_a.class_.application.ClassDeleteService;
import com.example.be_a.class_.application.ClassEnrollmentReadService;
import com.example.be_a.class_.application.ClassReadService;
import com.example.be_a.class_.application.ClassStatusChangeService;
import com.example.be_a.class_.application.ClassUpdateService;
import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.application.ClassEnrollmentSummaryView;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "Classes", description = "강의 생성, 조회, 수정, 삭제, 상태 전환 API")
public class ClassController {

    private final ClassCreateService classCreateService;
    private final ClassDeleteService classDeleteService;
    private final ClassEnrollmentReadService classEnrollmentReadService;
    private final ClassReadService classReadService;
    private final ClassUpdateService classUpdateService;
    private final ClassStatusChangeService classStatusChangeService;

    public ClassController(
        ClassCreateService classCreateService,
        ClassDeleteService classDeleteService,
        ClassEnrollmentReadService classEnrollmentReadService,
        ClassReadService classReadService,
        ClassUpdateService classUpdateService,
        ClassStatusChangeService classStatusChangeService
    ) {
        this.classCreateService = classCreateService;
        this.classDeleteService = classDeleteService;
        this.classEnrollmentReadService = classEnrollmentReadService;
        this.classReadService = classReadService;
        this.classUpdateService = classUpdateService;
        this.classStatusChangeService = classStatusChangeService;
    }

    @Operation(summary = "강의 목록 조회", description = "상태 필터와 페이지네이션으로 강의 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "강의 목록 조회 성공")
    @GetMapping
    public PageResponse<ClassSummaryResponse> list(
        @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
        @Parameter(description = "페이지 크기, 최대 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size는 100 이하여야 합니다.") int size,
        @Parameter(description = "강의 상태 필터", example = "OPEN")
        @RequestParam(required = false) ClassStatus status
    ) {
        return PageResponse.of(classReadService.list(
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        ).map(ClassSummaryResponse::from));
    }

    @Operation(summary = "강의 상세 조회", description = "강의 상세 정보와 현재 수강 인원, 대기열 수를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "강의 상세 조회 성공"),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음")
    })
    @GetMapping("/{classId}")
    public ClassDetailResponse get(
        @Parameter(description = "강의 ID", example = "1")
        @PathVariable Long classId
    ) {
        return ClassDetailResponse.from(classReadService.get(classId));
    }

    @Operation(summary = "크리에이터 수강생 목록 조회", description = "해당 강의 크리에이터만 수강생 목록을 조회할 수 있습니다. 취소 후 재신청 이력이 있으면 사용자별 최신 신청 1건만 노출합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수강생 목록 조회 성공"),
        @ApiResponse(responseCode = "403", description = "강의 크리에이터가 아님"),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음")
    })
    @GetMapping("/{classId}/enrollments")
    public PageResponse<ClassEnrollmentSummaryView> listEnrollments(
        @Parameter(description = "강의 ID", example = "1")
        @PathVariable Long classId,
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user,
        @Parameter(description = "수강 신청 상태 필터", example = "CONFIRMED")
        @RequestParam(required = false) EnrollmentStatus status,
        @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") int page,
        @Parameter(description = "페이지 크기, 최대 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        return PageResponse.of(classEnrollmentReadService.listByClass(
            classId,
            user,
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "requestedAt").and(Sort.by(Sort.Direction.ASC, "id")))
        ));
    }

    @Operation(summary = "강의 수정", description = "강의 소유 크리에이터가 DRAFT 또는 OPEN 상태의 강의를 부분 수정합니다.")
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = "요청 사용자 ID", example = "1")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "강의 수정 성공"),
        @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
        @ApiResponse(responseCode = "403", description = "강의 소유자가 아님"),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "CLOSED 상태 수정 또는 정원 축소 제한 위반")
    })
    @PatchMapping("/{classId}")
    public ClassDetailResponse update(
        @Parameter(description = "강의 ID", example = "1")
        @PathVariable Long classId,
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user,
        @Valid @RequestBody UpdateClassRequest request
    ) {
        return ClassDetailResponse.from(classUpdateService.update(classId, user, request.toCommand()));
    }

    @Operation(summary = "강의 삭제", description = "신청 이력이 없는 강의만 소유 크리에이터가 삭제할 수 있습니다.")
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = "요청 사용자 ID", example = "1")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "강의 삭제 성공"),
        @ApiResponse(responseCode = "403", description = "강의 소유자가 아님"),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "신청 이력이 있어 삭제 불가")
    })
    @DeleteMapping("/{classId}")
    public ResponseEntity<Void> delete(
        @Parameter(description = "강의 ID", example = "1")
        @PathVariable Long classId,
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user
    ) {
        classDeleteService.delete(classId, user);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "강의 상태 전환", description = "강의 소유 크리에이터가 DRAFT→OPEN, OPEN→CLOSED 상태 전이를 수행합니다.")
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = "요청 사용자 ID", example = "1")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "상태 전환 성공"),
        @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
        @ApiResponse(responseCode = "403", description = "강의 소유자가 아님"),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음"),
        @ApiResponse(responseCode = "409", description = "허용되지 않는 상태 전이")
    })
    @PostMapping("/{classId}/status")
    public ChangeClassStatusResponse changeStatus(
        @Parameter(description = "강의 ID", example = "1")
        @PathVariable Long classId,
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user,
        @Valid @RequestBody ChangeClassStatusRequest request
    ) {
        return ChangeClassStatusResponse.from(
            classStatusChangeService.changeStatus(classId, user, request.targetStatus())
        );
    }

    @Operation(summary = "강의 생성", description = "크리에이터가 DRAFT 상태의 강의를 생성합니다.")
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = "요청 사용자 ID", example = "1")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "강의 생성 성공"),
        @ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
        @ApiResponse(responseCode = "403", description = "크리에이터 권한 없음")
    })
    @PostMapping
    public ResponseEntity<CreateClassResponse> create(
        @Parameter(hidden = true)
        @CurrentUser CurrentUserInfo user,
        @Valid @RequestBody CreateClassRequest request
    ) {
        ClassEntity createdClass = classCreateService.create(user, request.toCommand());
        CreateClassResponse response = CreateClassResponse.from(createdClass);

        return ResponseEntity.created(URI.create("/api/classes/" + response.id()))
            .body(response);
    }
}
