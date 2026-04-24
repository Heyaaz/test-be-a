# BE-A 수강 신청 시스템 구현 계획

## 1. 과제 요약

| 항목 | 내용 |
| --- | --- |
| 과제 | BE-A. 수강 신청 시스템 (CRUD + 비즈니스 규칙형) |
| 기술 스택 | Java 17, Spring Boot 3.x, MySQL 8, JPA(Hibernate), Docker |
| 제출물 | Public Git repository, README, 소스 코드, 테스트 코드(필수), 실행 방법(Docker), API 명세, ERD, AI 활용 내역 |
| 기한 | 최초 접속일로부터 5일 |

---

## 2. 요구사항 해석 및 가정
- 기존 요구사항 분석(`docs/analyze-requirements.md`)을 바탕으로, 구현에 필요한 세부 가정과 해석을 명시적으로 기술한다. 요구사항 문서에 명시되지 않았거나 모호한 부분에 대한 설계적 판단과 그 근거를 포함한다.


### 2.1 도메인 용어
- **Class (강의)**: 크리에이터가 개설하는 판매 상품. 정원/가격/기간 보유.
- **Enrollment (수강 신청)**: 클래스메이트가 특정 강의에 신청한 내역.
- **User**: 본 과제에서는 인증/인가를 단순화. 구현은 **`X-User-Id` 헤더 방식으로 통일**한다(JD상 파라미터도 허용되지만, 문서/테스트/예외 처리를 단순화하기 위해 헤더만 채택).

### 2.2 상태 전이 규칙
- Class: `DRAFT → OPEN → CLOSED` (단방향, 역방향 불가)
  - DRAFT: 생성 직후. 수정/삭제 가능, 신청 불가.
  - OPEN: 모집 중. 신청 가능, 가격/정원 수정 제한.
  - CLOSED: 마감. 신규 신청/대기열 불가. 기존 PENDING은 유효(결제 확정은 **CLOSED 이후 불허**로 가정).
- Enrollment: 상태 집합 `{WAITING, PENDING, CONFIRMED, CANCELLED}`
  - WAITING: 정원 초과 시 대기열 진입. enrolled_count에 포함 **안 됨**.
  - PENDING: 자리 확보 완료, 결제 대기. enrolled_count 포함.
  - CONFIRMED: 결제 완료로 수강 확정. enrolled_count 포함.
  - CANCELLED: 취소. enrolled_count 차감.
- 허용 전이: `WAITING → PENDING` (승급), `WAITING → CANCELLED`, `PENDING → CONFIRMED`, `PENDING → CANCELLED`, `CONFIRMED → CANCELLED`
- 금지: 그 외 모든 전이 → `INVALID_STATE_TRANSITION`

### 2.3 정원 관리 가정
- **정원 차감 시점**: PENDING 생성 시점 (WAITING은 차감 안 함).
- CANCELLED 전환 시 정원 복구 (PENDING/CONFIRMED에서 취소한 경우).
- CONFIRMED → CANCELLED 가능 기간: **결제 후 7일 이내**.
- 대기열 승급: 정원 복구 트랜잭션 내에서 **가장 오래 기다린 WAITING 중 아직 유효한 1건**을 즉시 PENDING으로 전환(FIFO). 승급 후보가 자기 취소와 경합해 무효가 되면 **같은 트랜잭션에서 다음 WAITING을 재조회**한다. 승급된 사용자는 그 시점부터 결제 7일 시계가 시작되는 게 아니라, 취소 기간 시계는 CONFIRMED 시점 기준이므로 변화 없음.

### 2.4 인증/인가 가정
- `X-User-Id` 헤더로 사용자 식별.
- 요청마다 `X-User-Id`에 해당하는 `User`가 실제 존재하는지 확인한다. 없으면 `USER_NOT_FOUND`.
- 강의 생성(`POST /api/classes`)은 `User.role == CREATOR`인 경우만 허용한다.
- 강의 소유권 판정은 생성 이후 `Class.creatorId == X-User-Id`로 체크한다.
- 별도 권한 테이블/관리자 개념은 없다.

### 2.5 도메인 외 범위
- 실제 결제 연동 없음: `POST /enrollments/{id}/confirm` 호출 시 바로 상태만 변경.
- 이메일/알림 발송 없음.
- 회원가입/로그인 API 없음: `User`는 데이터 시드로만 존재.

---

## 3. 기술 스택 및 선정 이유

선정 강도를 **[필수] / [강함] / [합리] / [선호]** 4단계로 구분한다. 각 기술마다 **배제한 대안**과 **배제 근거**를 함께 적는다.

### [필수] Java 17 + Spring Boot 3.x
- **근거**: 과제가 "필수: Spring Boot" 명시. Spring Boot 3.x는 Java 17 이상 요구. 17은 현재 가장 안정적인 LTS.
- **배제**: Kotlin(허용되나 평가자 가독성 우선).

### [강함] Spring Data JPA (vs MyBatis)
- **근거**: 본 도메인이 **상태 전이 중심**(Class, Enrollment 각 3단계). 엔티티 메서드로 상태 전이를 캡슐화하는 설계에 JPA가 자연스럽게 맞음. `@Modifying` 네이티브 쿼리로 원자적 UPDATE도 그대로 가능.
- **배제**: MyBatis는 복잡 조인/동적 쿼리가 주일 때 강점. 2개 테이블 CRUD에 도입할 이유 없음.

### [합리] MySQL 8 (vs PostgreSQL / H2)
- **H2 배제 (강함)**: 본 과제 핵심이 동시성 정확성. H2는 `FOR UPDATE` 시맨틱, 예약어, 제약 위반 예외 타입이 MySQL과 달라 **테스트 통과 ≠ 운영 통과**.
- **PostgreSQL vs MySQL**: 본 해법(원자적 조건부 UPDATE + UNIQUE)은 양쪽 모두 동등하게 지원. 강한 배타성 없음.
- **MySQL 채택**: 국내 B2B SaaS 운영 환경에서 일반적, InnoDB 락 동작 공식 문서가 풍부해 동시성 근거 인용 용이.

### [강함] Flyway (vs Liquibase / hibernate ddl-auto)
- **ddl-auto 배제**: 스키마가 런타임에 유추됨 → **리뷰 불가, 롤백 불가**. 과제에서 결정적 스키마 필수.
- **Liquibase 배제**: XML/YAML 추상화가 SQL 한 단계 더 감쌈. Flyway는 `V1__init.sql` 자체가 스키마 → 평가자 리뷰 비용 최저.

### [강함] Testcontainers + 실 MySQL (vs @DataJpaTest + H2)
- **근거**: 과제 "BE 테스트 코드 필수" + 본 설계의 핵심인 동시성 검증.
- **H2 배제**: 락 시맨틱/예외 타입 차이로 동시성 테스트가 **H2에서 통과해도 증명이 안 됨**.
- **트레이드오프 인정**: CI 시간 증가 → 단위/통합 테스트 태그 분리로 완화.

