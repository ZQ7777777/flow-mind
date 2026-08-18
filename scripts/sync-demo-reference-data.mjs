import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { DatabaseSync } from "node:sqlite";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDir, "..");
const defaultDatabase = resolve(repositoryRoot, "data", "business-flow-local.db");
const defaultProducts = resolve(repositoryRoot, "doc", "example_process", "品种数据.txt");
const schemaPath = resolve(
  repositoryRoot,
  "business-base",
  "backend",
  "src",
  "main",
  "resources",
  "mock-data",
  "003_reference_data_schema.sql",
);

const exchanges = [
  { code: "CFFEX", name: "中金所", prefix: "J", order: 10 },
  { code: "CZCE", name: "郑商所", prefix: "Z", order: 20 },
  { code: "DCE", name: "大商所", prefix: "D", order: 30 },
  { code: "GFEX", name: "广期所", prefix: "G", order: 40 },
];

const accounts = [
  {
    accountNo: "80000188",
    customerName: "上海启明实业有限公司",
    funds: [32500000, 16800000, 7800000, 22400000],
    exchangeFunds: { DCE: [3200000, 14800000], CZCE: [2100000, 9600000] },
  },
  {
    accountNo: "80000236",
    customerName: "浙江远航贸易有限公司",
    funds: [18600000, 7200000, 5900000, 9100000],
    exchangeFunds: { DCE: [5100000, 11500000], CZCE: [3600000, 7800000] },
  },
  {
    accountNo: "80000621",
    customerName: "深圳华远投资有限公司",
    funds: [52600000, 24100000, 12600000, 31500000],
    exchangeFunds: { DCE: [7600000, 22600000], CZCE: [4800000, 13800000] },
  },
  {
    accountNo: "80000852",
    customerName: "北京恒盛资产管理有限公司",
    funds: [14300000, 3900000, 4700000, 6100000],
    exchangeFunds: { DCE: [3300000, 8200000], CZCE: [2900000, 6400000] },
  },
  {
    accountNo: "80001027",
    customerName: "江苏中禾供应链有限公司",
    funds: [27800000, 10900000, 6900000, 15200000],
    exchangeFunds: { DCE: [4400000, 13200000], CZCE: [3500000, 9900000] },
  },
];

const extractedProductDetails = new Map(Object.entries({
  "J-IF": [300, 1, 3952.6],
  "J-IC": [200, 1, 5846.2],
  "J-T": [10000, 1, 105.3864],
  "J-TF": [10000, 1, 104.822],
  "Z-CF": [5, 8, 14980],
  "Z-SR": [10, 20, 6125],
  "Z-TA": [5, 20, 4978],
  "Z-MA": [10, 10, 2543],
  "D-i": [100, 1, 842.5],
  "D-m": [10, 10, 3126],
  "D-y": [10, 10, 7854],
  "D-p": [10, 10, 8162],
  "G-si": [5, 5, 11345],
  "G-lc": [1, 1, 89750],
  "G-ps": [3, 1, 42180],
}));

const multiplierCandidates = [1, 5, 10, 20, 50, 100];
const unitCandidates = [1, 5, 10, 20];

function parseArguments(argv) {
  const result = { database: defaultDatabase, products: defaultProducts };
  for (let index = 0; index < argv.length; index += 1) {
    const name = argv[index];
    if (name !== "--database" && name !== "--products") {
      throw new Error(`未知参数: ${name}`);
    }
    const value = argv[index + 1];
    if (!value) throw new Error(`${name} 缺少路径参数`);
    result[name.slice(2)] = resolve(repositoryRoot, value);
    index += 1;
  }
  return result;
}

