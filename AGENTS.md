# Repository Guidelines

## 프로젝트 구조 및 모듈 구성
- 이 디렉터리는 **Java 17 + Spring Boot** 백엔드 모듈입니다.
- 애플리케이션 코드는 `src/main/java/com/example/be_a` 아래에 둡니다.
  - 설정: `global/config`
  - 공통 예외 처리: `global/error`
  - 공통 엔티티 기반 클래스: `global/support`
- 설정 파일은 `src/main/resources`에 두며, DB 마이그레이션은 `src/main/resources/db/migration`에서 관리합니다.
- 테스트 코드는 `src/test/java/com/example/be_a` 아래에 프로덕션 패키지 구조를 따라 배치합니다.

## 빌드, 테스트, 로컬 실행 명령어
- `./gradlew build`: 컴파일, 테스트, 패키징까지 전체 검증을 수행합니다.
- `./gradlew test`: JUnit 5 테스트만 실행합니다.
- `./gradlew bootRun`: `application.yml`과 기본 `local` 프로필로 서버를 실행합니다.
- `docker compose -f docker-compose.yml up -d`: 로컬 MySQL을 실행합니다.
- `docker compose -f docker-compose.yml down`: 로컬 DB 컨테이너를 정리합니다.

## 코딩 스타일 및 네이밍 규칙
- Java는 **4칸 들여쓰기**, UTF-8, 파일당 하나의 공개 클래스 원칙을 유지합니다.
- 패키지는 소문자, 클래스는 `PascalCase`, 메서드/필드는 `camelCase`, 상수는 `UPPER_SNAKE_CASE`를 사용합니다.
- 설정 클래스는 `*Config`, 예외 타입은 `*Exception`, 테스트 클래스는 `*Test`로 끝나게 작성합니다.
- 마이그레이션 파일은 `V1__init.sql`처럼 `V번호__설명.sql` 형식을 따릅니다.

## 테스트 가이드
- 기본 도구는 **JUnit 5, Spring Boot Test, MockMvc, Testcontainers**입니다.
- 웹 계층은 `@WebMvcTest`, 통합 검증은 `@SpringBootTest`를 우선합니다.
- 테스트 이름은 `returnsValidationErrorShape`, `appliesFlywayMigrationsAndSeedData`처럼 기대 동작이 드러나게 작성합니다.
- DB 스키마나 예외 응답 형식을 바꾸면 관련 테스트를 함께 갱신합니다.

## 커밋 및 PR 가이드
- 커밋 메시지는 전역 규칙에 맞춰 `type: 요약` 형식을 사용합니다. 예: `docs: be_a 모듈 기여 가이드 추가`
- 권장 타입: `feat`, `fix`, `docs`, `chore`, `refactor`, `style`, `test`
- `git add .`는 지양하고, 변경한 파일만 선택적으로 스테이징합니다.
- 필요하면 본문에 `Constraint:`, `Rejected:`, `Tested:`, `Not-tested:` trailer를 추가합니다.
- PR에는 변경 목적, 영향 범위, 실행한 검증 명령, DB/설정 변경 여부를 적습니다.

## 설정 및 보안 팁
- 실제 비밀번호나 개인 환경값은 커밋하지 않습니다.
- 로컬 실행 전 `docker-compose.yml`의 MySQL 3306 포트 충돌 여부와 Docker 실행 상태를 확인합니다.
