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
        Integer enrollmentMyListIndexes = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'enrollments'
                  AND index_name = 'idx_enrollments_user_status_id_desc'
            """,
            Integer.class
        );
        Integer activeEnrollmentColumns = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'enrollments'
                  AND column_name = 'active_user_id'
                  AND generation_expression IS NOT NULL
            """,
            Integer.class
        );
        Integer activeEnrollmentUniqueIndexes = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'enrollments'
                  AND index_name = 'uk_enrollments_class_active_user'
                  AND non_unique = 0
            """,
            Integer.class
        );
        Integer legacyEnrollmentUniqueIndexes = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'enrollments'
                  AND index_name = 'uk_enrollments_class_user'
            """,
            Integer.class
        );
        Integer enrollmentClassUserStatusIndexes = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'enrollments'
                  AND index_name = 'idx_enrollments_class_user_status'
            """,
            Integer.class
        );

        assertThat(managedTables).isEqualTo(4);
        assertThat(userCount).isEqualTo(3);
        assertThat(enrollmentCheckConstraints).isEqualTo(2);
        assertThat(enrollmentMyListIndexes).isGreaterThan(0);
        assertThat(activeEnrollmentColumns).isEqualTo(1);
        assertThat(activeEnrollmentUniqueIndexes).isEqualTo(1);
        assertThat(legacyEnrollmentUniqueIndexes).isZero();
        assertThat(enrollmentClassUserStatusIndexes).isEqualTo(1);
    }
}
