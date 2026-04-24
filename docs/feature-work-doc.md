# BE-A 기능 단위 작업 문서

이 문서는 `docs/implementation-plan.md`를 실제 작업 순서로 풀어쓴 **기능 단위 실행 문서**다.  
원칙은 각 기능을 다음 순서로 끝내는 것이다.

> **구현 → 테스트 코드 → 수동 확인(필요 시) → 다음 기능 이동**

즉, 여러 기능을 한꺼번에 구현한 뒤 마지막에 테스트를 몰아서 작성하지 않는다.  
각 기능을 작은 vertical slice로 끊어서 완료한다.

---

## 0. 작업 원칙

### 기본 원칙
- 기능 하나를 구현하면 그 기능의 테스트까지 바로 작성한다.
- 테스트 없이 다음 기능으로 넘어가지 않는다.
- 공통 기반 작업도 “기능을 위한 기반”으로 보고 필요한 최소 범위만 먼저 만든다.
- 문서와 구현이 충돌하면 문서를 먼저 수정한 뒤 구현한다.

### 권장 작업 흐름
1. 기능 범위 확정
2. 필요한 DTO / Entity / Repository / Service / Controller 구현
3. 에러 코드 / 예외 처리 연결
4. 해당 기능 테스트 작성
5. 테스트 통과 확인
6. 다음 기능으로 이동

---

## 1. 공통 기반 기능

이 단계는 모든 기능의 공통 기반이다.  
다만 “완벽한 공통 레이어”를 먼저 만드는 게 아니라, 이후 기능 구현에 필요한 최소 기반을 준비하는 목적이다.

### 구현
- [x] Gradle / Spring Boot / Java 17 셋업
- [x] 패키지 구조 생성
  - [x] `class_`
  - [x] `enrollment`
  - [x] `user`
  - [x] `global`
- [x] Flyway 설정
- [x] MySQL / Docker Compose 설정
- [x] Testcontainers 설정
- [x] `BaseEntity` 작성
- [x] 공통 에러 응답 DTO 작성
- [x] `ErrorCode` enum 작성
- [x] `ApiException` 작성
- [x] `GlobalExceptionHandler` 기본 골격 작성
- [x] Swagger 기본 설정

### 테스트 코드
- [x] 애플리케이션 컨텍스트 로딩 테스트
- [x] Flyway 마이그레이션 적용 확인 테스트
- [x] GlobalExceptionHandler가 `VALIDATION_ERROR` 형태를 반환하는지 기본 테스트

### 완료 기준
- [x] 앱이 기동된다
- [x] DB 마이그레이션이 적용된다
- [x] 테스트 컨테이너 기반 테스트가 돈다
- [x] 공통 에러 응답 포맷이 동작한다

---

## 2. 사용자 식별 / 권한 기반 기능

이 기능의 목적은 모든 API가 공통으로 의존하는 `X-User-Id` 처리와 권한 판단 기반을 만드는 것이다.

### 구현
- [x] `UserEntity`, `UserRole` enum 작성
- [x] `UserRepository` 작성
- [x] seed user 준비 (`CREATOR`, `STUDENT`)
- [x] `X-User-Id` 헤더 파싱 로직 구현
- [x] 누락 시 `MISSING_USER_ID`
- [x] 숫자 변환 실패 시 `INVALID_USER_ID`
- [x] 존재하지 않는 user 시 `USER_NOT_FOUND`
- [x] 역할 검사 유틸 / 서비스 작성
  - [x] `CREATOR_ONLY`
  - [x] `FORBIDDEN`

### 테스트 코드
- [x] 헤더 누락 시 400 `MISSING_USER_ID`
- [x] 헤더 형식 오류 시 400 `INVALID_USER_ID`
- [x] 존재하지 않는 사용자 시 404 `USER_NOT_FOUND`
- [x] STUDENT가 크리에이터 전용 API 접근 시 403 `CREATOR_ONLY`

