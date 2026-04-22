package com.example.be_a.enrollment.infrastructure;

import com.example.be_a.enrollment.domain.EnrollmentStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EnrollmentCountRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public EnrollmentCountRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public boolean existsByClassId(Long classId) {
        String sql = """
            SELECT EXISTS(
                SELECT 1
                FROM enrollments
                WHERE class_id = :classId
            )
            """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("classId", classId);

        Boolean exists = namedParameterJdbcTemplate.queryForObject(sql, parameters, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public Map<Long, Long> countWaitingByClassIds(List<Long> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
            SELECT class_id, COUNT(*) AS waiting_count
            FROM enrollments
            WHERE status = :status
              AND class_id IN (:classIds)
            GROUP BY class_id
            """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("status", EnrollmentStatus.WAITING.name())
            .addValue("classIds", classIds);

        return namedParameterJdbcTemplate.query(sql, parameters, resultSet -> {
            Map<Long, Long> counts = new HashMap<>();
            while (resultSet.next()) {
                counts.put(resultSet.getLong("class_id"), resultSet.getLong("waiting_count"));
            }
            return counts;
        });
    }
}
