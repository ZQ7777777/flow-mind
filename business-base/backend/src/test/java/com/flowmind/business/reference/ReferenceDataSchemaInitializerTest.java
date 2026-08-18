package com.flowmind.business.reference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceDataSchemaInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsAllReferenceTablesIdempotentlyWithoutSeedingData() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("reference-schema.db"));
        ReferenceDataSchemaInitializer initializer = new ReferenceDataSchemaInitializer(dataSource);

        initializer.initialize();
        initializer.initialize();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tables = jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                + "AND name IN ('mock_exchange', 'mock_futures_account', 'mock_account_fund_snapshot', "
                + "'mock_account_exchange_fund_snapshot', 'mock_account_trading_code', "
                + "'mock_futures_product')", Integer.class);
        assertThat(tables).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mock_futures_product", Integer.class)).isZero();
    }
}