### 완료 기준
- [x] 모든 이후 API에서 사용자 식별을 재사용할 수 있다
- [x] 권한 오류 응답 코드가 문서와 일치한다

---

## 3. 강의 생성 기능

가장 먼저 완성할 비즈니스 기능이다. 이후 수정/삭제/상태 전이/신청 기능의 출발점이 된다.

### 구현
- [x] `ClassEntity`, `ClassStatus` enum 작성
- [x] `ClassRepository` 작성
- [x] `CreateClassRequest` 작성
- [x] `POST /api/classes` 구현
- [x] `role == CREATOR` 검사
- [x] 생성 시 `status = DRAFT`
- [x] `creatorId = requesterId`
- [x] 필드 validation 연결
  - [x] `title`
  - [x] `price`
  - [x] `capacity`
  - [x] `startDate`
  - [x] `endDate`

### 테스트 코드
- [x] 크리에이터가 강의 생성 성공
- [x] STUDENT는 생성 실패 (`CREATOR_ONLY`)
- [x] `title` 빈값 검증
- [x] `price < 0` 검증
- [x] `capacity <= 0` 검증
- [x] `endDate < startDate` 검증

### 완료 기준
- [x] 강의가 DRAFT 상태로 저장된다
- [x] 생성 응답 형식이 문서와 맞는다

---

## 4. 강의 조회 기능

강의 목록/상세 조회는 이후 Enrollment 시나리오에서도 자주 확인하는 기능이므로 초기에 안정화한다.

### 구현
- [x] `GET /api/classes`
- [x] status filter 지원
- [x] 페이지네이션 적용
- [x] `GET /api/classes/{id}`
- [x] `enrolledCount` 포함
- [x] `waitingCount` 계산 포함
- [x] `PageResponse` 적용

### 테스트 코드
- [x] 목록 조회 성공
- [x] status filter 동작
- [x] page / size 정상 동작
- [x] `size > 100`이면 400 `VALIDATION_ERROR`
- [x] 상세 조회 성공
- [x] 존재하지 않는 강의면 404 `CLASS_NOT_FOUND`

### 완료 기준
- [x] 목록/상세 응답이 문서와 일치한다
- [x] 페이지네이션 정책이 400 기준으로 동작한다

---

## 5. 강의 수정 기능

과제 원문 기준에 맞춰 DRAFT/OPEN 수정과 CLOSED 차단, PATCH 의미론을 정확히 반영해야 한다.

### 구현
- [x] `UpdateClassRequest` 작성
- [x] `PATCH /api/classes/{id}` 구현
- [x] partial update 적용
- [x] body에 없는 필드는 유지
- [x] `null` clear 미지원
- [x] DRAFT 전체 수정 가능
- [x] OPEN 전체 수정 가능
- [x] CLOSED 수정 불가
- [x] 현재 신청 인원보다 작은 정원은 허용하지 않음

### 테스트 코드
- [x] DRAFT 전체 수정 성공
- [x] OPEN 전체 수정 성공
- [x] 현재 신청 인원보다 작은 정원 수정 시 400 `VALIDATION_ERROR`
- [x] CLOSED 수정 시 실패
- [x] 다른 크리에이터 강의 수정 시 403 `FORBIDDEN`
- [x] `null` 입력 시 400 `VALIDATION_ERROR`

### 완료 기준
- [x] PATCH 의미론이 문서와 정확히 일치한다
- [x] 금지 필드 무시 없이 명시적으로 에러 처리된다

---

## 6. 강의 상태 전이 기능

Enrollment는 OPEN 상태 강의에만 신청 가능하므로, 상태 전이 기능을 먼저 안정화해야 한다.

### 구현
- [x] `ChangeClassStatusRequest` 작성
- [x] `POST /api/classes/{id}/status` 구현
- [x] `DRAFT -> OPEN`
- [x] `OPEN -> CLOSED`
- [x] 그 외 전이는 `INVALID_STATE_TRANSITION`
- [x] enum 파싱 실패는 `VALIDATION_ERROR`

