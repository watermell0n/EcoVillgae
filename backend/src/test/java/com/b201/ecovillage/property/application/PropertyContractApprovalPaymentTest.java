package com.b201.ecovillage.property.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:property-approval-payment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.ai-monitor.enabled=false"
})
class PropertyContractApprovalPaymentTest {

    private static final Instant NOW = Instant.parse("2026-08-10T01:00:00Z");
    private static final String TEACHER_SUBJECT = "property-payment-teacher";
    private static final long INITIAL_BALANCE = 10_000L;
    private static final long SALE_PRICE = 3_000L;

    @Autowired PropertyService propertyService;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClassroomRepository classroomRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired ClassroomTeacherRepository teacherRepository;
    @Autowired ClassroomStudentRepository studentRepository;
    @Autowired ClassroomModuleRepository moduleRepository;
    @Autowired LedgerAccountRepository ledgerAccountRepository;
    @Autowired PolicySnapshotRepository snapshotRepository;
    @Autowired AtomicLedgerTransactionService transactionService;

    private UUID classroomId;
    private UUID studentId;
    private UUID teacherId;

    @BeforeEach
    void setUp() {
        createPropertyTables();
        classroomId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        teacherId = UUID.randomUUID();

        classroomRepository.saveAndFlush(Classroom.create(
            classroomId,
            UUID.randomUUID(),
            "PROP" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(),
            "ECO",
            6,
            CourseSize.ONE_MONTH,
            10,
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            "Asia/Seoul",
            NOW
        ));
        userRepository.saveAndFlush(UserAccount.createTeacher(
            teacherId,
            TEACHER_SUBJECT,
            "부동산 교사",
            NOW
        ));
        teacherRepository.saveAndFlush(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            ClassroomTeacherRole.OWNER,
            NOW
        ));
        studentRepository.saveAndFlush(ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "부동산 학생",
            NOW
        ));
        ClassroomModule module = ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "REAL_ESTATE",
            NOW
        );
        module.activate(Map.of(), NOW);
        moduleRepository.saveAndFlush(module);
        fundStudentWallet();
    }

    @Test
    void keepsStudentBalancePendingAndChargesOnlyWhenTeacherApproves() {
        Map<String, Object> property = propertyService.createProperty(
            TEACHER_SUBJECT,
            classroomId,
            new PropertyService.PropertyInput(
                "A1", "A1 자리", 1, 1, "GENERAL", true, true,
                false, false, true, false, SALE_PRICE, 0, 0, 7, 28
            )
        );
        UUID propertyId = (UUID) property.get("property_id");

        Map<String, Object> requested = propertyService.requestContract(
            studentId.toString(),
            propertyId,
            new PropertyService.ContractInput("SALE", null)
        );
        UUID contractId = (UUID) requested.get("contract_id");

        assertEquals("PENDING", requested.get("status"));
        assertEquals(INITIAL_BALANCE, studentWalletBalance());
        assertNull(timestamp("select paid_at from property_contracts where id=?", contractId));
        assertEquals("TEACHER", jdbc.queryForObject(
            "select owner_kind from properties where id=?",
            String.class,
            propertyId
        ));

        Map<String, Object> approved = propertyService.decideContract(
            TEACHER_SUBJECT,
            contractId,
            true,
            "승인",
            UUID.randomUUID().toString()
        );

        assertEquals("COMPLETED", approved.get("status"));
        assertEquals(INITIAL_BALANCE - SALE_PRICE, studentWalletBalance());
        assertEquals(SALE_PRICE, propertyRevenueBalance());
        assertNotNull(timestamp("select paid_at from property_contracts where id=?", contractId));
        assertEquals(studentId, jdbc.queryForObject(
            "select owner_student_id from properties where id=?",
            UUID.class,
            propertyId
        ));
    }

    private long studentWalletBalance() {
        return ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                studentId,
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseThrow()
            .getCurrentBalance();
    }

    private long propertyRevenueBalance() {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                "PROPERTY_REVENUE",
                LedgerAccountType.PROPERTY_REVENUE,
                LedgerAccountStatus.ACTIVE
            )
            .orElseThrow()
            .getCurrentBalance();
    }

    private Instant timestamp(String sql, UUID id) {
        return jdbc.query(sql, result -> {
            if (!result.next() || result.getTimestamp(1) == null) return null;
            return result.getTimestamp(1).toInstant();
        }, id);
    }

    private void fundStudentWallet() {
        LedgerAccount wallet = ledgerAccountRepository.save(LedgerAccount.studentWallet(
            UUID.randomUUID(), classroomId, studentId, NOW
        ));
        LedgerAccount issuance = ledgerAccountRepository.save(LedgerAccount.systemAccount(
            UUID.randomUUID(), classroomId, "MONEY_ISSUANCE", LedgerAccountType.MONEY_ISSUANCE, NOW
        ));
        PolicySnapshot snapshot = snapshotRepository.save(PolicySnapshot.capture(
            UUID.randomUUID(), classroomId, UUID.randomUUID().toString(), "1",
            Map.of("amount", INITIAL_BALANCE), "INTEGER", NOW
        ));
        transactionService.execute(LedgerTransactionCommand.original(
            classroomId,
            "TEST_ISSUANCE",
            LedgerActorType.SYSTEM,
            studentId,
            snapshot.getId(),
            "/test/property-funding",
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "property payment test funding",
            List.of(
                new LedgerTransactionPosting(wallet.getId(), INITIAL_BALANCE),
                new LedgerTransactionPosting(issuance.getId(), -INITIAL_BALANCE)
            ),
            NOW
        ));
    }

    private void createPropertyTables() {
        jdbc.execute("""
            create table if not exists properties (
                id uuid primary key,
                classroom_id uuid not null,
                coordinate varchar(20) not null,
                display_name varchar(120) not null,
                row_index integer not null,
                column_index integer not null,
                property_tier varchar(40) not null,
                is_public boolean not null,
                is_active boolean not null,
                allows_rent boolean not null,
                allows_jeonse boolean not null,
                allows_sale boolean not null,
                allows_subscription boolean not null,
                sale_price bigint not null,
                deposit_amount bigint not null,
                monthly_rent bigint not null,
                rent_payment_cycle_days integer not null,
                contract_term_days integer not null,
                owner_kind varchar(40) not null,
                owner_student_id uuid,
                status varchar(40) not null,
                subscription_offer_id uuid,
                subscription_winner_student_id uuid,
                state_version bigint default 0 not null,
                status_changed_at timestamp with time zone not null,
                created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null
            )
            """);
        jdbc.execute("""
            create table if not exists property_contracts (
                id uuid primary key,
                classroom_id uuid not null,
                property_id uuid not null,
                lease_listing_id uuid,
                subscription_offer_id uuid,
                subscription_application_id uuid,
                contract_type varchar(40) not null,
                occupant_student_id uuid not null,
                landlord_kind varchar(40) not null,
                landlord_student_id uuid,
                deposit_amount bigint not null,
                monthly_rent bigint not null,
                sale_price bigint not null,
                payment_cycle_days integer,
                contract_term_days integer,
                paid_at timestamp with time zone,
                status varchar(40) not null,
                decision_reason varchar(500),
                approved_by_user_id uuid,
                approved_at timestamp with time zone,
                started_at timestamp with time zone,
                ends_at timestamp with time zone,
                next_payment_at timestamp with time zone,
                terminated_at timestamp with time zone,
                state_version bigint default 0 not null,
                status_changed_at timestamp with time zone not null,
                created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null
            )
            """);
        jdbc.execute("""
            create table if not exists property_sale_listings (
                id uuid primary key,
                classroom_id uuid not null,
                property_id uuid not null,
                seller_student_id uuid not null,
                sale_price bigint not null,
                status varchar(40) not null,
                state_version bigint default 0 not null,
                created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null
            )
            """);
    }
}