function parseProducts(path) {
  if (!existsSync(path)) throw new Error(`品种数据文件不存在: ${path}`);
  const prefixToExchange = new Map(exchanges.map((item) => [item.prefix, item.code]));
  const seen = new Set();
  const products = [];
  const lines = readFileSync(path, "utf8").replace(/^\uFEFF/, "").split(/\r?\n/);
  for (let lineNumber = 0; lineNumber < lines.length; lineNumber += 1) {
    const line = lines[lineNumber].trim();
    if (!line || line === "交易所-品种编号-品种名称") continue;
    const firstSeparator = line.indexOf("-");
    const secondSeparator = line.indexOf("-", firstSeparator + 1);
    if (firstSeparator <= 0 || secondSeparator <= firstSeparator + 1 || secondSeparator === line.length - 1) {
      throw new Error(`第 ${lineNumber + 1} 行格式错误: ${line}`);
    }
    const prefix = line.slice(0, firstSeparator).trim();
    const productCode = line.slice(firstSeparator + 1, secondSeparator).trim();
    const productName = line.slice(secondSeparator + 1).trim();
    const exchangeCode = prefixToExchange.get(prefix);
    if (!exchangeCode) throw new Error(`第 ${lineNumber + 1} 行交易所前缀不受支持: ${prefix}`);
    const sourceKey = `${prefix}-${productCode}`;
    if (seen.has(sourceKey)) throw new Error(`品种数据重复: ${sourceKey}`);
    seen.add(sourceKey);
    products.push({ exchangeCode, productCode, productName, sourceKey });
  }
  if (!products.length) throw new Error("品种数据为空");
  return products;
}

function generatedDetails(exchangeCode, productCode) {
  const digest = createHash("sha256").update(`${exchangeCode}:${productCode}`, "utf8").digest();
  return [
    multiplierCandidates[digest[0] % multiplierCandidates.length],
    unitCandidates[digest[1] % unitCandidates.length],
    Number((100 + (digest.readUInt32BE(2) % 999000000) / 10000).toFixed(4)),
  ];
}

