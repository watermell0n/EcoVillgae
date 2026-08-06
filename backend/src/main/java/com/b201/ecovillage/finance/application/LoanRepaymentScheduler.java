package com.b201.ecovillage.finance.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanRepaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoanRepaymentScheduler.class);

    private final JdbcTemplate jdbc;
    private final LoanRepaymentProcessor processor;

    public LoanRepaymentScheduler(JdbcTemplate jdbc, LoanRepaymentProcessor processor) {
        this.jdbc = jdbc;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${ecovillage.finance.loan-repayment-delay-ms:60000}")
    public void repayDueLoans() {
        for (UUID classroomId : jdbc.query(
            "select distinct classroom_id from loans where status in ('ACTIVE','OVERDUE')",
            (rs, rowNum) -> rs.getObject(1, UUID.class)
        )) {
            try {
                processor.processClassroom(classroomId);
            } catch (RuntimeException exception) {
                log.error("Failed to process loan repayments for classroom {}", classroomId, exception);
            }
        }
    }
}
