package com.flowmind.business.reference;

import com.flowmind.business.reference.dto.ReferenceDataResponses.AccountFundResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesAccountResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesProductResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.TradingCodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceDataRepositoryTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private ReferenceDataRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("reference-repository.db"));
        new ReferenceDataSchemaInitializer(dataSource).initialize();
        jdbc = new JdbcTemplate(dataSource);
        repository = new ReferenceDataRepository(jdbc);
        seed();
    }

    @Test
    void queriesAccountsFundsAndTradingCodesWithStableOrderingAndPrecision() {
        List<FuturesAccountResponse> accounts = repository.findAccounts("%启明%");
        assertThat(accounts).extracting(FuturesAccountResponse::getAccountNo).containsExactly("80000188");

        AccountFundResponse funds = repository.findFund("80000188", "CNY").get();
        assertThat(funds.getCurrentEquity()).isEqualByComparingTo("32500000.1234");
        assertThat(repository.findExchangeFunds("80000188"))
                .extracting(item -> item.getExchangeCode()).containsExactly("DCE");

        List<TradingCodeResponse> codes = repository.findTradingCodes("80000188", "DCE");
        assertThat(codes).extracting(TradingCodeResponse::getTradingStatus)
                .containsExactly("NORMAL", "DORMANT");
    }

    @Test
    void filtersProductsByExchangeKeywordTypeAndEnabledState() {
        List<FuturesProductResponse> products = repository.findProducts("DCE", "FUTURES", "%铁矿%");
        assertThat(products).hasSize(1);
        assertThat(products.get(0).getProductCode()).isEqualTo("i");
        assertThat(products.get(0).getPreviousSettlementPrice()).isEqualByComparingTo("842.5000");
        assertThat(repository.findProducts("DCE", "FUTURES", null))
                .extracting(FuturesProductResponse::getProductCode).containsExactly("i");
    }

    private void seed() {
        jdbc.update("INSERT INTO mock_exchange "
                + "(exchange_code, exchange_name, source_prefix, status, sort_order) VALUES (?, ?, ?, ?, ?)",
                "DCE", "大商所", "D", "ENABLED", 30);
        jdbc.update("INSERT INTO mock_futures_account (account_no, customer_name, account_status) VALUES (?, ?, ?)",
                "80000188", "上海启明实业有限公司", "NORMAL");
        jdbc.update("INSERT INTO mock_account_fund_snapshot "
                        + "(account_no, currency, current_equity, available_funds, pledge_amount, actual_cash, snapshot_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "80000188", "CNY", "32500000.1234", "16800000.0000", "7800000.0000",
                "22400000.0000", "2026-08-18T00:00:00+08:00");
        jdbc.update("INSERT INTO mock_account_exchange_fund_snapshot "
                        + "(account_no, exchange_code, pledge_amount, position_margin, snapshot_at) VALUES (?, ?, ?, ?, ?)",
                "80000188", "DCE", "3200000", "14800000", "2026-08-18T00:00:00+08:00");
        jdbc.update("INSERT INTO mock_account_trading_code "
                        + "(account_no, exchange_code, trading_code, trading_status) VALUES (?, ?, ?, ?)",
                "80000188", "DCE", "DCE-80000188-D", "DORMANT");
        jdbc.update("INSERT INTO mock_account_trading_code "
                        + "(account_no, exchange_code, trading_code, trading_status) VALUES (?, ?, ?, ?)",
                "80000188", "DCE", "DCE-80000188", "NORMAL");
        jdbc.update("INSERT INTO mock_futures_product "
                        + "(exchange_code, product_code, product_name, product_type, contract_multiplier, "
                        + "pledge_unit_quantity, previous_settlement_price, data_source, enabled, source_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "DCE", "i", "铁矿石", "FUTURES", 100, 1, "842.5000", "HTML_EXTRACTED", 1, "D-i");
        jdbc.update("INSERT INTO mock_futures_product "
                        + "(exchange_code, product_code, product_name, product_type, contract_multiplier, "
                        + "pledge_unit_quantity, previous_settlement_price, data_source, enabled, source_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "DCE", "m", "豆粕", "FUTURES", 10, 10, "3126", "HTML_EXTRACTED", 0, "D-m");
    }
}