### [합리] springdoc-openapi (vs Spring REST Docs / 수기)
- **근거**: 과제 요건 "API 명세 또는 샘플 요청/응답" 필수. 코드-문서 동기화.
- **REST Docs 배제**: 품질 최상이나 학습/셋업 비용이 5일 과제에 과함. ROI 불일치.
- **수기 배제**: drift 불가피.

### [강함] Docker Compose
- **근거**: 과제 "실행 방법(로컬 또는 Docker)" + 평가자 재현성. `docker compose up` 단일 명령이 채점 지연 리스크 최소화.
- **주의**: MySQL 컨테이너 healthcheck + `depends_on: service_healthy`로 기동 순서 보장.

### [선호] Lombok
- **솔직**: 없어도 무방. 개인 선호.
- **절충안**: DTO는 Java 17 `record`, 엔티티만 `@Getter` 최소 사용.

### 요약

| 기술 | 등급 | 핵심 근거 | 배제 대안 |
| --- | --- | --- | --- |
| Spring Boot 3.x / Java 17 | 필수 | 과제 명시 | Kotlin |
| Spring Data JPA | 강함 | 상태 전이 도메인 + 락 추상화 | MyBatis |
| MySQL 8 | 합리 | 운영 일반성 + InnoDB 문서화 | H2(락 상이), PG(대등) |
| Flyway | 강함 | SQL=스키마, 리뷰 최저 비용 | ddl-auto, Liquibase |
| Testcontainers | 강함 | 동시성 증명엔 실 DB 필수 | H2 |
| springdoc-openapi | 합리 | 요건 충족 + ROI | REST Docs, 수기 |
| Docker Compose | 강함 | 재현성 단일 명령 | 수동 셋업 |
| Lombok | 선호 | 선택 사항 | record 대체 가능 |

---

## 4. 아키텍처

### 4.1 레이어 구조
```
com.example.be_a
├── class_              (강의 도메인 - `class`가 예약어라 언더스코어)
│   ├── api             # ClassController
│   ├── application     # ClassService, ClassCommand/Query
│   ├── domain          # ClassEntity, ClassStatus, ClassRepository
│   └── dto             # Request/Response DTO
├── enrollment
│   ├── api             # EnrollmentController
│   ├── application     # EnrollmentService, 비즈니스 규칙
│   ├── domain          # EnrollmentEntity, EnrollmentStatus
│   └── dto
├── user
│   └── domain          # UserEntity (간이)
├── global
│   ├── config          # JPA, OpenAPI, Web (UserIdArgumentResolver)
│   ├── error           # GlobalExceptionHandler, ErrorCode
│   └── support         # BaseEntity (createdAt, updatedAt)
└── BeAApplication
```

### 4.2 패키지 원칙
- 도메인별 수직 분할(패키지 by feature).
- `api → application → domain` 단방향 의존.
- DTO는 controller 레이어 경계에서만 사용, service는 command/query 객체로 수신.

---

## 5. 데이터 모델 (ERD)

### 5.1 테이블

**users**
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| name | VARCHAR(100) | NOT NULL |
| role | VARCHAR(20) | NOT NULL (`CREATOR` / `STUDENT`) |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

**classes**
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT |
| creator_id | BIGINT | FK(users.id), NOT NULL |
| title | VARCHAR(200) | NOT NULL |
| description | TEXT | |
| price | INT | NOT NULL, CHECK >= 0 |
| capacity | INT | NOT NULL, CHECK > 0 |
| enrolled_count | INT | NOT NULL, DEFAULT 0, **CHECK (enrolled_count >= 0 AND enrolled_count <= capacity)** |
| start_date | DATE | NOT NULL |
| end_date | DATE | NOT NULL, CHECK (end_date >= start_date) |
| status | VARCHAR(20) | NOT NULL (`DRAFT`/`OPEN`/`CLOSED`) |
| created_at / updated_at | DATETIME(6) | |

인덱스:
- `(status, id)` — 목록 페이지네이션
- `(creator_id, id)` — 크리에이터 자기 강의 조회

**CHECK 제약 의도**: 원자적 UPDATE가 1차 방어선, DB CHECK가 최종 방어선. 코드 실수로 `UPDATE classes SET enrolled_count = enrolled_count + 1`이 조건 없이 나가더라도 DB가 거부.

**enrollments**
| 컬럼 | 타입 | 제약 |
| --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT |
| class_id | BIGINT | FK(classes.id), NOT NULL |
| user_id | BIGINT | FK(users.id), NOT NULL |
| status | VARCHAR(20) | NOT NULL |
| requested_at | DATETIME(6) | NOT NULL |
| confirmed_at | DATETIME(6) | |
| cancelled_at | DATETIME(6) | |
| version | BIGINT | NOT NULL, DEFAULT 0 — **낙관적 락(`@Version`)**. 승급 vs 동시 취소 경합 감지용 |
| created_at / updated_at | DATETIME(6) | |

상태 값: `WAITING` / `PENDING` / `CONFIRMED` / `CANCELLED`

유니크/인덱스:
- `uk_enrollments_class_active_user(class_id, active_user_id)` — 같은 사용자가 같은 강의에 **활성 상태(PENDING/CONFIRMED/WAITING) enrollment를 2건 이상 갖지 못하게** 한다. `active_user_id`는 `status <> CANCELLED`일 때만 `user_id`, `CANCELLED`면 `NULL`인 VIRTUAL generated column이다. MySQL UNIQUE의 NULL 중복 허용을 이용해 취소 이력은 보존하면서 재신청을 허용한다. `design-rationale.md §3.3` 참조.
- `(user_id, id DESC)` — `GET /api/enrollments/me` 페이지네이션
- `(class_id, status, requested_at, id)` — 대기열 FIFO 승급 조회 및 상태 필터 보조. FIFO 기준은 `requested_at ASC, id ASC`(동일 시각 tie-breaker)로 통일한다.
- `(class_id, user_id, status)` — 취소 후 재신청으로 같은 사용자 이력이 누적될 때 크리에이터 수강생 목록의 "사용자별 최신 1건" 조회를 보조한다.

**version 컬럼의 존재 이유**: Class row의 X-lock은 "신규 신청 vs 취소" 경합은 차단하나, "승급 트랜잭션이 WAITING row를 PENDING으로 전환" vs "해당 사용자가 동시에 자신의 WAITING을 취소" 경합은 서로 다른 Enrollment row 흐름이라 막지 못함. `@Version`으로 두 UPDATE 중 늦은 쪽을 `OptimisticLockException` 발생시켜 강제 재시도/실패.

### 5.2 ERD
```
users 1 ─< classes (creator)
users 1 ─< enrollments
classes 1 ─< enrollments
```

---

## 6. API 설계

### 6.1 강의(Class)

