package com.b201.ecovillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

class SchemaIntegrityTest {

    static PostgreSQLContainer postgres;
    static Flyway flyway;
    static String jdbcUrl;
    static String databaseUsername;
    static String databasePassword;

    @BeforeAll
    static void migrate() {
        configureDatabase();
        flyway = Flyway.configure()
            .dataSource(jdbcUrl, databaseUsername, databasePassword)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load();
        flyway.migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static void configureDatabase() {
        String externalJdbcUrl = System.getenv("TEST_POSTGRES_JDBC_URL");
        if (externalJdbcUrl != null && !externalJdbcUrl.isBlank()) {
            requireSafeExternalDatabase(externalJdbcUrl);
            jdbcUrl = externalJdbcUrl;
            databaseUsername = requiredEnvironment("TEST_POSTGRES_USERNAME");
            databasePassword = requiredEnvironment("TEST_POSTGRES_PASSWORD");
            return;
        }

        assumeTrue(
            DockerClientFactory.instance().isDockerAvailable(),
            "Docker or a guarded external PostgreSQL test database is required."
        );
        postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("ecovillage")
            .withUsername("ecovillage")
            .withPassword("ecovillage");
        postgres.start();
        jdbcUrl = postgres.getJdbcUrl();
        databaseUsername = postgres.getUsername();
        databasePassword = postgres.getPassword();
    }

    private static void requireSafeExternalDatabase(String externalJdbcUrl) {
        if (!"true".equalsIgnoreCase(System.getenv("ALLOW_EXTERNAL_DB_CLEAN"))) {
            throw new IllegalStateException(
                "ALLOW_EXTERNAL_DB_CLEAN=true is required for the external DB test."
            );
        }
        if (!externalJdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Only a PostgreSQL JDBC URL is allowed.");
        }
        URI uri = URI.create(externalJdbcUrl.substring("jdbc:".length()));
        String databaseName = uri.getPath() == null
            ? ""
            : uri.getPath().replaceFirst("^/", "");
        boolean localHost = "localhost".equalsIgnoreCase(uri.getHost())
            || "127.0.0.1".equals(uri.getHost());
        if (!localHost || !"ecovillage_migration_test".equals(databaseName)) {
            throw new IllegalStateException(
                "External DB tests may clean only local ecovillage_migration_test."
            );
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required.");
        }
        return value;
    }

    @Test
    @DisplayName("DBT-000: Flyway V1_1~V1_33 creates all 100 physical tables")
    void allMigrationsCreateOneHundredTables() throws SQLException {
        assertEquals(100, physicalTableCount());
    }

    @Test
    @DisplayName("DBT-020: an isolated database can be cleaned and recreated from all migrations")
    void databaseCanBeRecreatedFromAllMigrations() throws SQLException {
        flyway.clean();
        assertEquals(0, physicalTableCount());
        assertEquals(33, flyway.migrate().migrationsExecuted);
        assertEquals(100, physicalTableCount());
    }

    @Test
    @DisplayName("DBT-023: loan installment status constraint is installed")
    void loanInstallmentStatusConstraintIsInstalled() throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT count(*)
                   FROM pg_constraint
                  WHERE conname = 'ck_loan_installments_status'
                 """);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    private static int physicalTableCount() throws SQLException {
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
            return result.getInt(1);
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
    @DisplayName("DBT-008: policy snapshot calculated values are immutable")
    void policySnapshotCalculatedValuesCannotBeChanged() throws SQLException {
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
    @DisplayName("DBT-012: negative store stock is rejected")
    void negativeStoreStockIsRejected() throws SQLException {
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
    @DisplayName("DBT-013: a published store product unit price is immutable")
    void publishedStoreProductUnitPriceIsImmutable() throws SQLException {
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
    @DisplayName("DBT-016: public projections enforce the approved GAME top-three boundary")
    void publicProjectionColumnsAreSafe() throws SQLException {
        Set<String> forbidden = Set.of(
            "classroom_student_id", "credit_score", "level",
            "outstanding_principal", "selected_option_key", "free_text",
            "eligibility_token_hash", "receipt_hash", "score", "correct_count",
            "total_response_time_ms");
        Set<String> leaderboardColumns = new HashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT table_name, column_name
                   FROM information_schema.columns
                  WHERE table_schema = 'public'
                    AND table_name LIKE 'public\\_%' ESCAPE '\\'
                 """);
             ResultSet result = statement.executeQuery()) {
            int columnCount = 0;
            while (result.next()) {
                columnCount++;
                String tableName = result.getString(1);
                String columnName = result.getString(2);
                assertFalse(forbidden.contains(columnName));
                if ("public_quiz_leaderboard".equals(tableName)) {
                    leaderboardColumns.add(columnName);
                }
            }
            assertTrue(columnCount > 0);
        }
        assertEquals(
            Set.of("classroom_id", "quiz_session_id", "rank", "nickname",
                "character_code", "character_name"),
            leaderboardColumns);

        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT definition
                   FROM pg_views
                  WHERE schemaname = 'public'
                    AND viewname = 'public_quiz_leaderboard'
                 """);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            String definition = result.getString(1);
            assertTrue(definition.contains("leaderboard_public"));
            assertTrue(definition.contains("rank >= 1"));
            assertTrue(definition.contains("rank <= 3"));
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

    @Test
    @DisplayName("DBT-018: classroom code and currency name are valid, global, and immutable")
    void classroomIdentityAndCurrencyNameAreProtected() throws SQLException {
        Seed seed = seed(false);
        UUID secondSchool = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO schools (id, school_code, name)
                VALUES (?, ?, '두 번째 학교')
                """, secondSchool, "school-" + shortId());
        }

        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO classrooms
                        (id, school_id, classroom_code, currency_name,
                         grade, scale_label, planned_student_count, display_name, status)
                    VALUES (?, ?, ?, '별', 5, 'ONE_MONTH', 30, '중복 코드 교실', 'ACTIVE')
                    """, uuid(), secondSchool, seed.classroomCode());
            }
        });
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO classrooms
                        (id, school_id, classroom_code, currency_name,
                         grade, scale_label, planned_student_count, display_name, status)
                    VALUES (?, ?, ?, '우리 반 돈', 5, 'ONE_MONTH', 30, '잘못된 화폐 교실', 'ACTIVE')
                    """, uuid(), secondSchool, classroomCode());
            }
        });
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO classrooms
                        (id, school_id, classroom_code, currency_name,
                         grade, scale_label, planned_student_count, display_name, status)
                    VALUES (?, ?, 'INVALID1', '별', 5, 'ONE_MONTH', 30, '잘못된 코드 교실', 'ACTIVE')
                    """, uuid(), secondSchool);
            }
        });
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    UPDATE classrooms SET currency_name = '별'
                     WHERE id = ?
                    """, seed.classroomId());
            }
        });
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    UPDATE classrooms SET classroom_code = ?
                     WHERE id = ?
                    """, classroomCode(), seed.classroomId());
            }
        });
    }

    @Test
    @DisplayName("DBT-019: MVP classroom planning, role, schedule, and school AI constraints are enforced")
    void mvpSchemaAlignmentConstraintsAreEnforced() throws SQLException {
        Seed first = seed(false);
        Seed second = seed(false);

        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    UPDATE classrooms SET planned_student_count = 0 WHERE id = ?
                    """, first.classroomId());
            }
        });

        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO salary_roles
                    (id, classroom_id, role_code, name, gross_amount, status)
                VALUES (?, ?, 'CLASS_PRESIDENT', '회장', 100, 'ACTIVE')
                """, uuid(), first.classroomId());
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO salary_roles
                        (id, classroom_id, role_code, name, gross_amount, status)
                    VALUES (?, ?, 'CLASS_PRESIDENT', '다른 이름', 200, 'ACTIVE')
                    """, uuid(), first.classroomId());
            }
        });

        UUID secondSchedule = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO classroom_modules
                    (id, classroom_id, module_code, status)
                VALUES (?, ?, 'QUIZ_GAME', 'ACTIVE')
                """, uuid(), first.classroomId());
            execute(connection, """
                INSERT INTO autonomous_schedules
                    (id, classroom_id, day_of_week, local_start_time,
                     local_end_time, status)
                VALUES (?, ?, 1, '09:00', '10:00', 'ACTIVE')
                """, secondSchedule, second.classroomId());
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO autonomous_schedule_modules
                        (id, classroom_id, autonomous_schedule_id, module_code)
                    VALUES (?, ?, ?, 'QUIZ_GAME')
                    """, uuid(), first.classroomId(), secondSchedule);
            }
        });

        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO school_ai_setting_versions
                    (id, school_id, version, enabled, reason,
                     effective_from, created_by_user_id)
                VALUES (?, ?, 1, false, '학교 정책', now(), ?)
                """, uuid(), first.schoolId(), first.userId());
        }
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO school_ai_setting_versions
                        (id, school_id, version, enabled, reason,
                         effective_from, created_by_user_id)
                    VALUES (?, ?, 2, true, '승인 정책', now(), ?)
                    """, uuid(), first.schoolId(), first.userId());
            }
        });
    }

    @Test
    @DisplayName("DBT-021: one profile per student is enforced")
    void characterProfileIdentityIsProtected() throws SQLException {
        Seed seed = seed(true);
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO student_character_profiles
                    (id, classroom_id, classroom_student_id)
                VALUES (?, ?, ?)
                """, uuid(), seed.classroomId(), seed.studentId());
        }

        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO student_character_profiles
                        (id, classroom_id, classroom_student_id)
                    VALUES (?, ?, ?)
                    """, uuid(), seed.classroomId(), seed.studentId());
            }
        });
    }

    @Test
    @DisplayName("DBT-022: equipment must reference an available item owned by the profile student")
    void characterEquipmentOwnershipAndTradeBoundaryAreProtected() throws SQLException {
        Seed first = seed(true);
        Seed second = seed(true);
        UUID firstProfile = insertCharacterProfile(first);
        UUID secondProfile = insertCharacterProfile(second);
        UUID character = createCharacter();
        UUID equippedItem = insertInventoryItem(first, character);

        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO character_equipment
                    (id, classroom_id, classroom_student_id, character_profile_id,
                     inventory_item_id, slot)
                VALUES (?, ?, ?, ?, ?, 'HAT')
                """, uuid(), first.classroomId(), first.studentId(), firstProfile, equippedItem);
        }

        UUID backgroundItem = insertInventoryItem(
            first,
            createCharacter("bg_test_" + shortId())
        );
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO character_equipment
                    (id, classroom_id, classroom_student_id, character_profile_id,
                     inventory_item_id, slot)
                VALUES (?, ?, ?, ?, ?, 'BACKGROUND')
                """, uuid(), first.classroomId(), first.studentId(),
                firstProfile, backgroundItem);
        }

        UUID counterpart = createStudent(
            first.classroomId(),
            2,
            "trade-counterpart-" + shortId()
        );
        UUID trade = insertTrade(
            first.classroomId(),
            first.studentId(),
            counterpart
        );
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    UPDATE inventory_items
                       SET status = 'LOCKED_TRADE', locked_trade_id = ?
                     WHERE id = ?
                    """, trade, equippedItem);
            }
        });

        UUID anotherOwnedItem = insertInventoryItem(first, character);
        assertSqlFailure(() -> {
            try (Connection connection = connection()) {
                execute(connection, """
                    INSERT INTO character_equipment
                        (id, classroom_id, classroom_student_id, character_profile_id,
                         inventory_item_id, slot)
                    VALUES (?, ?, ?, ?, ?, 'HAT')
                    """, uuid(), second.classroomId(), second.studentId(),
                    secondProfile, anotherOwnedItem);
            }
        });
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
            jdbcUrl, databaseUsername, databasePassword);
    }

    private static UUID insertCharacterProfile(Seed seed) throws SQLException {
        UUID profile = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO student_character_profiles
                    (id, classroom_id, classroom_student_id)
                VALUES (?, ?, ?)
                """, profile, seed.classroomId(), seed.studentId());
        }
        return profile;
    }

    private static UUID insertInventoryItem(Seed seed, UUID characterId)
        throws SQLException {
        UUID item = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO inventory_items
                    (id, classroom_id, classroom_student_id, character_catalog_id, status)
                VALUES (?, ?, ?, ?, 'AVAILABLE')
                """, item, seed.classroomId(), seed.studentId(), characterId);
        }
        return item;
    }

    private static Seed seed(boolean withStudent) throws SQLException {
        UUID school = uuid();
        UUID classroom = uuid();
        UUID user = createUser();
        String classroomCode = classroomCode();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO schools (id, school_code, name)
                VALUES (?, ?, '테스트 학교')
                """, school, "school-" + shortId());
            execute(connection, """
                INSERT INTO classrooms
                    (id, school_id, classroom_code, currency_name,
                     grade, scale_label, planned_student_count, display_name, status)
                VALUES (?, ?, ?, '에코', 5, 'ONE_MONTH', 30, '5학년 경제교실', 'ACTIVE')
                """, classroom, school, classroomCode);
        }
        UUID student = null;
        String nickname = null;
        if (withStudent) {
            nickname = "student-" + shortId();
            student = createStudent(classroom, 1, nickname);
        }
        return new Seed(school, classroom, user, student, nickname, classroomCode);
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
        return createCharacter("HAT_TEST_" + shortId());
    }

    private static UUID createCharacter(String characterCode) throws SQLException {
        UUID character = uuid();
        try (Connection connection = connection()) {
            execute(connection, """
                INSERT INTO character_catalog
                    (id, character_code, name, source_title, source_url,
                     rights_status, rights_evidence_key, status)
                VALUES (?, ?, '테스트 캐릭터', 'Public Domain',
                        'https://example.invalid', 'VERIFIED', 'evidence/key', 'ACTIVE')
                """, character, characterCode);
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

    private static String classroomCode() {
        String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        String source = uuid().toString().replace("-", "").toUpperCase();
        StringBuilder result = new StringBuilder(8);
        for (int index = 0; index < source.length() && result.length() < 8; index++) {
            char candidate = source.charAt(index);
            if (alphabet.indexOf(candidate) >= 0) {
                result.append(candidate);
            }
        }
        while (result.length() < 8) {
            result.append(alphabet.charAt(result.length()));
        }
        return result.toString();
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
        String nickname,
        String classroomCode
    ) {
    }

    private record LedgerAccounts(UUID first, UUID second) {
    }
}
