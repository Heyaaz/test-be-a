# BE-A 수강 신청 시스템

Spring Boot 기반의 수강 신청 시스템입니다.

## 기술 스택
- Java 17
- Spring Boot 3.x
- Spring Data JPA
- MySQL 8
- Flyway
- Testcontainers
- Docker Compose

## 실행 방법
```bash
docker compose -f docker-compose.yml up -d
./gradlew bootRun
```

## 테스트
```bash
./gradlew test
./gradlew bootJar
```

## API 문서
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI Docs: `http://localhost:8080/api-docs`