### 테스트 코드
- [x] `DRAFT -> OPEN` 성공
- [x] `OPEN -> CLOSED` 성공
- [x] `DRAFT -> CLOSED` 실패
- [x] `CLOSED -> OPEN` 실패
- [x] 잘못된 enum 값이면 400 `VALIDATION_ERROR`

### 완료 기준
- [x] 상태 전이 규칙이 문서와 일치한다

---

## 7. 강의 삭제 기능

문서상 DRAFT는 삭제 가능하므로, CRUD 완성도를 위해 반드시 포함한다.

### 구현
- [x] `DELETE /api/classes/{id}` 구현
- [x] 본인 크리에이터만 가능
- [x] DRAFT만 삭제 가능
- [x] enrollment 존재 시 `CLASS_DELETE_NOT_ALLOWED`
- [x] 성공 시 204 반환

### 테스트 코드
- [x] DRAFT 삭제 성공
- [x] OPEN 삭제 실패
- [x] CLOSED 삭제 실패
- [x] 다른 사용자 강의 삭제 실패
- [x] enrollment 존재 시 삭제 실패

### 완료 기준
- [x] 문서의 “DRAFT 수정/삭제 가능” 정책이 실제 API로 구현된다

---

## 8. 수강 신청 기능

이 기능부터 동시성과 정원 관리가 본격적으로 들어간다.  
가장 중요한 핵심 기능 중 하나다.

### 구현
- [x] `ApplyEnrollmentRequest` 작성
- [x] `EnrollmentEntity`, `EnrollmentStatus` enum 작성
- [x] `EnrollmentRepository` 작성
- [x] `existsByClassIdAndUserIdAndStatusNot`
- [x] `tryIncrementEnrolled`
- [x] `POST /api/enrollments` 구현
- [x] `ALREADY_ENROLLED` 우선 규칙 반영
- [x] 성공 시 PENDING 생성
- [x] 만석 + `waitlist=false`면 `CLASS_FULL`
- [x] 만석 + `waitlist=true`면 WAITING 생성
- [x] UNIQUE 위반 시 `ALREADY_ENROLLED`

### 테스트 코드
- [x] 정원 여유 시 PENDING 생성 성공
- [x] 중복 신청 시 `ALREADY_ENROLLED`
- [x] DRAFT/CLOSED 강의 신청 시 `CLASS_NOT_OPEN`
- [x] 정원 초과 + `waitlist=false`면 `CLASS_FULL`
- [x] 정원 초과 + `waitlist=true`면 WAITING 생성

### 완료 기준
- [x] 신청 성공/실패/대기열 진입 시나리오가 모두 동작한다

---

## 9. 결제 확정 기능

상태 전이 규칙은 단순하지만, CLOSED 강의와 권한 검사를 정확히 처리해야 한다.

### 구현
- [x] `POST /api/enrollments/{id}/confirm`
- [x] 본인 enrollment만 가능
- [x] PENDING만 CONFIRMED 가능
- [x] class CLOSED면 `CLASS_NOT_OPEN`
- [x] `confirmedAt` 세팅

### 테스트 코드
- [x] PENDING -> CONFIRMED 성공
- [x] WAITING confirm 시 `INVALID_STATE_TRANSITION`
- [x] CANCELLED confirm 시 `INVALID_STATE_TRANSITION`
- [x] 다른 사용자 enrollment confirm 시 `FORBIDDEN`
- [x] CLOSED 강의 confirm 시 `CLASS_NOT_OPEN`

### 완료 기준
- [x] 결제 확정 상태 전이와 제한이 문서와 일치한다

---

## 10. 수강 취소 기능

취소는 단순 상태 변경이 아니라 정원 복구와 연결되므로, waitlist 승급 전 단계까지 포함해 구현해야 한다.

### 구현
- [x] `POST /api/enrollments/{id}/cancel`
- [x] WAITING 취소는 정원 변화 없음
- [x] PENDING 취소는 정원 복구
- [x] CONFIRMED 취소는 7일 제한 적용
- [x] 이미 CANCELLED면 409 처리
- [x] `tryDecrementEnrolled` 연결