| Method | Path | 설명 | 권한 |
| --- | --- | --- | --- |
| POST | `/api/classes` | 강의 등록 (DRAFT 생성) | 크리에이터 |
| PATCH | `/api/classes/{id}` | 강의 수정 (DRAFT/OPEN 수정 가능, CLOSED 불가) | 본인 크리에이터 |
| DELETE | `/api/classes/{id}` | 강의 삭제 (DRAFT만 가능) | 본인 크리에이터 |
| POST | `/api/classes/{id}/status` | 상태 전이 (`OPEN`/`CLOSED`) | 본인 크리에이터 |
| GET | `/api/classes` | 강의 목록 (`status`, `page`, `size` 쿼리) | 누구나 |
| GET | `/api/classes/{id}` | 강의 상세 (`enrolledCount`, `waitingCount` 포함 — 아래 주석) | 누구나 |
| GET | `/api/classes/{id}/enrollments` | 수강생 목록 (`status`, `page`, `size`) | 본인 크리에이터 |

**강의 수정/삭제 정책**
- `DRAFT`: 제목/설명/가격/정원/기간 전체 수정 가능, 삭제 가능
- `OPEN`: 제목/설명/가격/정원/기간 수정 가능
- `CLOSED`: 수정/삭제 불가
- 수정 위반 시 409 `CLASS_UPDATE_NOT_ALLOWED`

**PATCH 의미론**
- `PATCH /api/classes/{id}`는 **부분 수정(partial update)** 으로 해석한다.
- 요청 바디에 포함되지 않은 필드는 기존 값을 유지한다.
- `null`로 필드를 지우는 동작은 지원하지 않는다. nullable이 아닌 필드에 `null`이 오면 400 `VALIDATION_ERROR`
- `capacity`는 현재 신청 인원(`enrolledCount`)보다 작게 수정할 수 없다. 위반 시 400 `VALIDATION_ERROR`

**enrolledCount / waitingCount 산출 전략**
- `enrolledCount`: `classes.enrolled_count` 컬럼을 직접 노출. 원자적 UPDATE 경로에서만 증감해 항상 정합.
- `waitingCount`: `classes`에 컬럼을 두지 않고 **조회 시점 `SELECT COUNT(*) FROM enrollments WHERE class_id=? AND status='WAITING'`**로 계산. 이유:
  - 대기열 증감은 신청/취소/승급 모든 경로에서 발생 → 컬럼 유지 시 모든 경로에 `waiting_count` 증감 로직 추가 필요(버그 면적 증가)
  - `(class_id, status, requested_at, id)` 인덱스의 선두 컬럼만으로 WAITING 카운팅을 커버해 COUNT 쿼리가 인덱스 스캔으로 빠름
  - 상세 조회는 초당 호출 수가 낮아 COUNT 비용이 문제가 되지 않음

### 6.2 수강 신청(Enrollment)

| Method | Path | 설명 |
| --- | --- | --- |
| POST | `/api/enrollments` | `{classId, waitlist: boolean}` — 정원 여유 시 PENDING, 초과 시 `waitlist=true`면 WAITING, 아니면 `CLASS_FULL` |
| POST | `/api/enrollments/{id}/confirm` | 결제 확정 (PENDING → CONFIRMED) |
| POST | `/api/enrollments/{id}/cancel` | 취소 (`WAITING/PENDING/CONFIRMED → CANCELLED`) → 정원 복구 시 대기열 FIFO 승급 트리거 |
| GET | `/api/enrollments/me` | 내 신청 목록 (`status`, `page`, `size`) |

**신청 플로우(클라이언트 관점)**
1. `waitlist: false` (기본): 정원 차면 409 `CLASS_FULL`.
2. `waitlist: true`: 정원 차면 WAITING으로 저장(201), 여유 있으면 PENDING으로 저장(201). 응답 `status` 필드로 구분.
3. 사용자는 대기 중 원하면 `cancel`로 취소 가능.
4. 누군가 취소해 자리가 나면 **가장 오래 기다린 유효 WAITING**이 자동 PENDING 승급. 승급 대상이 자기 취소와 경합해 사라졌다면 같은 트랜잭션에서 다음 WAITING을 재조회한다. 승급 후 결제는 기존 플로우와 동일.

### 6.3 공통 페이지네이션 응답

Spring `Page<T>`를 **그대로 노출하지 않는다**(`pageable`, `sort`, `last` 등 내부 필드 노출 회피). 아래 얇은 래퍼로 통일:

```json
{
  "items": [ /* T[] */ ],
  "page": 0,
  "size": 20,
  "totalElements": 123,
  "totalPages": 7,
  "hasNext": true
}
```

- 쿼리 파라미터: `page`(0-indexed, 기본 0), `size`(기본 20, 최대 100).
- size 상한 초과 시 400 `VALIDATION_ERROR`.
- 정렬 키는 엔드포인트가 고정(임의 정렬 허용 시 인덱스 전략 파편화 → 제한).

**페이지네이션 입력 처리 정책**
- `page < 0`, `size <= 0`, `size > 100`은 모두 400 `VALIDATION_ERROR`
- 구현은 Spring `Pageable` 자동 바인딩에만 의존하지 않고, 컨트롤러에서 `@RequestParam @Min @Max` 검증 후 `PageRequest.of(page, size)`를 생성한다.
- 즉, `size=1000` 요청을 100으로 **자동 보정(clamp)하지 않고 명시적으로 거절**한다.

### 6.4 요청/응답 예시

**POST /api/classes**
```json
// Headers: X-User-Id: 1
// Request
{
  "title": "Spring Boot 입문",
  "description": "백엔드 기본기",
  "price": 30000,
  "capacity": 30,
  "startDate": "2026-05-01",
  "endDate": "2026-05-31"
}

// 201 Created
{
  "id": 10,
  "creatorId": 1,
  "title": "Spring Boot 입문",
  "price": 30000,
  "capacity": 30,
  "status": "DRAFT"
}
```

**DELETE /api/classes/{id}**
```json
// 204 No Content
```

**POST /api/classes/{id}/status**
```json
// Request
{ "targetStatus": "OPEN" }

// 200 OK
{
  "id": 10,
  "status": "OPEN"
}
```

**POST /api/enrollments — 정원 여유**
```json
// Request
{ "classId": 10, "waitlist": false }
// Headers: X-User-Id: 3

// 201 Created
{
  "id": 101, "classId": 10, "userId": 3,
  "status": "PENDING",
  "requestedAt": "2026-04-22T11:00:00"
}
```

**POST /api/enrollments — 정원 초과 + 대기열 진입**
```json
// Request
{ "classId": 10, "waitlist": true }

// 201 Created
{
  "id": 102, "classId": 10, "userId": 7,
  "status": "WAITING",
  "requestedAt": "2026-04-22T11:00:05"
}
```

**POST /api/enrollments — 정원 초과 + 대기열 거부**
```json
// Request
{ "classId": 10, "waitlist": false }

// 409 Conflict
{ "code": "CLASS_FULL", "message": "정원이 가득 찼습니다.", "path": "/api/enrollments" }
```

