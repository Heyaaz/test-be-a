package com.example.be_a.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI beAOpenApi() {
        return new OpenAPI().info(new Info()
            .title("BE-A Enrollment API")
            .version("v1")
            .description("강의 관리, 수강 신청, 결제 확정, 취소, 대기열 승급을 다루는 API 문서입니다. 인증은 X-User-Id 헤더로 단순화했습니다."));
    }
}