### 테스트 코드
- [x] WAITING 취소 성공
- [x] PENDING 취소 성공
- [x] CONFIRMED 7일 이내 취소 성공
- [x] CONFIRMED 7일 초과 취소 실패 (`CANCEL_PERIOD_EXPIRED`)
- [x] 이미 CANCELLED 취소 실패
- [x] 다른 사용자 enrollment 취소 실패

### 완료 기준
- [x] 취소 정책과 기간 제한이 문서와 일치한다

---

## 11. 대기열 승급 기능

이 기능은 취소와 강하게 연결되며, 구현 난도가 가장 높다.  
취소 기능 완료 직후 바로 붙여서 테스트까지 끝내는 것이 좋다.

### 구현
- [x] `findOldestWaiting ORDER BY requestedAt, id`
- [x] WAITING -> PENDING 승급 로직 작성
- [x] `@Version` 적용
- [x] 충돌 시 다음 WAITING 재조회
- [x] OPEN 상태에서만 승급
- [x] 승급 성공 시 `enrolled_count` 재할당
- [x] 불변식 유지

### 테스트 코드
- [x] 취소 시 WAITING 1건 승급
- [x] FIFO 순서 보장
- [x] 대기열이 없으면 좌석만 복구
- [x] 승급 대상 자기 취소 경합 시 다음 WAITING 승급

### 완료 기준
- [x] 대기열 승급 규칙과 FIFO 정책이 문서와 일치한다

---

## 12. 내 신청 목록 기능

사용자 관점 조회 기능이다. 기능은 단순하지만, 실제 흐름 확인용으로 중요하다.

### 구현
- [x] `GET /api/enrollments/me`
- [x] 상태 필터
- [x] 페이지네이션
- [x] `PageResponse` 적용

### 테스트 코드
- [x] 내 신청 목록 조회 성공
- [x] 상태 필터 동작
- [x] 페이지네이션 동작

### 완료 기준
- [x] 신청/대기/취소 결과를 사용자 기준으로 확인할 수 있다

---

## 13. 크리에이터 수강생 목록 기능

선택 구현 항목이지만 문서상 필수 범위로 반영했으므로 별도 기능으로 마감한다.

### 구현
- [x] `GET /api/classes/{id}/enrollments`
- [x] 본인 강의만 조회 가능
- [x] status filter 지원
- [x] users join으로 이름 포함
- [x] 페이지네이션 적용
- [x] 취소 후 재신청 이력이 있는 사용자는 최신 수강 신청 1건만 노출

### 테스트 코드
- [x] 본인 강의 수강생 목록 조회 성공
- [x] 다른 크리에이터 강의 조회 시 `FORBIDDEN`
- [x] status filter 동작
- [x] 페이지네이션 동작
- [x] 같은 사용자가 취소 후 재신청하면 최신 수강 신청만 목록에 노출

### 완료 기준
- [x] 크리에이터 전용 수강생 조회 기능이 완성된다


---

## 13-1. 취소 후 재신청 허용 수정

기존 `UNIQUE(class_id, user_id)`는 `CANCELLED` 이력까지 중복 키로 잡아 같은 강의 재신청을 막았다. 이슈 #28에서 활성 신청만 중복 차단하도록 수정한다.

### 구현
- [x] V4 Flyway 마이그레이션 추가
- [x] `active_user_id` VIRTUAL generated column 추가
- [x] `uk_enrollments_class_user` 제거
- [x] `uk_enrollments_class_active_user(class_id, active_user_id)` 추가
- [x] `CANCELLED` 이력 제외 사전 중복 체크 적용
- [x] 크리에이터 수강생 목록은 사용자별 최신 수강 신청 1건만 노출