**POST /api/enrollments/{id}/cancel — 대기열 승급 트리거**
```json
// 200 OK (취소자 응답)
{ "id": 101, "status": "CANCELLED", "cancelledAt": "2026-04-22T12:00:00" }

// 내부 동작: enrolled_count 복구 → 가장 오래된 유효 WAITING 1건을 PENDING으로 승급
// 승급된 사용자는 다음 GET /api/enrollments/me 조회에서 확인
```

**GET /api/classes/{id}/enrollments — 수강생 목록 (크리에이터 전용)**
```json
// 200 OK
{
  "items": [
    { "enrollmentId": 55, "userId": 3, "userName": "Alice",
      "status": "CONFIRMED", "requestedAt": "2026-04-20T09:00:00",
      "confirmedAt": "2026-04-20T09:30:00" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}

// 403 Forbidden (크리에이터 아님)
{ "code": "FORBIDDEN", "message": "접근 권한이 없습니다.", "path": "/api/classes/10/enrollments" }
```

### 6.5 에러 코드 체계
- `MISSING_USER_ID` (400) — `X-User-Id` 헤더 누락
- `INVALID_USER_ID` (400) — 숫자 변환 실패 등 형식 오류
- `USER_NOT_FOUND` (404)
- `CREATOR_ONLY` (403) — 강의 생성 등 크리에이터 전용 기능 접근
- `CLASS_NOT_FOUND` (404)
- `CLASS_NOT_OPEN` (409) — DRAFT/CLOSED에서 신청/확정 시
- `CLASS_NOT_DRAFT` (409) — DRAFT 전용 삭제 위반
- `CLASS_UPDATE_NOT_ALLOWED` (409) — CLOSED 상태 강의 수정 시도
- `CLASS_DELETE_NOT_ALLOWED` (409) — 삭제 불가 조건(예: enrollment 존재) 위반
- `CLASS_FULL` (409)
- `ALREADY_ENROLLED` (409) — PENDING/CONFIRMED/WAITING 어떤 상태라도 동일 사용자가 이미 존재
- `ENROLLMENT_NOT_FOUND` (404)
- `INVALID_STATE_TRANSITION` (409)
- `CANCEL_PERIOD_EXPIRED` (409)
- `CONFLICT_RETRY` (409) — 낙관적 락 충돌(동시 취소/승급 경합). 재시도 권장
- `FORBIDDEN` (403)
- `VALIDATION_ERROR` (400)

**에러 응답 포맷**
```json
{
  "code": "VALIDATION_ERROR",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/classes",
  "fieldErrors": [
    { "field": "capacity", "reason": "must be greater than 0" }
  ]
}
```

- 단건 도메인 오류는 `fieldErrors` 없이 `code/message/path`만 반환 가능
- 입력 검증 오류는 `fieldErrors`를 포함해 어떤 필드가 잘못됐는지 명시

**에러 코드 사용 기준**
- `CREATOR_ONLY`: 역할(role) 자체가 `CREATOR`가 아니라서 크리에이터 전용 API에 접근한 경우
- `FORBIDDEN`: 역할은 충족하지만 **내 리소스가 아님**(예: 다른 크리에이터의 강의 수정/조회)인 경우
- `INVALID_STATE_TRANSITION`: 요청 시점에 이미 비즈니스적으로 불가능한 상태 전이인 경우
- `CONFLICT_RETRY`: 원래는 가능했지만 **동시성 경합으로 인해** 실패한 경우(optimistic lock 등)
- enum/타입 파싱 실패, JSON 형식 오류, Bean Validation 위반은 모두 400 `VALIDATION_ERROR`

**검사 우선순위(Precedence)**
1. 요청 형식/파싱/Bean Validation (`VALIDATION_ERROR`)
2. 인증 헤더 존재/형식 (`MISSING_USER_ID`, `INVALID_USER_ID`)
3. 사용자 존재 (`USER_NOT_FOUND`)
4. 대상 리소스 존재 (`CLASS_NOT_FOUND`, `ENROLLMENT_NOT_FOUND`)
5. 권한/역할 (`CREATOR_ONLY`, `FORBIDDEN`)
6. 상태 전이/도메인 규칙 (`CLASS_NOT_OPEN`, `INVALID_STATE_TRANSITION`, `CANCEL_PERIOD_EXPIRED` 등)
7. 동시성 충돌 (`CONFLICT_RETRY`)

- 복수 조건이 동시에 성립하면 **더 앞선 단계의 에러를 우선 반환**한다.
- 단, `POST /api/enrollments`는 고경합 핫패스 특성상 `ALREADY_ENROLLED`만 별도 우선 규칙을 둔다(아래 §7.1.4 참고).

---

## 7. 핵심 비즈니스 로직

### 7.1 수강 신청 (정원 동시성 제어)

#### 7.1.1 문제 특성 고정

| 속성 | 값 |
| --- | --- |
| 경쟁 자원 수 | 1개 (Class.enrolled_count) |
| 경합 형태 | 동일 자원에 N명 집중 (마지막 자리) |
| 연산 | `enrolled_count += 1 WHERE enrolled_count < capacity` |
| 불변식 | `enrolled_count ≤ capacity` 절대 위반 금지 |
| 예상 경합 강도 | 높음 (인기 강의 오픈 순간) |

#### 7.1.2 후보 기법 배제/채택 근거

| 기법 | 평가 | 이 문제에 대한 판정 근거 |
| --- | --- | --- |
| 낙관적 락 `@Version` | ❌ 탈락 | 전제("충돌 드묾")가 본 문제의 고경합과 정면 충돌. 100명 동시 신청 시 99명이 version mismatch → 재시도 폭증. 실패 원인(정원 초과 vs 단순 충돌) 구분 불가 → 재조회 필요. |
| 비관적 락 `FOR UPDATE` | △ 열등 | 정확하나 SELECT→검사→UPDATE 2단계 왕복. 락 점유 구간이 원자적 UPDATE보다 길어 같은 Class 신청이 전면 직렬화됨. 원자적 UPDATE가 제공하는 보장을 더 싸게 대체 가능 → 채택 이유 없음. |
| **원자적 조건부 UPDATE** | ✅ **채택** | 단일 자원 + 조건부 산술 연산과 SQL 한 문장이 1:1 대응. 락 점유 수 μs. `affected rows`로 즉시 판별 → 재시도 불필요. `WHERE enrolled_count < capacity`가 DB 엔진에서 평가되어 over-enrollment **구조적으로 불가**. |
| `FOR UPDATE SKIP LOCKED` | ❌ 부적합 | 전제가 "자원 여러 개 + 워커가 나눠 가짐". 본 문제는 자원 1개라 스킵 대상 없음. 스킵하면 신청 유실. |
| 분산 락 (Redis Redlock) | ❌ 과잉 | 모든 상태가 단일 RDBMS 내부. DB 트랜잭션이 이미 격리 제공. 네트워크 홉 + 락 만료/연장 + Redis-DB 일관성 부담을 도입할 이유 없음. |
| 메시지 큐 직렬화 | ❌ 범위 초과 | 초당 수만 건 규모/동기 API UX 문제. 과제 스코프와 불일치. |

#### 7.1.3 선택 트리

