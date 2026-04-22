package com.example.be_a;

import com.example.be_a.support.MySqlTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BeAApplicationContextTest extends MySqlTestContainerSupport {

    @Test
    void contextLoads() {
    }
}
