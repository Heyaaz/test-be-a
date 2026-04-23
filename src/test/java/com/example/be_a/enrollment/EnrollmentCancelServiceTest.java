package com.example.be_a.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.enrollment.application.EnrollmentCancelService;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.application.UserAuthorizationService;
import com.example.be_a.user.domain.UserRole;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnrollmentCancelServiceTest {

    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 4, 23, 10, 0);

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private UserAuthorizationService userAuthorizationService;

    private EnrollmentCancelService enrollmentCancelService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW.atZone(TEST_ZONE).toInstant(), TEST_ZONE);
        enrollmentCancelService = new EnrollmentCancelService(
            enrollmentRepository,
            classRepository,
            userAuthorizationService,
            fixedClock
        );
    }

    @Test
    void WAITING_상태면_정원_변화_없이_CANCELLED로_변경한다() {
        EnrollmentEntity enrollment = waitingEnrollment();
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        EnrollmentEntity cancelled = enrollmentCancelService.cancel(1L, user);

        assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        verify(classRepository, never()).tryDecrementEnrolled(10L);
    }

    @Test
    void PENDING_상태면_정원을_복구하고_CANCELLED로_변경한다() {
        EnrollmentEntity enrollment = pendingEnrollment();
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(classRepository.tryDecrementEnrolled(10L)).thenReturn(1);

        EnrollmentEntity cancelled = enrollmentCancelService.cancel(1L, user);

        assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        verify(classRepository).tryDecrementEnrolled(10L);
    }

    @Test
    void CONFIRMED_상태고_7일_이내면_취소할_수_있다() {
        EnrollmentEntity enrollment = confirmedEnrollment(FIXED_NOW.minusDays(6));
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(classRepository.tryDecrementEnrolled(10L)).thenReturn(1);

        EnrollmentEntity cancelled = enrollmentCancelService.cancel(1L, user);

        assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        verify(classRepository).tryDecrementEnrolled(10L);
    }

    @Test
    void CONFIRMED_상태고_정확히_7일째면_취소할_수_있다() {
        EnrollmentEntity enrollment = confirmedEnrollment(FIXED_NOW.minusDays(7));
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(classRepository.tryDecrementEnrolled(10L)).thenReturn(1);

        EnrollmentEntity cancelled = enrollmentCancelService.cancel(1L, user);

        assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isEqualTo(FIXED_NOW);
        verify(classRepository).tryDecrementEnrolled(10L);
    }

    @Test
    void CONFIRMED_상태고_7일이_지나면_CANCEL_PERIOD_EXPIRED를_반환한다() {
        EnrollmentEntity enrollment = confirmedEnrollment(FIXED_NOW.minusDays(8));
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentCancelService.cancel(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CANCEL_PERIOD_EXPIRED);

        verify(classRepository, never()).tryDecrementEnrolled(10L);
    }

    @Test
    void 이미_CANCELLED_상태면_INVALID_STATE_TRANSITION을_반환한다() {
        EnrollmentEntity enrollment = cancelledEnrollment();
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentCancelService.cancel(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);

        verify(classRepository, never()).tryDecrementEnrolled(10L);
    }

    @Test
    void 수강_신청이_없으면_ENROLLMENT_NOT_FOUND를_반환한다() {
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentCancelService.cancel(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_수강_신청이면_FORBIDDEN을_반환한다() {
        EnrollmentEntity enrollment = pendingEnrollment();
        CurrentUserInfo user = studentUser(21L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        doThrow(new ApiException(ErrorCode.FORBIDDEN))
            .when(userAuthorizationService)
            .requireOwner(user, 20L);

        assertThatThrownBy(() -> enrollmentCancelService.cancel(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 동시성_충돌이면_CONFLICT_RETRY를_반환한다() {
        EnrollmentEntity enrollment = pendingEnrollment();
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        doThrow(new ObjectOptimisticLockingFailureException(EnrollmentEntity.class, 1L))
            .when(enrollmentRepository)
            .saveAndFlush(enrollment);

        assertThatThrownBy(() -> enrollmentCancelService.cancel(1L, user))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT_RETRY);

        verify(classRepository, never()).tryDecrementEnrolled(10L);
    }

    private CurrentUserInfo studentUser(Long userId) {
        return new CurrentUserInfo(userId, UserRole.STUDENT);
    }

    private EnrollmentEntity waitingEnrollment() {
        EnrollmentEntity enrollment = EnrollmentEntity.createWaiting(10L, 20L);
        ReflectionTestUtils.setField(enrollment, "id", 1L);
        return enrollment;
    }

    private EnrollmentEntity pendingEnrollment() {
        EnrollmentEntity enrollment = EnrollmentEntity.createPending(10L, 20L);
        ReflectionTestUtils.setField(enrollment, "id", 1L);
        return enrollment;
    }

    private EnrollmentEntity confirmedEnrollment(LocalDateTime confirmedAt) {
        EnrollmentEntity enrollment = pendingEnrollment();
        ReflectionTestUtils.setField(enrollment, "status", EnrollmentStatus.CONFIRMED);
        ReflectionTestUtils.setField(enrollment, "confirmedAt", confirmedAt);
        return enrollment;
    }

    private EnrollmentEntity cancelledEnrollment() {
        EnrollmentEntity enrollment = pendingEnrollment();
        ReflectionTestUtils.setField(enrollment, "status", EnrollmentStatus.CANCELLED);
        ReflectionTestUtils.setField(enrollment, "cancelledAt", FIXED_NOW.minusDays(1));
        return enrollment;
    }
}