```
경합 자원이 1개인가?
├─ YES → 조건부 산술 연산으로 표현 가능?
│         ├─ YES → 원자적 조건부 UPDATE ✅  ← 본 문제
│         └─ NO  → 비관적 락
└─ NO  → 워커가 자원을 나눠 가지는 패턴?
          ├─ YES → FOR UPDATE SKIP LOCKED
          └─ NO  → 낙관적 락(저경합) / 비관적 락(고경합)
```

본 문제는 YES-YES 가지 → **원자적 조건부 UPDATE 이외의 선택은 열등하거나 부적합**.

#### 7.1.4 구현 (정원 여유 + 대기열 폴백)

```java
// ClassRepository
@Modifying
@Query(value = """
    UPDATE classes
    SET enrolled_count = enrolled_count + 1
    WHERE id = :id AND status = 'OPEN' AND enrolled_count < capacity
    """, nativeQuery = true)
int tryIncrementEnrolled(@Param("id") Long id);

// EnrollmentService
@Transactional
public EnrollmentResponse apply(Long classId, Long userId, boolean waitlistOption) {
    // best-effort 중복 검사: 사용자 경험상 ALREADY_ENROLLED를 CLASS_FULL보다 우선
    if (enrollmentRepository.existsByClassIdAndUserIdAndStatusNot(classId, userId, CANCELLED)) {
        throw new ApiException(ALREADY_ENROLLED);
    }

    int updated = classRepository.tryIncrementEnrolled(classId);
    if (updated == 1) {
        return saveEnrollment(Enrollment.pending(classId, userId));
    }

    // 증가 실패 → 원인 분리
    ClassEntity cls = classRepository.findById(classId)
        .orElseThrow(() -> new ApiException(CLASS_NOT_FOUND));
    if (cls.getStatus() != OPEN) throw new ApiException(CLASS_NOT_OPEN);

    // 정원 초과
    if (!waitlistOption) throw new ApiException(CLASS_FULL);
    return saveEnrollment(Enrollment.waiting(classId, userId));  // 대기열
}

private EnrollmentResponse saveEnrollment(Enrollment e) {
    try {
        return EnrollmentResponse.from(enrollmentRepository.save(e));
    } catch (DataIntegrityViolationException ex) {
        // uk_enrollments_class_active_user 위반 → 트랜잭션 롤백으로 enrolled_count 복구
        throw new ApiException(ALREADY_ENROLLED);
    }
}
```

**핵심 포인트**
- 정원 증가와 enrollment 저장이 **하나의 트랜잭션**. INSERT 실패 시 `enrolled_count += 1`도 롤백.
- 중복 신청(PENDING/WAITING/CONFIRMED 어떤 활성 상태든)은 `uk_enrollments_class_active_user`로 DB에서 차단한다. `CANCELLED` 이력은 중복 신청으로 보지 않아 재신청 가능하다.
- WAITING 저장 시 enrolled_count는 건드리지 않음.
- `POST /api/enrollments`는 사용자 경험상 **`ALREADY_ENROLLED`를 `CLASS_FULL`보다 우선**한다. 이를 위해 best-effort 사전 조회를 두되, 최종 정합성은 DB UNIQUE 제약이 보장한다.

**사전 검증 순서**
1. `X-User-Id` 헤더 존재/형식 확인
2. 사용자 존재 확인
3. 요청 바디 Bean Validation
4. 리소스 존재/권한 확인
5. 비즈니스 규칙 검사 및 상태 전이 수행

**`POST /api/enrollments` 에러 우선순위**
1. `VALIDATION_ERROR` — body 형식 오류, `classId` 누락 등
2. `MISSING_USER_ID` / `INVALID_USER_ID`
3. `USER_NOT_FOUND`
4. `CLASS_NOT_FOUND`
5. `CLASS_NOT_OPEN`
6. `ALREADY_ENROLLED` — 사전 조회 또는 INSERT 시 UNIQUE 위반
7. `CLASS_FULL` — `waitlist=false` 이고 좌석이 없는 경우

- 즉, “이미 신청한 사용자”는 강의가 만석이더라도 **가능한 한** `ALREADY_ENROLLED`를 받도록 설계한다.
- 다만 최종 authoritative source는 DB UNIQUE 제약이며, 사전 조회와 INSERT 사이의 race는 `DataIntegrityViolationException -> ALREADY_ENROLLED`로 정규화한다.

### 7.2 결제 확정

- PENDING만 CONFIRMED로 전이 허용.
- Class 상태가 **CLOSED면 거부** (`CLASS_NOT_OPEN`). §2.2 가정 반영.
- `confirmedAt` 세팅. Class의 `enrolledCount`는 이미 PENDING에서 증가했으므로 변화 없음.

```java
@Transactional
public EnrollmentResponse confirm(Long enrollmentId, Long userId) {
    Enrollment e = enrollmentRepository.findById(enrollmentId)
        .orElseThrow(() -> new ApiException(ENROLLMENT_NOT_FOUND));
    if (!e.getUserId().equals(userId)) throw new ApiException(FORBIDDEN);

    // CLOSED 강의의 PENDING은 확정 불허
    ClassEntity cls = classRepository.findById(e.getClassId())
        .orElseThrow(() -> new ApiException(CLASS_NOT_FOUND));
    if (cls.getStatus() == CLOSED) throw new ApiException(CLASS_NOT_OPEN);

    e.confirm(clock);  // 도메인: 상태 검증(PENDING만) + CONFIRMED 전환 + confirmedAt
    return EnrollmentResponse.from(e);
}
```

### 7.3 취소 + 대기열 승급

#### 취소 규칙
- WAITING → CANCELLED: 제한 없음. 정원 변화 없음(애초에 증가 안 했음). 승급 트리거 **없음**.
- PENDING → CANCELLED: 제한 없음. 정원 복구 + 승급 트리거.
- CONFIRMED → CANCELLED: `confirmedAt + 7일` 이내만 허용. 정원 복구 + 승급 트리거.
- CANCELLED → *: `INVALID_STATE_TRANSITION`.

**409 분기 기준**
- 이미 `CANCELLED`인 enrollment를 다시 취소하는 등 **처음부터 불가능한 요청**은 `INVALID_STATE_TRANSITION`
- 두 요청이 거의 동시에 들어와 늦은 쪽이 낙관적 락 충돌로 실패한 경우는 `CONFLICT_RETRY`
- 즉, **비즈니스 규칙 위반**과 **동시성 충돌**을 외부 API 코드에서 구분한다.

#### 동시성/공정성 보장

**경합 케이스 3종과 방어 수단**

