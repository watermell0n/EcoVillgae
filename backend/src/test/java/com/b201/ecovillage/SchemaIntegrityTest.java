package com.b201.ecovillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class SchemaIntegrityTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("ecovillage")
            .withUsername("ecovillage")
            .withPassword("ecovillage");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    @Test
    @DisplayName("DBT-000: Flyway V1_1~V1_9 creates all 83 physical tables")
    void allMigrationsCreateEightyThreeTables() throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT count(*)
                   FROM information_schema.tables
                  WHERE table_schema = 'public'
                    AND table_type = 'BASE TABLE'
                    AND table_name <> 'flyway_schema_history'
                 """);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(83, result.getInt(1));
        }
    }

    @Test
    @DisplayName("DBT-001: cross-classroom student references are rejected")
    void crossClassroomStudentReferenceIsRejected() throws SQLException {
        Seed first = seed(true);
        Seed second = seed(false);

        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO student_credentials
                        (classroom_student_id, classroom_id, password_hash)
                    VALUES (?, ?, ?)
                    """, first.studentId(), second.classroomId(), "argon2id-hash");
            }
        });
    }

    @Test
    @DisplayName("DBT-002: a classroom cannot have two active owners")
    void duplicateActiveOwnerIsRejected() throws SQLException {
        Seed seed = seed(false);
        UUID firstUser = createUser();
        UUID secondUser = createUser();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO classroom_teachers
                    (id, classroom_id, user_id, role)
                VALUES (?, ?, ?, 'OWNER')
                """, uuid(), seed.classroomId(), firstUser);
        }

        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO classroom_teachers
                        (id, classroom_id, user_id, role)
                    VALUES (?, ?, ?, 'OWNER')
                    """, uuid(), seed.classroomId(), secondUser);
            }
        });
    }

    @Test
    @DisplayName("DBT-003: active roster numbers and nicknames are unique")
    void duplicateActiveStudentIdentityIsRejected() throws SQLException {
        Seed seed = seed(true);
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO classroom_students
                        (id, classroom_id, roster_number, nickname, status)
                    VALUES (?, ?, 1, 'another-name', 'ACTIVE')
                    """, uuid(), seed.classroomId());
            }
        });
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO classroom_students
                        (id, classroom_id, roster_number, nickname, status)
                    VALUES (?, ?, 2, ?, 'ACTIVE')
                    """, uuid(), seed.classroomId(), seed.nickname().toUpperCase());
            }
        });
    }

    @Test
    @DisplayName("DBT-004: each student has at most one active session")
    void duplicateActiveStudentSessionIsRejected() throws SQLException {
        Seed seed = seed(true);
        try (Connection connection = connection()) {
            insertSession(connection, seed, "token-a");
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                insertSession(connection, seed, "token-b");
            }
        });
    }

    @Test
    @DisplayName("DBT-005: a student wallet cannot have a negative balance")
    void negativeStudentWalletIsRejected() throws SQLException {
        Seed seed = seed(true);
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO ledger_accounts
                        (id, classroom_id, owner_type, classroom_student_id,
                         account_type, current_balance, status)
                    VALUES (?, ?, 'STUDENT', ?, 'STUDENT_WALLET', -1, 'ACTIVE')
                    """, uuid(), seed.classroomId(), seed.studentId());
            }
        });
    }

    @Test
    @DisplayName("DBT-006: an unbalanced ledger group cannot commit")
    void unbalancedCommittedLedgerIsRejected() throws SQLException {
        Seed seed = seed(false);
        LedgerAccounts accounts = createLedgerAccounts(seed.classroomId());

        assertCommitFailure(connection -> {
            UUID group = insertTransactionGroup(connection, seed.classroomId(), "VALIDATING", null);
            insertEntry(connection, seed.classroomId(), group, accounts.first(), 1, 100, 0, 100);
            insertEntry(connection, seed.classroomId(), group, accounts.second(), 1, -50, 0, -50);
            setAccountSnapshot(connection, accounts.first(), 100, 2);
            setAccountSnapshot(connection, accounts.second(), -50, 2);
            execute(connection, """
                UPDATE transaction_groups
                   SET status = 'COMMITTED', committed_at = now(), state_version = 1
                 WHERE id = ?
                """, group);
        });
    }

    @Test
    @DisplayName("DBT-007: stale account sequence or balance cannot commit")
    void staleLedgerAccountSnapshotIsRejected() throws SQLException {
        Seed seed = seed(false);
        LedgerAccounts accounts = createLedgerAccounts(seed.classroomId());

        assertCommitFailure(connection -> {
            UUID group = insertTransactionGroup(connection, seed.classroomId(), "VALIDATING", null);
            insertEntry(connection, seed.classroomId(), group, accounts.first(), 1, 100, 0, 100);
            insertEntry(connection, seed.classroomId(), group, accounts.second(), 1, -100, 0, -100);
            execute(connection, """
                UPDATE transaction_groups
                   SET status = 'COMMITTED', committed_at = now(), state_version = 1
                 WHERE id = ?
                """, group);
        });
    }

    @Test
    @DisplayName("DBT-008: policy snapshots and ledger entries are immutable")
    void immutableFinancialEvidenceCannotBeChanged() throws SQLException {
        Seed seed = seed(false);
        UUID snapshot = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO policy_snapshots
                    (id, classroom_id, snapshot_hash, schema_version,
                     calculated_values, rounding_rule)
                VALUES (?, ?, ?, '1', '{}'::jsonb, 'HALF_UP')
                """, snapshot, seed.classroomId(), "hash-" + snapshot);
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    UPDATE policy_snapshots
                       SET calculated_values = '{"tax": 999}'::jsonb
                     WHERE id = ?
                    """, snapshot);
            }
        });
    }

    @Test
    @DisplayName("DBT-009: recovery transactions preserve the original root")
    void recoveryTransactionPreservesRoot() throws SQLException {
        Seed seed = seed(false);
        LedgerAccounts accounts = createLedgerAccounts(seed.classroomId());
        UUID original;
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            original = insertTransactionGroup(connection, seed.classroomId(), "VALIDATING", null);
            insertEntry(connection, seed.classroomId(), original, accounts.first(), 1, 100, 0, 100);
            insertEntry(connection, seed.classroomId(), original, accounts.second(), 1, -100, 0, -100);
            setAccountSnapshot(connection, accounts.first(), 100, 2);
            setAccountSnapshot(connection, accounts.second(), -100, 2);
            execute(connection, """
                UPDATE transaction_groups
                   SET status = 'COMMITTED', committed_at = now(), state_version = 1
                 WHERE id = ?
                """, original);
            connection.commit();
        }

        UUID recovery;
        try (Connection connection = connection()) {
            recovery = insertTransactionGroup(
                connection, seed.classroomId(), "VALIDATING", original);
            assertEquals(original, queryUuid(connection, """
                SELECT root_transaction_group_id
                  FROM transaction_groups
                 WHERE id = ?
                """, recovery));
        }

        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    UPDATE transaction_groups SET reason = 'edit original' WHERE id = ?
                    """, original);
            }
        });
    }

    @Test
    @DisplayName("DBT-010: the database prevents duplicate idempotency keys")
    void duplicateIdempotencyKeyIsRejected() throws SQLException {
        Seed seed = seed(false);
        UUID actor = uuid();
        try (Connection connection = connection()) {
            insertIdempotency(connection, seed.classroomId(), actor, "same-hash");
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                insertIdempotency(connection, seed.classroomId(), actor, "different-hash");
            }
        });
    }

    @Test
    @DisplayName("DBT-011: one item cannot be locked by two active trades")
    void itemCannotBelongToTwoActiveTrades() throws SQLException {
        Seed seed = seed(true);
        UUID counterpart = createStudent(seed.classroomId(), 2, "counterpart-" + shortId());
        UUID character = createCharacter();
        UUID firstTrade = insertTrade(seed.classroomId(), seed.studentId(), counterpart);
        UUID item = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO inventory_items
                    (id, classroom_id, classroom_student_id, character_catalog_id,
                     locked_trade_id, status)
                VALUES (?, ?, ?, ?, ?, 'LOCKED_TRADE')
                """, item, seed.classroomId(), seed.studentId(), character, firstTrade);
            insertTradeItemLeg(
                connection, seed.classroomId(), firstTrade, item, seed.studentId(), counterpart);
        }
        UUID secondTrade = insertTrade(seed.classroomId(), seed.studentId(), counterpart);

        assertCommitFailure(connection -> insertTradeItemLeg(
            connection, seed.classroomId(), secondTrade, item, seed.studentId(), counterpart));
    }

    @Test
    @DisplayName("DBT-012: negative stock and invalid domain quantities are rejected")
    void negativeDomainQuantityIsRejected() throws SQLException {
        Seed seed = seed(false);
        UUID product = createStoreProduct(seed);
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO store_stocks
                        (store_product_id, classroom_id, available_quantity, reserved_quantity)
                    VALUES (?, ?, -1, 0)
                    """, product, seed.classroomId());
            }
        });
    }

    @Test
    @DisplayName("DBT-013: published product versions keep business fields immutable")
    void publishedVersionBusinessFieldsAreImmutable() throws SQLException {
        Seed seed = seed(false);
        UUID product = createStoreProduct(seed);
        UUID version = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO store_product_versions
                    (id, classroom_id, store_product_id, version, product_kind,
                     unit_price, vat_rate_bps, published_at)
                VALUES (?, ?, ?, 1, 'DIGITAL', 100, 1000, now())
                """, version, seed.classroomId(), product);
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    UPDATE store_product_versions SET unit_price = 200 WHERE id = ?
                    """, version);
            }
        });
    }

    @Test
    @DisplayName("DBT-014: an eligibility token can cast only one ballot")
    void duplicateVoteTokenIsRejected() throws SQLException {
        Seed seed = seed(true);
        UUID activity = createActivity(seed);
        UUID option = uuid();
        String token = "eligibility-" + uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO votes
                    (activity_session_id, classroom_id, title, status)
                VALUES (?, ?, '공동기금', 'OPEN')
                """, activity, seed.classroomId());
            execute(connection, """
                INSERT INTO vote_options
                    (id, classroom_id, vote_id, label, sort_order)
                VALUES (?, ?, ?, '도서 구매', 0)
                """, option, seed.classroomId(), activity);
            execute(connection, """
                INSERT INTO vote_eligibilities
                    (id, classroom_id, vote_id, classroom_student_id,
                     eligibility_token_hash)
                VALUES (?, ?, ?, ?, ?)
                """, uuid(), seed.classroomId(), activity, seed.studentId(), token);
            insertBallot(connection, seed.classroomId(), activity, option, token);
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                insertBallot(connection, seed.classroomId(), activity, option, token);
            }
        });
    }

    @Test
    @DisplayName("DBT-015: outbox payload is immutable but delivery metadata can change")
    void outboxPayloadIsImmutable() throws SQLException {
        Seed seed = seed(false);
        UUID stream = uuid();
        UUID event = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO realtime_streams
                    (id, classroom_id, stream_type, status)
                VALUES (?, ?, 'CLASSROOM', 'ACTIVE')
                """, stream, seed.classroomId());
            execute(connection, """
                INSERT INTO outbox_events
                    (event_id, classroom_id, realtime_stream_id, channel_sequence,
                     event_type, aggregate_type, aggregate_id, state_version, payload)
                VALUES (?, ?, ?, 1, 'classroom.updated', 'CLASSROOM', ?, 1, '{"safe":true}')
                """, event, seed.classroomId(), stream, seed.classroomId());
            execute(connection, """
                UPDATE outbox_events
                   SET published_at = now(), publish_attempts = 1
                 WHERE event_id = ?
                """, event);
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    UPDATE outbox_events SET payload = '{"safe":false}' WHERE event_id = ?
                    """, event);
            }
        });
    }

    @Test
    @DisplayName("DBT-016: public projections exclude private finance and identity fields")
    void publicProjectionColumnsAreSafe() throws SQLException {
        Set<String> forbidden = Set.of(
            "classroom_student_id", "current_balance", "credit_score", "level",
            "outstanding_principal", "selected_option_key", "free_text",
            "eligibility_token_hash", "receipt_hash");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT column_name
                   FROM information_schema.columns
                  WHERE table_schema = 'public'
                    AND table_name LIKE 'public\\_%' ESCAPE '\\'
                 """);
             ResultSet result = statement.executeQuery()) {
            int columnCount = 0;
            while (result.next()) {
                columnCount++;
                assertFalse(forbidden.contains(result.getString(1)));
            }
            assertTrue(columnCount > 0);
        }
    }

    @Test
    @DisplayName("DBT-017: service write guard returns false for READ_ONLY classrooms")
    void readOnlyClassroomDoesNotAcceptNewOperations() throws SQLException {
        Seed seed = seed(false);
        try (Connection connection = connection()) {
            assertTrue(queryBoolean(connection, """
                SELECT classroom_accepts_new_operations(?)
                """, seed.classroomId()));
            execute(connection, """
                UPDATE classrooms
                   SET status = 'READ_ONLY', state_version = state_version + 1
                 WHERE id = ?
                """, seed.classroomId());
            assertFalse(queryBoolean(connection, """
                SELECT classroom_accepts_new_operations(?)
                """, seed.classroomId()));
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Seed seed(boolean withStudent) throws SQLException {
        UUID school = uuid();
        UUID classroom = uuid();
        UUID user = createUser();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO schools (id, school_code, name)
                VALUES (?, ?, '테스트 학교')
                """, school, "school-" + shortId());
            execute(connection, """
                INSERT INTO classrooms
                    (id, school_id, classroom_code, grade, scale_label, status)
                VALUES (?, ?, ?, 5, 'ONE_MONTH', 'ACTIVE')
                """, classroom, school, "class-" + shortId());
        }
        UUID student = null;
        String nickname = null;
        if (withStudent) {
            nickname = "student-" + shortId();
            student = createStudent(classroom, 1, nickname);
        }
        return new Seed(school, classroom, user, student, nickname);
    }

    private static UUID createUser() throws SQLException {
        UUID user = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO users
                    (id, external_subject, display_name, user_type)
                VALUES (?, ?, '테스트 교사', 'TEACHER')
                """, user, "subject-" + user);
        }
        return user;
    }

    private static UUID createStudent(UUID classroom, int roster, String nickname)
        throws SQLException {
        UUID student = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO classroom_students
                    (id, classroom_id, roster_number, nickname, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, student, classroom, roster, nickname);
        }
        return student;
    }

    private static void insertSession(Connection connection, Seed seed, String token)
        throws SQLException {
        execute(connection, """
            INSERT INTO student_sessions
                (id, classroom_id, classroom_student_id, token_hash, status, expires_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', now() + interval '1 hour')
            """, uuid(), seed.classroomId(), seed.studentId(), token + "-" + uuid());
    }

    private static LedgerAccounts createLedgerAccounts(UUID classroom) throws SQLException {
        UUID first = uuid();
        UUID second = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO ledger_accounts
                    (id, classroom_id, owner_type, system_owner_key, account_type, status)
                VALUES (?, ?, 'SYSTEM', ?, 'CLEARING', 'ACTIVE')
                """, first, classroom, "clearing-" + first);
            execute(connection, """
                INSERT INTO ledger_accounts
                    (id, classroom_id, owner_type, system_owner_key, account_type, status)
                VALUES (?, ?, 'SYSTEM', ?, 'CLEARING', 'ACTIVE')
                """, second, classroom, "clearing-" + second);
        }
        return new LedgerAccounts(first, second);
    }

    private static UUID insertTransactionGroup(
        Connection connection, UUID classroom, String status, UUID recoveryOf)
        throws SQLException {
        UUID group = uuid();
        execute(connection, """
            INSERT INTO transaction_groups
                (id, classroom_id, transaction_type, actor_type, recovery_of_group_id,
                 request_id, correlation_id, status)
            VALUES (?, ?, 'TEST', 'SYSTEM', ?, ?, ?, ?)
            """, group, classroom, recoveryOf, "request-" + group, "correlation-" + group, status);
        return group;
    }

    private static void insertEntry(
        Connection connection,
        UUID classroom,
        UUID group,
        UUID account,
        long sequence,
        long amount,
        long before,
        long after
    ) throws SQLException {
        execute(connection, """
            INSERT INTO ledger_entries
                (id, classroom_id, transaction_group_id, ledger_account_id,
                 account_sequence, amount, balance_before, balance_after)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, uuid(), classroom, group, account, sequence, amount, before, after);
    }

    private static void setAccountSnapshot(
        Connection connection, UUID account, long balance, long nextSequence)
        throws SQLException {
        execute(connection, """
            UPDATE ledger_accounts
               SET current_balance = ?, next_entry_sequence = ?, state_version = state_version + 1
             WHERE id = ?
            """, balance, nextSequence, account);
    }

    private static void insertIdempotency(
        Connection connection, UUID classroom, UUID actor, String hash)
        throws SQLException {
        execute(connection, """
            INSERT INTO idempotency_records
                (id, classroom_id, actor_id, route_key, idempotency_key,
                 request_hash, response_status, response_body, expires_at)
            VALUES (?, ?, ?, '/test', 'same-key', ?, 200, '{}'::jsonb,
                    now() + interval '1 day')
            """, uuid(), classroom, actor, hash);
    }

    private static UUID createCharacter() throws SQLException {
        UUID character = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO character_catalog
                    (id, character_code, name, source_title, source_url,
                     rights_status, rights_evidence_key, status)
                VALUES (?, ?, '테스트 캐릭터', 'Public Domain',
                        'https://example.invalid', 'VERIFIED', 'evidence/key', 'ACTIVE')
                """, character, "character-" + shortId());
        }
        return character;
    }

    private static UUID insertTrade(UUID classroom, UUID proposer, UUID counterpart)
        throws SQLException {
        UUID trade = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO trades
                    (id, classroom_id, proposer_student_id, counterpart_student_id, status)
                VALUES (?, ?, ?, ?, 'AWAITING_COUNTERPART')
                """, trade, classroom, proposer, counterpart);
        }
        return trade;
    }

    private static void insertTradeItemLeg(
        Connection connection,
        UUID classroom,
        UUID trade,
        UUID item,
        UUID from,
        UUID to
    ) throws SQLException {
        execute(connection, """
            INSERT INTO trade_item_legs
                (id, classroom_id, trade_id, inventory_item_id,
                 from_student_id, to_student_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """, uuid(), classroom, trade, item, from, to);
    }

    private static UUID createStoreProduct(Seed seed) throws SQLException {
        UUID product = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO store_products
                    (id, classroom_id, name, product_kind, created_by_user_id, status)
                VALUES (?, ?, ?, 'DIGITAL', ?, 'ACTIVE')
                """, product, seed.classroomId(), "product-" + shortId(), seed.userId());
        }
        return product;
    }

    private static UUID createActivity(Seed seed) throws SQLException {
        UUID activity = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO activity_sessions
                    (id, classroom_id, activity_type, created_by_user_id, status)
                VALUES (?, ?, 'VOTE', ?, 'LIVE')
                """, activity, seed.classroomId(), seed.userId());
        }
        return activity;
    }

    private static void insertBallot(
        Connection connection, UUID classroom, UUID vote, UUID option, String token)
        throws SQLException {
        execute(connection, """
            INSERT INTO vote_ballots
                (id, classroom_id, vote_id, vote_option_id,
                 eligibility_token_hash, receipt_hash)
            VALUES (?, ?, ?, ?, ?, ?)
            """, uuid(), classroom, vote, option, token, "receipt-" + uuid());
    }

    private static UUID queryUuid(Connection connection, String sql, Object value)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static boolean queryBoolean(Connection connection, String sql, Object value)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... values)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static void assertSqlFailure(SqlRunnable runnable) {
        assertThrows(SQLException.class, runnable::run);
    }

    private static void assertCommitFailure(SqlConsumer work) throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            work.accept(connection);
            assertThrows(SQLException.class, connection::commit);
            connection.rollback();
        }
    }

    private static UUID uuid() {
        return UUID.randomUUID();
    }

    private static String shortId() {
        return uuid().toString().substring(0, 8);
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    private record Seed(
        UUID schoolId,
        UUID classroomId,
        UUID userId,
        UUID studentId,
        String nickname
    ) {
    }

    private record LedgerAccounts(UUID first, UUID second) {
    }
}
