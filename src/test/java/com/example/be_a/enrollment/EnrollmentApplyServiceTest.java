package com.example.be_a.enrollment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.be_a.class_.domain.ClassEntity;
import com.example.be_a.class_.domain.ClassRepository;
import com.example.be_a.class_.domain.ClassStatus;
import com.example.be_a.enrollment.application.ApplyEnrollmentCommand;
import com.example.be_a.enrollment.application.EnrollmentApplyService;
import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentRepository;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.example.be_a.global.error.ApiException;
import com.example.be_a.global.error.ErrorCode;
import com.example.be_a.user.application.CurrentUserInfo;
import com.example.be_a.user.domain.UserRole;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class EnrollmentApplyServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private EnrollmentApplyService enrollmentApplyService;

    @Test
    void 저장_중_UNIQUE_예외가_나면_ALREADY_ENROLLED로_변환한다() {
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.existsByClassIdAndUserId(1L, 20L)).thenReturn(false);
        when(classRepository.tryIncrementEnrolled(1L)).thenReturn(1);
        when(enrollmentRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate key",
                new ConstraintViolationException("duplicate key", null, "UK_ENROLLMENTS_CLASS_USER")
            ));

        assertThatThrownBy(() -> enrollmentApplyService.apply(user, new ApplyEnrollmentCommand(1L, false)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ALREADY_ENROLLED);
    }

    @Test
    void UNIQUE가_아닌_무결성_예외는_그대로_전파한다() {
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.existsByClassIdAndUserId(1L, 20L)).thenReturn(false);
        when(classRepository.tryIncrementEnrolled(1L)).thenReturn(1);
        when(enrollmentRepository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException(
                "fk violation",
                new ConstraintViolationException("fk violation", null, "fk_enrollments_class")
            ));

        assertThatThrownBy(() -> enrollmentApplyService.apply(user, new ApplyEnrollmentCommand(1L, false)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 중복_신청이면_정원_증가를_시도하지_않고_ALREADY_ENROLLED를_반환한다() {
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.existsByClassIdAndUserId(1L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> enrollmentApplyService.apply(user, new ApplyEnrollmentCommand(1L, false)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ALREADY_ENROLLED);

        verify(classRepository, never()).tryIncrementEnrolled(any());
        verify(enrollmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 강의가_없으면_CLASS_NOT_FOUND를_반환한다() {
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.existsByClassIdAndUserId(1L, 20L)).thenReturn(false);
        when(classRepository.tryIncrementEnrolled(1L)).thenReturn(0);
        when(classRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentApplyService.apply(user, new ApplyEnrollmentCommand(1L, false)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CLASS_NOT_FOUND);

        verify(enrollmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 정원_증가_실패_후_강의가_CLOSED면_CLASS_NOT_OPEN을_반환한다() {
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.existsByClassIdAndUserId(1L, 20L)).thenReturn(false);
        when(classRepository.tryIncrementEnrolled(1L)).thenReturn(0);
        when(classRepository.findById(1L)).thenReturn(Optional.of(classWithStatus(10L, "마감 강의", ClassStatus.CLOSED)));

        assertThatThrownBy(() -> enrollmentApplyService.apply(user, new ApplyEnrollmentCommand(1L, true)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CLASS_NOT_OPEN);

        verify(enrollmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 정원_증가_실패_후_WAITLIST를_사용하지_않으면_CLASS_FULL을_반환한다() {
        CurrentUserInfo user = studentUser(20L);

        when(enrollmentRepository.existsByClassIdAndUserId(1L, 20L)).thenReturn(false);
        when(classRepository.tryIncrementEnrolled(1L)).thenReturn(0);
        when(classRepository.findById(1L)).thenReturn(Optional.of(classWithStatus(10L, "만석 강의", ClassStatus.OPEN)));

        assertThatThrownBy(() -> enrollmentApplyService.apply(user, new ApplyEnrollmentCommand(1L, false)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CLASS_FULL);

        verify(enrollmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void 정원_증가_실패_후_WAITLIST를_사용하면_WAITING으로_저장한다() {
        CurrentUserInfo user = studentUser(20L);
        ArgumentCaptor<EnrollmentEntity> enrollmentCaptor = ArgumentCaptor.forClass(EnrollmentEntity.class);

        when(enrollmentRepository.existsByClassIdAndUserId(1L, 20L)).thenReturn(false);
        when(classRepository.tryIncrementEnrolled(1L)).thenReturn(0);
        when(classRepository.findById(1L)).thenReturn(Optional.of(classWithStatus(10L, "대기열 강의", ClassStatus.OPEN)));
        when(enrollmentRepository.saveAndFlush(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        enrollmentApplyService.apply(user, new ApplyEnrollmentCommand(1L, true));

        verify(enrollmentRepository, times(1)).saveAndFlush(enrollmentCaptor.capture());

        EnrollmentEntity savedEnrollment = enrollmentCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(savedEnrollment.getClassId()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(savedEnrollment.getUserId()).isEqualTo(20L);
        org.assertj.core.api.Assertions.assertThat(savedEnrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
    }

    private CurrentUserInfo studentUser(Long userId) {
        return new CurrentUserInfo(userId, UserRole.STUDENT);
    }

    private ClassEntity classWithStatus(Long creatorId, String title, ClassStatus status) {
        ClassEntity classEntity = ClassEntity.createDraft(
            creatorId,
            title,
            "설명",
            30000,
            30,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 31)
        );
        if (status == ClassStatus.OPEN || status == ClassStatus.CLOSED) {
            classEntity.changeStatus(ClassStatus.OPEN);
        }
        if (status == ClassStatus.CLOSED) {
            classEntity.changeStatus(ClassStatus.CLOSED);
        }
        return classEntity;
    }
}
