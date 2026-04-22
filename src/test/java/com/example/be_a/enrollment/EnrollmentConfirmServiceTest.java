package com.example.be_a.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.application.EnrollmentConfirmService;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import com.example.be_a.user.domain.UserRole;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnrollmentConfirmServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private UserAuthorizationService userAuthorizationService;

    @InjectMocks
    private EnrollmentConfirmService enrollmentConfirmService;

    @Test
    void PENDING_상태면_CONFIRMED로_변경한다() {
        EnrollmentEntity enrollment = enrollmentWithStatus(EnrollmentStatus.PENDING);
        CurrentUserInfo user = new CurrentUserInfo(20L, UserRole.STUDENT);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(classRepository.findById(10L)).thenReturn(Optional.of(openClass()));

        EnrollmentEntity confirmed = enrollmentConfirmService.confirm(1L, user);

        assertThat(confirmed.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
    }

    @Test
    void 수강_신청이_없으면_ENROLLMENT_NOT_FOUND를_반환한다() {
        CurrentUserInfo user = new CurrentUserInfo(20L, UserRole.STUDENT);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentConfirmService.confirm(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_수강_신청이면_FORBIDDEN을_반환한다() {
        EnrollmentEntity enrollment = enrollmentWithStatus(EnrollmentStatus.PENDING);
        CurrentUserInfo user = new CurrentUserInfo(21L, UserRole.STUDENT);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        doThrow(new ApiException(ErrorCode.FORBIDDEN))
            .when(userAuthorizationService)
            .requireOwner(user, 20L);

        assertThatThrownBy(() -> enrollmentConfirmService.confirm(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void WAITING_상태면_INVALID_STATE_TRANSITION을_반환한다() {
        EnrollmentEntity enrollment = enrollmentWithStatus(EnrollmentStatus.WAITING);
        CurrentUserInfo user = new CurrentUserInfo(20L, UserRole.STUDENT);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(classRepository.findById(10L)).thenReturn(Optional.of(openClass()));

        assertThatThrownBy(() -> enrollmentConfirmService.confirm(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    void CLOSED_강의면_CLASS_NOT_OPEN을_반환한다() {
        EnrollmentEntity enrollment = enrollmentWithStatus(EnrollmentStatus.PENDING);
        CurrentUserInfo user = new CurrentUserInfo(20L, UserRole.STUDENT);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(classRepository.findById(10L)).thenReturn(Optional.of(closedClass()));

        assertThatThrownBy(() -> enrollmentConfirmService.confirm(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CLASS_NOT_OPEN);
    }

    @Test
    void DRAFT_강의면_CLASS_NOT_OPEN을_반환한다() {
        EnrollmentEntity enrollment = enrollmentWithStatus(EnrollmentStatus.PENDING);
        CurrentUserInfo user = new CurrentUserInfo(20L, UserRole.STUDENT);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(classRepository.findById(10L)).thenReturn(Optional.of(draftClass()));

        assertThatThrownBy(() -> enrollmentConfirmService.confirm(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CLASS_NOT_OPEN);
    }

    private EnrollmentEntity enrollmentWithStatus(EnrollmentStatus status) {
        EnrollmentEntity enrollment = EnrollmentEntity.createPending(10L, 20L);
        ReflectionTestUtils.setField(enrollment, "id", 1L);
        ReflectionTestUtils.setField(enrollment, "status", status);
        return enrollment;
    }

    private ClassEntity openClass() {
        ClassEntity classEntity = ClassEntity.createDraft(
            10L,
            "확정 대상 강의",
            "설명",
            30000,
            30,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        );
        classEntity.changeStatus(ClassStatus.OPEN);
        ReflectionTestUtils.setField(classEntity, "id", 10L);
        return classEntity;
    }

    private ClassEntity closedClass() {
        ClassEntity classEntity = openClass();
        classEntity.changeStatus(ClassStatus.CLOSED);
        return classEntity;
    }

    private ClassEntity draftClass() {
        ClassEntity classEntity = ClassEntity.createDraft(
            10L,
            "확정 대상 강의",
            "설명",
            30000,
            30,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        );
        ReflectionTestUtils.setField(classEntity, "id", 10L);
        return classEntity;
    }
}