### 테스트 코드
- [x] `PENDING → CANCELLED → 재신청` 성공
- [x] `WAITING → CANCELLED → 재신청` 성공
- [x] 반복 취소/재신청 시 활성 신청 1건 유지
- [x] `CANCELLED` 이력 상태에서 동시 재신청 2건 중 1건만 성공
- [x] Flyway 마이그레이션으로 구 UNIQUE 제거 / 신규 UNIQUE·보조 인덱스 생성 검증

### 완료 기준
- [x] 취소 이력은 보존하면서 같은 강의 재신청이 가능하다
- [x] 활성 신청은 사용자당 강의별 1건만 유지된다

---

## 14. 동시성 테스트 기능

이 단계는 기능 구현을 새로 하는 단계라기보다, 핵심 비즈니스 규칙을 증명하는 단계다.

### 구현
- [x] 동시성 테스트 유틸 준비
  - [x] `ExecutorService`
  - [x] `CountDownLatch`
- [x] 테스트용 데이터 seed helper
- [x] 테스트 종료 후 DB 정리 방식 확정

### 테스트 코드
- [x] 정원 경합
  - [x] capacity=10, 20명 신청 -> 10 PENDING / 10 실패
- [x] 대기열 FIFO
  - [x] 여러 PENDING 동시 취소 시 가장 오래된 WAITING부터 승급
- [x] 승급 vs 신규 신청 경합
  - [x] WAITING이 신규 신청보다 우선
- [x] WAITING 부족 경합
  - [x] 취소 수보다 WAITING 수가 적으면 승급된 인원만 정원에 반영
- [x] 승급 vs WAITING 자기 취소 경합
  - [x] 승급 대상 WAITING의 자기 취소가 먼저 반영되면 다음 WAITING 승급
- [x] CLOSED 전환 vs PENDING 취소 경합
  - [x] 어떤 순서든 불변식 유지

### 완료 기준
- [x] 보강된 동시성 테스트에서 `enrolled_count == PENDING + CONFIRMED`가 유지된다
- [x] 대기열 승급 관련 주요 동시성 설계가 테스트로 증명된다
- [x] 승급 후보 자기 취소 race까지 별도 테스트로 증명한다

---

## 15. 문서화 / Swagger / README 기능

실제 제출물 품질을 끌어올리는 마지막 단계다.

### 구현
- [x] Swagger 설명 보강
- [x] API 예시 정리
- [x] README 작성
  - [x] 프로젝트 개요
  - [x] 기술 스택
  - [x] 실행 방법
  - [x] 요구사항 해석 및 가정
  - [x] 설계 결정과 이유
  - [x] 미구현 / 제약사항
  - [x] AI 활용 범위
  - [x] API 목록 및 예시
  - [x] 데이터 모델 설명
  - [x] 테스트 실행 방법

### 테스트 코드 / 검증
- [x] `docker compose up -d mysql` + `./gradlew bootRun` 실행 확인
- [x] Swagger UI 접속 확인
- [x] `./gradlew test` 최종 통과
- [x] `./gradlew build` 최종 통과

### 완료 기준
- [x] 제출 가능한 상태가 된다

---

## 16. 추천 작업 순서 요약

### 1차
- [x] 공통 기반
- [x] 사용자 식별 / 권한
- [x] 강의 생성
- [x] 강의 조회

### 2차
- [x] 강의 수정
- [x] 강의 상태 전이
- [x] 강의 삭제

### 3차
- [x] 수강 신청
- [x] 결제 확정
- [x] 수강 취소
- [x] 대기열 승급

### 4차
- [x] 내 신청 목록
- [x] 크리에이터 수강생 목록

### 5차
- [x] 동시성 테스트
- [x] Swagger 설명 보강 / README 최신화
- [x] Docker 최종 검증

---

## 17. 기능 완료 정의 (Definition of Done)

각 기능은 아래를 모두 만족해야 “완료”로 본다.

- [x] Controller 구현 완료
- [x] Service 로직 구현 완료
- [x] Repository / DB 반영 완료
- [x] 에러 처리 연결 완료
- [x] 해당 기능 테스트 코드 작성 완료
- [x] 테스트 통과
- [x] 문서와 구현 계약 일치 확인
