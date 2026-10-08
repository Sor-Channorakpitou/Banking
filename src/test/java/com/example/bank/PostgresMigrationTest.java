package com.example.bank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the migrations against a real PostgreSQL in Docker. H2's PostgreSQL mode is
 * close but not identical, so this catches SQL that only works on one of them.
 * Skipped automatically when Docker isn't available.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {

    // @ServiceConnection points the app's DataSource at this container,
    // overriding the H2 URL from application-test.yml.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsCreateAllTables() {
        Integer tables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'accounts', 'transactions', 'ledger_entries')
                """, Integer.class);

        assertThat(tables).isEqualTo(4);
    }
}