| 경합 | 방어 수단 | 외부 응답 |
| --- | --- | --- |
| A. 취소/승급 vs 신규 신청 | Class row X-lock (단일 자원 직렬화) | 정상 처리 (대기 후 순서대로) |
| B. 동일 enrollment 동시 취소 2건 | 첫 쪽 성공 → CANCELLED, 두 번째는 도메인 `cancel()`의 상태 가드에서 `INVALID_STATE_TRANSITION`. 타이밍상 동시 UPDATE인 경우 `@Version`이 늦은 쪽을 `OptimisticLockException`로 감지 | 첫 요청 200, 두 번째 409 `INVALID_STATE_TRANSITION` 또는 `CONFLICT_RETRY` |
| C. 승급과 해당 WAITING 사용자의 동시 취소 | Enrollment `@Version` — 충돌 시 해당 WAITING은 건너뛰고 **같은 트랜잭션에서 다음 WAITING 재조회** | 취소 요청 200. 대기열이 남아 있으면 다음 WAITING 승급, 없으면 좌석만 복구 |

**A 케이스 절차** — 취소 트랜잭션 안에서 순차 실행:
1. `UPDATE classes SET enrolled_count -= 1` (row X-lock 획득)
2. **Class가 OPEN인 경우에만** 대기열 FIFO 1건 조회 (정렬 기준: `requested_at ASC, id ASC`; CLOSED면 승급 스킵)
3. 해당 enrollment를 PENDING으로 전환 시도 (`@Version` 체크)
4. 3번이 동시 취소와 충돌하면 **같은 트랜잭션에서 다음 WAITING 재조회**. 승급 성공 시에만 `UPDATE classes SET enrolled_count += 1`
5. 커밋

InnoDB에서 1단계에서 잡은 row lock은 **트랜잭션 커밋까지 유지**되므로, 다른 신규 신청 트랜잭션은 1~5 단계 동안 대기 → 대기열이 신규 신청보다 **항상 우선**(FIFO 공정성).

#### 구현

```java
// ClassRepository
@Modifying
@Query(value = """
    UPDATE classes SET enrolled_count = enrolled_count - 1
    WHERE id = :id AND enrolled_count > 0
    """, nativeQuery = true)
int tryDecrementEnrolled(@Param("id") Long id);

@Modifying
@Query(value = """
    UPDATE classes SET enrolled_count = enrolled_count + 1
    WHERE id = :id AND status = 'OPEN' AND enrolled_count < capacity
    """, nativeQuery = true)
int tryIncrementEnrolled(@Param("id") Long id);

// EnrollmentRepository
@Query("""
    SELECT e FROM Enrollment e
    WHERE e.classId = :classId AND e.status = 'WAITING'
    ORDER BY e.requestedAt ASC, e.id ASC
    """)
Optional<Enrollment> findOldestWaiting(@Param("classId") Long classId, Pageable limit1);

// EnrollmentService
@Transactional
public EnrollmentResponse cancel(Long enrollmentId, Long userId) {
    Enrollment e = enrollmentRepository.findById(enrollmentId)
        .orElseThrow(() -> new ApiException(ENROLLMENT_NOT_FOUND));
    if (!e.getUserId().equals(userId)) throw new ApiException(FORBIDDEN);

    EnrollmentStatus prev = e.getStatus();
    e.cancel(clock);  // 도메인: 상태/기간 검증 + CANCELLED 전환 + cancelledAt

    // WAITING 취소는 정원 변화 없음 → 승급 트리거 없음
    if (prev == WAITING) return EnrollmentResponse.from(e);

    // PENDING/CONFIRMED 취소 → 정원 복구 (실패 시 불변식 위반이므로 예외)
    if (classRepository.tryDecrementEnrolled(e.getClassId()) == 0) {
        throw new IllegalStateException("enrolled_count 복구 실패: classId=" + e.getClassId());
    }

    // 승급은 Class가 OPEN일 때만 시도
    ClassEntity cls = classRepository.findById(e.getClassId()).orElseThrow();
    if (cls.getStatus() != OPEN) return EnrollmentResponse.from(e);  // CLOSED면 승급 스킵

    // 대기열 FIFO 승급 (같은 트랜잭션, 같은 Class row lock)
    while (true) {
        Optional<Enrollment> waiting = enrollmentRepository.findOldestWaiting(
            e.getClassId(), PageRequest.of(0, 1)
        );
        if (waiting.isEmpty()) {
            return EnrollmentResponse.from(e); // 대기열 없음 → 좌석만 복구된 상태로 종료
        }

        Enrollment head = waiting.get();
        try {
            head.promote(clock);  // WAITING → PENDING (@Version 체크)
            enrollmentRepository.saveAndFlush(head);  // OptimisticLock 즉시 감지

            if (classRepository.tryIncrementEnrolled(e.getClassId()) == 0) {
                throw new IllegalStateException("승급 좌석 재할당 실패: classId=" + e.getClassId());
            }
            return EnrollmentResponse.from(e);
        } catch (OptimisticLockingFailureException ex) {
            // WAITING 사용자가 같은 시점에 자기 스스로 취소 → 다음 대기자 재조회
            entityManager.clear();
            log.info("승급 대상 WAITING이 동시 취소됨. 다음 WAITING 재시도. classId={}",
                e.getClassId());
        }
    }
}
```

**핵심 포인트**
- 취소 + 승급이 **단일 트랜잭션**. 단, 승급 대상 WAITING이 경합으로 사라지면 **같은 트랜잭션 안에서 다음 WAITING을 재시도**한다.
- Class row lock이 신청/취소/승급 모든 경로에서 **같은 row를 잡음** → 전체 시스템이 이 row 기준으로 직렬화 ⇒ FIFO 보장.
- Enrollment `@Version`으로 "승급 vs 동시 취소" 경합 감지. 충돌이 나면 재조회 후 다음 WAITING을 승급시킨다.
- `enrolled_count`와 `PENDING+CONFIRMED` 수의 일치는 예외 억제가 아니라 **트랜잭션 롤백으로 보존**한다. 즉, 승급 후 좌석 재할당이 실패하면 취소/승급 전체를 롤백해 불변식을 지킨다.
- CLOSED 상태 Class의 PENDING 취소 시에도 취소는 정상 완료되고, 승급만 스킵된다.

### 7.4 Class 상태 전이
- DRAFT → OPEN: `startDate <= today <= endDate`가 아니어도 허용 (사전 오픈 가능). 필드 검증만.
- OPEN → CLOSED: PENDING 상태 신청 건 처리 정책 → **유지**(결제 확정은 불허로 가정).
- DRAFT → CLOSED / CLOSED → * : 불가.

```java
public record ChangeClassStatusRequest(ClassStatus targetStatus) {}
```

- 허용 요청값은 `OPEN`, `CLOSED` 두 값뿐
- `DRAFT -> OPEN`, `OPEN -> CLOSED`만 허용
- 그 외 상태 전이 요청은 409 `INVALID_STATE_TRANSITION`
- 예: `targetStatus = "OPENED"`처럼 enum 자체가 잘못되면 400 `VALIDATION_ERROR`
- 예: `targetStatus = "DRAFT"`처럼 enum은 맞지만 현재 상태에서 허용되지 않으면 409 `INVALID_STATE_TRANSITION`

