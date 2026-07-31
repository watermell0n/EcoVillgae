package com.b201.ecovillage.classroom.application;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;

@Component
public class SetupPrecheckInvalidator {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SetupPrecheckInvalidator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public int invalidate(UUID classroomId) {
        if (!aiRunsTableExists()) {
            return 0;
        }
        Instant now = clock.instant();
        return jdbcTemplate.update(
            """
            UPDATE ai_runs
               SET status = 'EXPIRED',
                   state_version = state_version + 1,
                   status_changed_at = ?,
                   updated_at = ?
             WHERE classroom_id = ?
               AND run_type = 'SETUP_PRECHECK'
               AND status IN ('AWAITING_TEACHER', 'APPROVED', 'COMPLETED')
            """,
            now,
            now,
            classroomId
        );
    }

    private boolean aiRunsTableExists() {
        return jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            for (String tableName : new String[] {"ai_runs", "AI_RUNS"}) {
                try (ResultSet tables = connection.getMetaData().getTables(
                    null,
                    null,
                    tableName,
                    new String[] {"TABLE"}
                )) {
                    if (tables.next()) {
                        return true;
                    }
                }
            }
            return false;
        });
    }
}
