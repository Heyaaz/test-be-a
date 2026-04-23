package com.example.be_a;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.be_a.support.MySqlTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest extends MySqlTestContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesFlywayMigrationsAndSeedData() {
        Integer managedTables = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('users', 'classes', 'enrollments', 'flyway_schema_history')
                """,
            Integer.class
        );
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        Integer enrollmentCheckConstraints = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_schema = DATABASE()
                  AND table_name = 'enrollments'
                  AND constraint_type = 'CHECK'
                  AND constraint_name IN (
                    'chk_enrollments_confirmed_at',
                    'chk_enrollments_cancelled_at'
                  )
                """,
            Integer.class
        );

        assertThat(managedTables).isEqualTo(4);
        assertThat(userCount).isEqualTo(3);
        assertThat(enrollmentCheckConstraints).isEqualTo(2);
    }
}
