package com.b201.ecovillage.report.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.report.application.ClassroomReportAggregate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:classroom-report;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class ClassroomReportProjectionRepositoryTest {

    @Autowired
    private ClassroomReportProjectionRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createProjectionOnlyTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS savings_accounts (
                id uuid PRIMARY KEY,
                classroom_id uuid NOT NULL,
                principal_balance bigint NOT NULL,
                opened_at timestamp with time zone NOT NULL,
                closed_at timestamp with time zone
            )
            """);
    }

    @Test
    void returnsZeroesForAClassroomWithoutActivity() {
        ClassroomReportAggregate result = repository.aggregate(
            UUID.randomUUID(),
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z")
        );

        assertEquals(
            new ClassroomReportAggregate(0, 0, 0, 0, 0, 0, 0),
            result
        );
    }
}