### 7.5 Class 삭제
- `DELETE /api/classes/{id}`
- 본인 크리에이터만 가능
- `DRAFT` 상태에서만 허용
- 해당 강의에 Enrollment가 1건이라도 존재하면 삭제 불가 (`CLASS_DELETE_NOT_ALLOWED`)
- 성공 시 204 No Content

> 이유 1: 문서 상단에서 DRAFT는 “수정/삭제 가능”이라고 정의했고, 과제 유형도 CRUD이므로 삭제 정책을 명시적으로 포함한다.
>
> 이유 2: 정상 플로우상 DRAFT에는 Enrollment가 존재할 수 없지만, 데이터 정합성 훼손/수동 데이터 조작 등 **비정상 상태 방어 규칙**으로 `CLASS_DELETE_NOT_ALLOWED`를 둔다.

### 7.6 수강생 목록 조회 (크리에이터 전용)

```java
@Transactional(readOnly = true)
public PageResponse<EnrollmentSummary> listByClass(
        Long classId, Long requesterId, EnrollmentStatus statusFilter, Pageable pageable) {

    ClassEntity cls = classRepository.findById(classId)
        .orElseThrow(() -> new ApiException(CLASS_NOT_FOUND));

    if (!cls.getCreatorId().equals(requesterId)) {
        throw new ApiException(FORBIDDEN);
    }

    Page<EnrollmentWithUser> page = (statusFilter == null)
        ? enrollmentRepository.findByClassIdWithUser(classId, pageable)
        : enrollmentRepository.findByClassIdAndStatusWithUser(classId, statusFilter, pageable);

    return PageResponse.of(page.map(EnrollmentSummary::from));
}
```

- **권한**: `Class.creatorId == X-User-Id`. 크리에이터 본인만 조회. 관리자 개념 없음.
- **필터**: `status` 쿼리 파라미터로 WAITING/PENDING/CONFIRMED/CANCELLED 중 1개 또는 전체. 단, 목록은 감사 로그가 아니라 사용자별 최신 수강 신청 상태 뷰이므로 같은 `(class_id, user_id)`에서는 최신 `requested_at DESC, id DESC` 1건만 후보가 된다. `status=CANCELLED`도 "모든 취소 이력"이 아니라 최신 상태가 CANCELLED인 사용자만 반환한다.
- **조인**: `users` 테이블과 조인해 name 포함. N+1 방지 위해 JPQL join으로 단일 쿼리.
- **정렬**: 노출 대상 최신 row들을 `requested_at ASC, id ASC`로 페이지네이션한다.

### 7.7 페이지네이션 공통 처리

```java
public record PageResponse<T>(
    List<T> items, int page, int size,
    long totalElements, int totalPages, boolean hasNext
) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(
            p.getContent(), p.getNumber(), p.getSize(),
            p.getTotalElements(), p.getTotalPages(), p.hasNext()
        );
    }
}

// 컨트롤러에서 명시 검증 후 PageRequest 생성
@GetMapping("/api/classes")
public PageResponse<ClassSummary> list(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) ClassStatus status) {
    return classService.list(status, PageRequest.of(page, size));
}
```

**전역 예외 정규화 대상**
- `MethodArgumentNotValidException`
- `ConstraintViolationException`
- `MethodArgumentTypeMismatchException`
- `HttpMessageNotReadableException`

위 예외들은 모두 `VALIDATION_ERROR` 포맷으로 변환해, 400 응답 shape이 엔드포인트마다 달라지지 않도록 한다.

---

## 8. 테스트 계획

### 8.1 단위 테스트
- 도메인 엔티티 상태 전이 (`ClassEntity.open()`, `Enrollment.confirm()`, `Enrollment.promote()` 등)
- 취소 가능 기간 계산 (CONFIRMED → CANCELLED 7일 경계)
- WAITING/PENDING/CONFIRMED별 취소 시 정원 복구 여부 구분

### 8.2 통합 테스트 (Testcontainers + MySQL)
- `ClassControllerTest`: CRUD + 상태 필터 목록 + 페이지네이션 응답 포맷
- `ClassDeleteTest`: DRAFT 삭제 성공, OPEN/CLOSED 삭제 실패, enrollment 존재 시 삭제 실패
- `EnrollmentControllerTest`: 신청 → 확정 → 취소 골든패스
- `WaitlistTest`: 정원 초과 + `waitlist=true` → WAITING, 취소 → FIFO 승급
- `CreatorEnrollmentListTest`: 크리에이터 본인만 200, 타 사용자 403
- 에러 시나리오: 정원 초과(`waitlist=false`), 이중 신청(PENDING/WAITING 어느 상태든), 상태 위반, 취소 기간 만료, `X-User-Id` 누락/형식 오류, 크리에이터 전용 API 접근 거부, 페이지 크기 초과

### 8.3 동시성 테스트 (핵심)
- `EnrollmentApplyConcurrencyIntegrationTest`:
  - capacity=10, 동시 20명 신청 (`waitlist=false`) → 정확히 10건 PENDING, 10건 `CLASS_FULL`
  - 같은 사용자 동시 신청 2건 → 활성 신청은 1건만 유지, 나머지는 `ALREADY_ENROLLED`
  - 불변식 검증: `enrolled_count == PENDING + CONFIRMED`
- `EnrollmentPromotionConcurrencyIntegrationTest.여러_PENDING_취소가_동시에_발생해도_WAITING은_FIFO_순서대로_승급된다`:
  - capacity=2, 2명 PENDING + 3명 WAITING
  - PENDING 2건 동시 취소 → 가장 오래된 WAITING 2건만 PENDING 승급
- `EnrollmentPromotionConcurrencyIntegrationTest.PENDING_취소와_신규_신청이_경합하면_기존_WAITING이_좌석을_우선_배정받는다`:
  - capacity=1, 1 PENDING + 1 WAITING(W)
  - PENDING 취소와 새 사용자 신규 신청(N)을 동시 실행
  - **기대**: W가 먼저 승급하고 N은 `CLASS_FULL`. N이 자리를 가로채면 안 됨
- `EnrollmentPromotionConcurrencyIntegrationTest.여러_PENDING_취소보다_WAITING이_적으면_승급된_인원만_정원에_반영된다`:
  - capacity=2, 2명 PENDING + 1명 WAITING
  - PENDING 2건 동시 취소 → WAITING 1건만 승급, `enrolled_count == 1`
- `EnrollmentPromotionConcurrencyIntegrationTest.CLOSED_전환_중_PENDING_취소가_발생해도_정원_불변식이_유지된다`:
  - PENDING 존재 + Class OPEN→CLOSED 전환 + PENDING 취소를 인접 시점 실행
  - **기대**: 취소와 마감 모두 성공. 어떤 직렬화 순서든 `enrolled_count == PENDING + CONFIRMED` 유지
- `EnrollmentPromotionConcurrencyIntegrationTest.승급_대상_WAITING이_자기_취소하면_다음_WAITING을_승급한다`:
  - capacity=1, 1명 PENDING + 2명 WAITING
  - 첫 번째 WAITING 자기 취소가 먼저 반영된 뒤 PENDING 취소 실행
  - **기대**: 취소된 WAITING은 제외되고 다음 WAITING이 PENDING 승급