function upsertReferenceData(database, products) {
  const schema = readFileSync(schemaPath, "utf8");
  database.exec("PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;");
  database.exec(schema);

  const upsertExchange = database.prepare(`
    INSERT INTO mock_exchange (exchange_code, exchange_name, source_prefix, status, sort_order)
    VALUES (?, ?, ?, 'ENABLED', ?)
    ON CONFLICT(exchange_code) DO UPDATE SET exchange_name = excluded.exchange_name,
      source_prefix = excluded.source_prefix, status = 'ENABLED', sort_order = excluded.sort_order,
      updated_at = datetime('now')`);
  const upsertAccount = database.prepare(`
    INSERT INTO mock_futures_account (account_no, customer_name, account_status)
    VALUES (?, ?, 'NORMAL')
    ON CONFLICT(account_no) DO UPDATE SET customer_name = excluded.customer_name,
      account_status = 'NORMAL', updated_at = datetime('now')`);
  const upsertFunds = database.prepare(`
    INSERT INTO mock_account_fund_snapshot
      (account_no, currency, current_equity, available_funds, pledge_amount, actual_cash, snapshot_at)
    VALUES (?, 'CNY', ?, ?, ?, ?, ?)
    ON CONFLICT(account_no, currency) DO UPDATE SET current_equity = excluded.current_equity,
      available_funds = excluded.available_funds, pledge_amount = excluded.pledge_amount,
      actual_cash = excluded.actual_cash, snapshot_at = excluded.snapshot_at,
      updated_at = datetime('now')`);
  const upsertExchangeFunds = database.prepare(`
    INSERT INTO mock_account_exchange_fund_snapshot
      (account_no, exchange_code, pledge_amount, position_margin, snapshot_at)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(account_no, exchange_code) DO UPDATE SET pledge_amount = excluded.pledge_amount,
      position_margin = excluded.position_margin, snapshot_at = excluded.snapshot_at,
      updated_at = datetime('now')`);
  const upsertTradingCode = database.prepare(`
    INSERT INTO mock_account_trading_code
      (account_no, exchange_code, trading_code, trading_status)
    VALUES (?, ?, ?, 'NORMAL')
    ON CONFLICT(account_no, exchange_code, trading_code) DO UPDATE SET
      trading_status = 'NORMAL', updated_at = datetime('now')`);
  const upsertProduct = database.prepare(`
    INSERT INTO mock_futures_product
      (exchange_code, product_code, product_name, product_type, contract_multiplier,
       pledge_unit_quantity, previous_settlement_price, data_source, enabled, source_key)
    VALUES (?, ?, ?, 'FUTURES', ?, ?, ?, ?, 1, ?)
    ON CONFLICT(exchange_code, product_code) DO UPDATE SET product_name = excluded.product_name,
      product_type = 'FUTURES', contract_multiplier = excluded.contract_multiplier,
      pledge_unit_quantity = excluded.pledge_unit_quantity,
      previous_settlement_price = excluded.previous_settlement_price,
      data_source = excluded.data_source, enabled = 1, source_key = excluded.source_key,
      updated_at = datetime('now')`);

  const snapshotAt = "2026-08-18T00:00:00+08:00";
  database.exec("BEGIN IMMEDIATE");
  try {
    for (const exchange of exchanges) {
      upsertExchange.run(exchange.code, exchange.name, exchange.prefix, exchange.order);
    }
    for (const account of accounts) {
      upsertAccount.run(account.accountNo, account.customerName);
      upsertFunds.run(account.accountNo, ...account.funds, snapshotAt);
      for (const [exchangeCode, values] of Object.entries(account.exchangeFunds)) {
        upsertExchangeFunds.run(account.accountNo, exchangeCode, ...values, snapshotAt);
      }
      for (const exchange of exchanges) {
        upsertTradingCode.run(account.accountNo, exchange.code, `${exchange.code}-${account.accountNo}`);
      }
    }
    database.exec("UPDATE mock_futures_product SET enabled = 0, updated_at = datetime('now') WHERE enabled <> 0");
    for (const product of products) {
      const extracted = extractedProductDetails.get(product.sourceKey);
      const [multiplier, unit, price] = extracted ?? generatedDetails(product.exchangeCode, product.productCode);
      upsertProduct.run(
        product.exchangeCode,
        product.productCode,
        product.productName,
        multiplier,
        unit,
        price,
        extracted ? "HTML_EXTRACTED" : "DEMO_GENERATED",
        product.sourceKey,
      );
    }
    database.exec("COMMIT");
  } catch (error) {
    database.exec("ROLLBACK");
    throw error;
  }
}

function scalar(database, sql) {
  return Number(database.prepare(sql).get().value);
}

export function synchronize(options = {}) {
  const databasePath = resolve(options.database ?? defaultDatabase);
  const productsPath = resolve(options.products ?? defaultProducts);
  mkdirSync(dirname(databasePath), { recursive: true });
  const products = parseProducts(productsPath);
  const database = new DatabaseSync(databasePath);
  try {
    upsertReferenceData(database, products);
    return {
      database: databasePath,
      productsSource: productsPath,
      exchanges: scalar(database, "SELECT COUNT(*) AS value FROM mock_exchange WHERE status = 'ENABLED'"),
      accounts: scalar(database, "SELECT COUNT(*) AS value FROM mock_futures_account WHERE account_status = 'NORMAL'"),
      tradingCodes: scalar(database, "SELECT COUNT(*) AS value FROM mock_account_trading_code"),
      products: scalar(database, "SELECT COUNT(*) AS value FROM mock_futures_product WHERE enabled = 1"),
      extractedProducts: scalar(database, "SELECT COUNT(*) AS value FROM mock_futures_product WHERE enabled = 1 AND data_source = 'HTML_EXTRACTED'"),
      generatedProducts: scalar(database, "SELECT COUNT(*) AS value FROM mock_futures_product WHERE enabled = 1 AND data_source = 'DEMO_GENERATED'"),
    };
  } finally {
    database.close();
  }
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : "";
if (invokedPath === fileURLToPath(import.meta.url)) {
  try {
    const result = synchronize(parseArguments(process.argv.slice(2)));
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  } catch (error) {
    process.stderr.write(`同步演示参考数据失败: ${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}
