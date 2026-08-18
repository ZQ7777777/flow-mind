package com.flowmind.business.reference;

import com.flowmind.business.reference.dto.ReferenceDataResponses.AccountFundResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.ExchangeFundResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.ExchangeResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesAccountResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesProductResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.TradingCodeResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 统一账户演示参考数据 SQLite 查询仓储。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Repository
public class ReferenceDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReferenceDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FuturesAccountResponse> findAccounts(String keywordPattern) {
        return jdbcTemplate.query("SELECT account_no, customer_name, account_status "
                        + "FROM mock_futures_account WHERE account_status IN ('NORMAL', 'DORMANT') "
                        + "AND (? IS NULL OR lower(account_no) LIKE ? ESCAPE '\\' "
                        + "OR lower(customer_name) LIKE ? ESCAPE '\\') "
                        + "ORDER BY account_no ASC LIMIT 50",
                accountMapper(), keywordPattern, keywordPattern, keywordPattern);
    }

    public Optional<FuturesAccountResponse> findAccount(String accountNo) {
        return first(jdbcTemplate.query("SELECT account_no, customer_name, account_status "
                        + "FROM mock_futures_account WHERE account_no = ?",
                accountMapper(), accountNo));
    }

    public Optional<AccountFundResponse> findFund(String accountNo, String currency) {
        return first(jdbcTemplate.query("SELECT f.account_no, a.customer_name, f.currency, f.current_equity, "
                        + "f.available_funds, f.pledge_amount, f.actual_cash, f.snapshot_at "
                        + "FROM mock_account_fund_snapshot f JOIN mock_futures_account a ON a.account_no = f.account_no "
                        + "WHERE f.account_no = ? AND f.currency = ?",
                new RowMapper<AccountFundResponse>() {
                    @Override
                    public AccountFundResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
                        AccountFundResponse response = new AccountFundResponse();
                        response.setAccountNo(rs.getString("account_no"));
                        response.setCustomerName(rs.getString("customer_name"));
                        response.setCurrency(rs.getString("currency"));
                        response.setCurrentEquity(rs.getBigDecimal("current_equity"));
                        response.setAvailableFunds(rs.getBigDecimal("available_funds"));
                        response.setPledgeAmount(rs.getBigDecimal("pledge_amount"));
                        response.setActualCash(rs.getBigDecimal("actual_cash"));
                        response.setSnapshotAt(rs.getString("snapshot_at"));
                        return response;
                    }
                }, accountNo, currency));
    }

    public List<ExchangeFundResponse> findExchangeFunds(String accountNo) {
        return jdbcTemplate.query("SELECT f.exchange_code, e.exchange_name, f.pledge_amount, f.position_margin "
                        + "FROM mock_account_exchange_fund_snapshot f "
                        + "JOIN mock_exchange e ON e.exchange_code = f.exchange_code "
                        + "WHERE f.account_no = ? ORDER BY e.sort_order ASC, f.exchange_code ASC",
                new RowMapper<ExchangeFundResponse>() {
                    @Override
                    public ExchangeFundResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
                        ExchangeFundResponse response = new ExchangeFundResponse();
                        response.setExchangeCode(rs.getString("exchange_code"));
                        response.setExchangeName(rs.getString("exchange_name"));
                        response.setPledgeAmount(rs.getBigDecimal("pledge_amount"));
                        response.setPositionMargin(rs.getBigDecimal("position_margin"));
                        return response;
                    }
                }, accountNo);
    }

    public List<TradingCodeResponse> findTradingCodes(String accountNo, String exchangeCode) {
        return jdbcTemplate.query("SELECT account_no, exchange_code, trading_code, trading_status "
                        + "FROM mock_account_trading_code WHERE account_no = ? AND exchange_code = ? "
                        + "AND trading_status IN ('NORMAL', 'DORMANT') "
                        + "ORDER BY CASE trading_status WHEN 'NORMAL' THEN 0 ELSE 1 END, trading_code ASC",
                new RowMapper<TradingCodeResponse>() {
                    @Override
                    public TradingCodeResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
                        TradingCodeResponse response = new TradingCodeResponse();
                        response.setAccountNo(rs.getString("account_no"));
                        response.setExchangeCode(rs.getString("exchange_code"));
                        response.setTradingCode(rs.getString("trading_code"));
                        response.setTradingStatus(rs.getString("trading_status"));
                        return response;
                    }
                }, accountNo, exchangeCode);
    }

    public List<ExchangeResponse> findExchanges() {
        return jdbcTemplate.query("SELECT exchange_code, exchange_name, source_prefix FROM mock_exchange "
                + "WHERE status = 'ENABLED' ORDER BY sort_order ASC, exchange_code ASC", exchangeMapper());
    }

    public Optional<ExchangeResponse> findExchange(String exchangeCode) {
        return first(jdbcTemplate.query("SELECT exchange_code, exchange_name, source_prefix FROM mock_exchange "
                + "WHERE exchange_code = ? AND status = 'ENABLED'", exchangeMapper(), exchangeCode));
    }

    public List<FuturesProductResponse> findProducts(String exchangeCode, String productType,
                                                       String keywordPattern) {
        return jdbcTemplate.query("SELECT p.exchange_code, e.exchange_name, p.product_code, p.product_name, "
                        + "p.product_type, p.contract_multiplier, p.pledge_unit_quantity, "
                        + "p.previous_settlement_price, p.data_source "
                        + "FROM mock_futures_product p JOIN mock_exchange e ON e.exchange_code = p.exchange_code "
                        + "WHERE p.exchange_code = ? AND p.product_type = ? AND p.enabled = 1 "
                        + "AND (? IS NULL OR lower(p.product_code) LIKE ? ESCAPE '\\' "
                        + "OR lower(p.product_name) LIKE ? ESCAPE '\\') "
                        + "ORDER BY lower(p.product_code) ASC, p.product_name ASC LIMIT 200",
                new RowMapper<FuturesProductResponse>() {
                    @Override
                    public FuturesProductResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
                        FuturesProductResponse response = new FuturesProductResponse();
                        response.setExchangeCode(rs.getString("exchange_code"));
                        response.setExchangeName(rs.getString("exchange_name"));
                        response.setProductCode(rs.getString("product_code"));
                        response.setProductName(rs.getString("product_name"));
                        response.setProductType(rs.getString("product_type"));
                        response.setContractMultiplier(Integer.valueOf(rs.getInt("contract_multiplier")));
                        response.setPledgeUnitQuantity(Integer.valueOf(rs.getInt("pledge_unit_quantity")));
                        response.setPreviousSettlementPrice(rs.getBigDecimal("previous_settlement_price"));
                        response.setDataSource(rs.getString("data_source"));
                        return response;
                    }
                }, exchangeCode, productType, keywordPattern, keywordPattern, keywordPattern);
    }

    private RowMapper<FuturesAccountResponse> accountMapper() {
        return new RowMapper<FuturesAccountResponse>() {
            @Override
            public FuturesAccountResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
                FuturesAccountResponse response = new FuturesAccountResponse();
                response.setAccountNo(rs.getString("account_no"));
                response.setCustomerName(rs.getString("customer_name"));
                response.setAccountStatus(rs.getString("account_status"));
                return response;
            }
        };
    }

    private RowMapper<ExchangeResponse> exchangeMapper() {
        return new RowMapper<ExchangeResponse>() {
            @Override
            public ExchangeResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
                ExchangeResponse response = new ExchangeResponse();
                response.setExchangeCode(rs.getString("exchange_code"));
                response.setExchangeName(rs.getString("exchange_name"));
                response.setSourcePrefix(rs.getString("source_prefix"));
                return response;
            }
        };
    }

    private <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.<T>empty() : Optional.of(rows.get(0));
    }
}
