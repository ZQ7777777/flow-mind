CREATE TABLE IF NOT EXISTS mock_exchange (
    exchange_code VARCHAR(16) PRIMARY KEY,
    exchange_name VARCHAR(64) NOT NULL UNIQUE,
    source_prefix VARCHAR(4) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ENABLED', 'DISABLED')),
    sort_order INTEGER NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS mock_futures_account (
    account_no VARCHAR(32) PRIMARY KEY,
    customer_name VARCHAR(128) NOT NULL,
    account_status VARCHAR(16) NOT NULL CHECK (account_status IN ('NORMAL', 'DORMANT')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_mock_futures_account_customer
    ON mock_futures_account (customer_name, account_no);

CREATE TABLE IF NOT EXISTS mock_account_fund_snapshot (
    account_no VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    current_equity NUMERIC NOT NULL,
    available_funds NUMERIC NOT NULL,
    pledge_amount NUMERIC NOT NULL,
    actual_cash NUMERIC NOT NULL,
    snapshot_at TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (account_no, currency),
    FOREIGN KEY (account_no) REFERENCES mock_futures_account(account_no)
);

CREATE TABLE IF NOT EXISTS mock_account_exchange_fund_snapshot (
    account_no VARCHAR(32) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    pledge_amount NUMERIC NOT NULL,
    position_margin NUMERIC NOT NULL,
    snapshot_at TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (account_no, exchange_code),
    FOREIGN KEY (account_no) REFERENCES mock_futures_account(account_no),
    FOREIGN KEY (exchange_code) REFERENCES mock_exchange(exchange_code)
);

CREATE INDEX IF NOT EXISTS idx_mock_account_exchange_fund_exchange
    ON mock_account_exchange_fund_snapshot (exchange_code, account_no);

CREATE TABLE IF NOT EXISTS mock_account_trading_code (
    account_no VARCHAR(32) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    trading_code VARCHAR(64) NOT NULL,
    trading_status VARCHAR(16) NOT NULL CHECK (trading_status IN ('NORMAL', 'DORMANT')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (account_no, exchange_code, trading_code),
    FOREIGN KEY (account_no) REFERENCES mock_futures_account(account_no),
    FOREIGN KEY (exchange_code) REFERENCES mock_exchange(exchange_code)
);

CREATE INDEX IF NOT EXISTS idx_mock_account_trading_code_lookup
    ON mock_account_trading_code (account_no, exchange_code, trading_status, trading_code);

CREATE TABLE IF NOT EXISTS mock_futures_product (
    exchange_code VARCHAR(16) NOT NULL,
    product_code VARCHAR(32) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    product_type VARCHAR(16) NOT NULL CHECK (product_type IN ('FUTURES')),
    contract_multiplier INTEGER NOT NULL CHECK (contract_multiplier > 0),
    pledge_unit_quantity INTEGER NOT NULL CHECK (pledge_unit_quantity > 0),
    previous_settlement_price NUMERIC NOT NULL CHECK (previous_settlement_price > 0),
    data_source VARCHAR(32) NOT NULL CHECK (data_source IN ('HTML_EXTRACTED', 'DEMO_GENERATED')),
    enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)),
    source_key VARCHAR(64) NOT NULL UNIQUE,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (exchange_code, product_code),
    FOREIGN KEY (exchange_code) REFERENCES mock_exchange(exchange_code)
);

CREATE INDEX IF NOT EXISTS idx_mock_futures_product_query
    ON mock_futures_product (exchange_code, product_type, enabled, product_code);

CREATE INDEX IF NOT EXISTS idx_mock_futures_product_name
    ON mock_futures_product (product_name, product_code);
