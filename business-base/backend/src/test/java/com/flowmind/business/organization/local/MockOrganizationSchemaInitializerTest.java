package com.flowmind.business.organization.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MockOrganizationSchemaInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsIdempotentEntryApplicationOrganizationDataset() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("organization.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        MockOrganizationSchemaInitializer initializer = new MockOrganizationSchemaInitializer(dataSource);

        initializer.initialize();
        initializer.initialize();

        assertThat(count(jdbc, "SELECT COUNT(*) FROM mock_department")).isGreaterThanOrEqualTo(5);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM mock_user WHERE status = 1")).isGreaterThanOrEqualTo(30);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM mock_user WHERE status = 1 AND user_type = 'ADMIN'"))
                .isEqualTo(5);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM mock_user WHERE id = 'u_sales_01' AND dept_id = 'dept_sales'"))
                .isEqualTo(1);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM mock_department WHERE id = 'dept_sales' "
                + "AND manager_id = 'u_dept_manager_01'"))
                .isEqualTo(1);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM mock_department d LEFT JOIN mock_user u "
                + "ON u.id = d.manager_id WHERE d.manager_id IS NOT NULL AND u.id IS NULL"))
                .isZero();
    }

    private int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value.intValue();
    }
}
