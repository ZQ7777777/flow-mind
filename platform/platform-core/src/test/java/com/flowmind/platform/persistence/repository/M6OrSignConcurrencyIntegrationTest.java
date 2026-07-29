package com.flowmind.platform.persistence.repository;

import com.flowmind.platform.testsupport.SchemaTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that concurrent OR_SIGN contenders share one SQLite CAS winner.
 *
 * @author FlowMind
 * @since 2026-07-29
 */
class M6OrSignConcurrencyIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentOrSignCompletionsProduceExactlyOneWinner() throws Exception {
        String jdbcUrl = "jdbc:sqlite:"
                + tempDir.resolve("or-sign-concurrency.db").toAbsolutePath().toString().replace('\\', '/')
                + "?busy_timeout=10000";
        initialize(jdbcUrl);
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl));
        insertFixture(jdbc);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(contender(jdbcUrl, ready, start));
            Future<Integer> second = executor.submit(contender(jdbcUrl, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> outcomes = Arrays.asList(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));

            assertEquals(1, outcomes.get(0).intValue() + outcomes.get(1).intValue());
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "SELECT group_status FROM process_task_group WHERE id = ?",
                    String.class, "group-or-concurrent"));
            assertEquals(Long.valueOf(1L), jdbc.queryForObject(
                    "SELECT lock_version FROM process_task_group WHERE id = ?",
                    Long.class, "group-or-concurrent"));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> contender(final String jdbcUrl,
                                        final CountDownLatch ready,
                                        final CountDownLatch start) {
        return new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                TaskGroupRepository repository = new TaskGroupRepository(
                        new JdbcTemplate(new DriverManagerDataSource(jdbcUrl)));
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return Integer.valueOf(repository.completeOrSignGroup("group-or-concurrent", 0L));
            }
        };
    }

    private void initialize(String jdbcUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 10000");
            SchemaTestSupport.executeSchema(connection);
        }
    }

    private void insertFixture(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO process_definition "
                        + "(id, process_code, process_name, system_code, version, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "definition-or", "or-concurrent", "OR Sign Concurrency", "test",
                Integer.valueOf(1), "test");
        jdbc.update("INSERT INTO process_instance "
                        + "(id, definition_id, process_code, process_name, version, instance_title, "
                        + "starter_user_id, starter_user_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "instance-or", "definition-or", "or-concurrent", "OR Sign Concurrency",
                Integer.valueOf(1), "OR Sign Instance", "starter", "Starter");
        jdbc.update("INSERT INTO process_task_group "
                        + "(id, instance_id, node_code, group_type, total_count, completed_count, "
                        + "branch_state_json, group_status, lock_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "group-or-concurrent", "instance-or", "or-review", "OR_SIGN",
                Integer.valueOf(2), Integer.valueOf(0), "{}", "ACTIVE", Long.valueOf(0L));
    }
}
