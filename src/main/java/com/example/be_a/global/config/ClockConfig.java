package com.example.be_a.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock systemClock() {
        return Clock.system(APP_ZONE);
    }
}
