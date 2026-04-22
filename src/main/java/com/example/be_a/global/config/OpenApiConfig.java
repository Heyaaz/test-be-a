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
            .description("수강 신청 시스템 과제용 초기 API 문서 설정입니다."));
    }
}
