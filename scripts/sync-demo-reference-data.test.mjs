import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import test from "node:test";
import { DatabaseSync } from "node:sqlite";
import { synchronize } from "./sync-demo-reference-data.mjs";

function cleanup(directory) {
  try {
    rmSync(directory, { recursive: true, force: true, maxRetries: 5, retryDelay: 50 });
  } catch (error) {
    // node:sqlite can retain the Windows file handle until process shutdown.
    if (!(error && error.code === "EBUSY")) throw error;
  }
}

test("synchronizes the complete deterministic reference dataset idempotently", () => {
  const directory = mkdtempSync(resolve(tmpdir(), "flowmind-reference-data-"));
  const databasePath = resolve(directory, "demo.db");
  try {
    const first = synchronize({ database: databasePath });
    const database = new DatabaseSync(databasePath);
    const generatedBefore = database.prepare(`
      SELECT contract_multiplier, pledge_unit_quantity, previous_settlement_price
      FROM mock_futures_product WHERE source_key = 'D-a'`).get();
    database.close();

    const second = synchronize({ database: databasePath });
    assert.deepEqual(
      {
        exchanges: second.exchanges,
        accounts: second.accounts,
        tradingCodes: second.tradingCodes,
        products: second.products,
        extractedProducts: second.extractedProducts,
        generatedProducts: second.generatedProducts,
      },
      { exchanges: 4, accounts: 5, tradingCodes: 20, products: 68, extractedProducts: 15, generatedProducts: 53 },
    );
    assert.equal(first.products, second.products);

    const verification = new DatabaseSync(databasePath);
    assert.deepEqual(
      verification.prepare(`
        SELECT contract_multiplier, pledge_unit_quantity, previous_settlement_price
        FROM mock_futures_product WHERE source_key = 'D-a'`).get(),
      generatedBefore,
    );
    const gfexPs = verification.prepare(
      "SELECT product_name, data_source FROM mock_futures_product WHERE source_key = 'G-ps'",
    ).get();
    assert.equal(gfexPs.product_name, "拟上市品种");
    assert.equal(gfexPs.data_source, "HTML_EXTRACTED");
    assert.equal(
      verification.prepare("SELECT current_equity FROM mock_account_fund_snapshot WHERE account_no = '80000188' AND currency = 'CNY'").get().current_equity,
      32500000,
    );
    verification.close();
  } finally {
    cleanup(directory);
  }
});

test("disables products removed from the source without deleting them", () => {
  const directory = mkdtempSync(resolve(tmpdir(), "flowmind-reference-data-disabled-"));
  const databasePath = resolve(directory, "demo.db");
  const productPath = resolve(directory, "products.txt");
  try {
    const repositoryProducts = resolve(process.cwd(), "doc", "example_process", "品种数据.txt");
    const source = readFileSync(repositoryProducts, "utf8");
    writeFileSync(productPath, source, "utf8");
    synchronize({ database: databasePath, products: productPath });
    writeFileSync(productPath, source.replace(/^D-a-.*\r?\n/m, ""), "utf8");
    synchronize({ database: databasePath, products: productPath });

    const database = new DatabaseSync(databasePath);
    assert.equal(
      database.prepare("SELECT enabled FROM mock_futures_product WHERE source_key = 'D-a'").get().enabled,
      0,
    );
    database.close();
  } finally {
    cleanup(directory);
  }
});