- 후속 보강 후보:
  - 동일 enrollment confirm/cancel 경합
  - 동일 사용자 cancel/reapply 경합
- 공통
  - `ExecutorService` + `CountDownLatch`로 동시 트리거
  - `@Transactional` 금지 (각 스레드 독립 트랜잭션)
  - 테스트 사이 테이블 정리 (`JdbcTemplate`, repository batch delete)
  - **공통 단언**: 테스트 종료 시 `enrolled_count`가 `PENDING + CONFIRMED` Enrollment 수와 일치

### 8.4 테스트 격리
- 통합 테스트는 매 테스트마다 `enrollments` + `classes` TRUNCATE 후 seed.
- 컨테이너는 테스트 클래스 단위 재사용(`@Testcontainers` + `@Container static`).

---

## 9. 실행 방법

### 9.1 로컬 (개발)
```bash
# 의존 DB만 먼저 기동 (로컬도 H2 금지 — 운영 DB와 시맨틱 일치)
docker compose up -d mysql

# 앱 실행
./gradlew bootRun
# 프로파일: local (Docker MySQL에 연결)
```

### 9.2 Docker Compose (전체 기동)
```bash
docker compose up --build
# app:8080, mysql:3306
# Flyway가 startup 시 자동 마이그레이션
# app은 mysql healthcheck 통과 후 기동 (depends_on: service_healthy)
```

### 9.3 테스트
```bash
./gradlew test
```

### 9.4 API 문서
- `http://localhost:8080/swagger-ui.html`

---

## 10. 일정 (5일)

선택 구현 4종(취소 기간 제한, waitlist, 수강생 목록, 페이지네이션) **모두 필수 범위**로 반영.

| Day | 작업 |
| --- | --- |
| D1 | 프로젝트 셋업 (Gradle, Spring Boot, Docker Compose, Flyway, Testcontainers), 엔티티/마이그레이션(users/classes/enrollments), BaseEntity, GlobalExceptionHandler, PageResponse |
| D2 | Class 도메인 (CRUD, 삭제 정책, 상태 전이, 목록/상세 API + 페이지네이션) + 단위/통합 테스트 |
| D3 | Enrollment 도메인: 신청(원자적 UPDATE + 대기열 폴백), 확정, 취소(+ 승급 트리거), 취소 7일 제한 + 단위/통합 테스트 |
| D4 | 동시성 테스트(정원경합, 대기열FIFO, 승급-신규경합, WAITING 부족, CLOSED전환중PENDING취소), 수강생 목록(권한 + 필터), Swagger, 에러 응답 정비 |
| D5 | README 작성(설계 근거/AI 활용 범위/실행), 최종 점검(Docker 재현 테스트, 커밋 히스토리 정리) |

---

## 11. 미구현 / 제약사항

### 구현 범위 내
- 필수 구현 전체
- 선택 구현 전체: 취소 7일 제한, 대기열, 수강생 목록, 페이지네이션

### 의도적 제약
- 실제 결제 연동 없음 (`POST /enrollments/{id}/confirm`은 단순 상태 변경)
- 인증/인가는 `X-User-Id` 헤더로 단순화 (Spring Security 미도입)
- 대기열 상한 없음 (무제한). 실무에선 보통 capacity 배수로 제한하나 과제 스코프에서 생략
- 승급 시 사용자 알림 없음 (이메일/푸시). 사용자는 `GET /api/enrollments/me`로 확인
- 관리자 권한/운영자 override 없음. 모든 권한은 `CREATOR` 역할과 리소스 소유권 기준으로만 판단
- Rate limiting, audit log 등 운영 요건은 범위 외

---

## 12. AI 활용 범위 (기록 방침)

README에 다음을 기재한다:
- 설계 단계: 도메인 모델/상태 전이 검토, ERD 초안 리뷰.
- 구현 단계: 보일러플레이트(DTO, Exception, Mapper), 테스트 케이스 발상.
- **직접 작성·검증**: 원자적 조건부 UPDATE 설계 및 구현, 동시성 테스트, 상태 전이 규칙, API 계약.
- 생성된 코드는 빌드/테스트로 검증 후 채택.

---

## 13. 리스크 및 대응

| 리스크 | 대응 |
| --- | --- |
| 인증/인가 단순화로 권한 해석 혼선 | `X-User-Id` 헤더로 통일하고, 생성 권한은 `role == CREATOR`, 이후 소유권은 `creatorId == requesterId`로 분리해 문서화 |
| Testcontainers CI 시간 증가 | 단위/통합 테스트 `@Tag` 분리 실행. 로컬도 Docker MySQL 사용(H2 금지). |
| 원자적 UPDATE 실패 시 원인 분기 | 재조회 1회로 `CLASS_NOT_FOUND` / `CLASS_NOT_OPEN` / `CLASS_FULL` 구분. 실패 경로에만 발생해 핫패스 비용 없음. |
| 대기열 승급 vs 신규 신청 경합 (공정성) | 취소 트랜잭션 안에서 동기 승급. Class row X-lock이 트랜잭션 커밋까지 유지되어 FIFO 직렬화 보장. 전용 동시성 테스트(§8.3)로 검증. |
| 승급과 WAITING 자기취소 경합 → 불변식 위반 가능성 | Enrollment `@Version` 낙관적 락. 충돌 시 같은 트랜잭션에서 다음 WAITING을 재조회해 승급하고, 끝까지 승급할 대상이 없으면 좌석만 복구한다. §8.3 `승급과WAITING자기취소경합` 테스트로 검증. |
| 승급 중 Class CLOSED 전환 | Class row lock으로 OPEN/CLOSED 전환과 취소/승급이 직렬화된다. CLOSE가 먼저 커밋되면 승급 스킵, 취소가 먼저 락을 잡으면 승급 후 CLOSE가 뒤따를 수 있다. §8.3 `CLOSED전환중PENDING취소` 테스트로 검증. |
| MySQL 파셜 유니크 미지원 | VIRTUAL generated column `active_user_id` + `uk_enrollments_class_active_user(class_id, active_user_id)`로 partial unique를 에뮬레이션한다. `CANCELLED`는 NULL이 되어 취소 이력과 재신청이 공존한다. `design-rationale.md §3.3` 참조. |
| 수강생 목록 N+1 쿼리 | `@EntityGraph(attributePaths = "user")` 또는 fetch join으로 단일 쿼리. |
| Pageable 자동 보정으로 400 정책이 흐려짐 | 컨트롤러에서 `@Min/@Max` 검증 후 `PageRequest` 생성으로 명시적 400 보장 |
| 5일 일정 빠듯함 | 선택 구현 4종 모두 반영 필요. D1~D3 타이트하게 셋업 + 핵심 로직, D4에 동시성 테스트 집중. D5는 README/점검 버퍼. waitlist 동시성 테스트가 지연되면 최소한 FIFO 단일 스레드 테스트는 반드시 포함. |
