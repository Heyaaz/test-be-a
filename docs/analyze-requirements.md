# **과제 A — 수강 신청 시스템**

**유형:** CRUD + 비즈니스 규칙형

---

배경 시나리오

- 크리에이터(강사)는 강의를 개설하고 수강 정원, 가격, 기간을 설정합니다.
    - DRAFT, OPEN 상태 수정 가능, CLOSED 상태 수정 불가
    - 정원 축소시 현재 수강 인원보다 작게는 불가
- 클래스메이트(수강생)는 원하는 강의에 수강 신청을 합니다.
- 정원이 초과되면 신청이 불가합니다.
    - 초과시 대기열로 넘어감
- 신청 후 결제가 완료되어야 수강 확정됩니다.
    - PENDING → 결제 완료시 CONFIRMED 상태로 변경
- 수강 확정 후 일정 기간 내에는 취소가 가능하며, 이후에는 불가합니다.
    - CONFIRMED 후 7일 안에 CANCELLED 가능 이후 불가
    - 경계값 포함 (7일째 OK, 8일째 불가)

---

구현 범위

필수 구현

1. 강의(Class) 관리
- 강의 등록: 제목, 설명, 가격, 정원(최대 수강 인원), 수강 기간(시작일~종료일)
    - id, creator_id, title, description, price, capacity, status, enrolled_count,
      started_at, ended_at, created_at, updated_at
- 강의 상태: DRAFT → OPEN → CLOSED
    - DRAFT: 초안 (신청 불가)
    - OPEN: 모집 중 (신청 가능)
    - CLOSED: 모집 마감 (신청 불가)
    - 상태는 단방향, 역행 불가
- 강의 목록 조회 (상태 필터 가능)
    - status DRAFT, OPEN, CLOSED 페이지네이션 20 ~ 100
- 강의 상세 조회 (현재 신청 인원 포함)
- 권한: 생성/수정/삭제/상태전환은 본인 크리에이터만
- 삭제: 신청자 있으면 불가
1. 수강 신청(Enrollment) 관리
- 수강 신청: 사용자가 강의에 신청
    - id, class_id, user_id, status, requested_at, confirmed_at, cancelled_at,
      created_at, updated_at
- 신청 상태: PENDING → CONFIRMED → CANCELLED
    - PENDING: 신청 완료, 결제 대기
    - CONFIRMED: 결제 완료, 수강 확정
    - CANCELLED: 취소됨
    - WAITING은 대기열용
- 결제 확정 처리 (외부 결제 시스템 연동은 불필요 — 단순 상태 변경으로 대체)
    - PENDING에서 /confirm 시 CONFIRMED로 단순 상태 변경
- 수강 취소
    - /cancel 시 7일 이내 취소, 이후 불가
    - WAITING 취소는 정원 변화 없음
    - PENDING/CONFIRMED 취소는 정원 복구 + 대기열 승급
- 내 수강 신청 목록 조회
    - status PENDING, CONFIRMED, CANCELLED 페이지네이션 20 ~ 100
- 권한: 본인 신청만 조작 가능
1. 정원 관리 규칙
- 강의별 최대 정원을 초과한 신청은 거부
    - 0 <= enrolled_count <= capacity
    - enrolled_count == COUNT(PENDING + CONFIRMED)
- 동시에 여러 사람이 마지막 자리에 신청하는 경우를 고려
    - N명이 1개 자리 경쟁
    - 비관적 락 → 비즈니스 로직 sql에 들어감 → 락 점유 길음
    - 낙관적 락 → 락 경쟁시 재시도
    - 원자적 UPDATE → DB에서 한 방 처리 → 락 점유 짧음
        - 실패시 원인 모름 → 실패시에만 재시도 ?
        - 후속 SELECT로 원인 분기 (만석/강의없음/CLOSED)
    - SKIP LOCKED → 자원 N개 일때 유리 ? 작업큐
    - Redis → 요구사항 x

선택 구현 (추가 점수)

- 수강 취소 시 취소 가능 기간 제한 (예: 결제 후 7일 이내)
- 대기열(waitlist) 기능
    - 정원 마감시 WAITING으로 저장
    - 앞선 취소시 가장 오래된 WAITING이 PENDING으로 승급
- 강의별 수강생 목록 조회 (크리에이터 전용)
- 신청 내역 페이지네이션