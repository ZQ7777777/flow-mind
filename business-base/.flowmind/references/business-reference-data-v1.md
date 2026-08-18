# Business Reference Data API v1

Generated frontend code may call these same-origin, authenticated, read-only APIs. Never hardcode futures accounts,
exchanges, trading codes, or futures products when a confirmed form field declares a matching
`referenceDataSource`.

## Resource mapping

### FUTURES_ACCOUNTS

`GET /api/reference-data/futures-accounts?keyword={optional}`

Returns an array of:

```ts
interface FuturesAccountOption {
  accountNo: string;
  customerName: string;
  accountStatus: "NORMAL" | "DORMANT";
}
```

Use `accountNo` as the value and `${accountNo} - ${customerName}` as the label. Account changes must clear every
dependent trading-code value. The optional fund detail is queried with
`GET /api/reference-data/futures-accounts/{accountNo}/funds?currency=CNY` and contains `currentEquity`,
`availableFunds`, `pledgeAmount`, `actualCash`, `snapshotAt`, and `exchangeFunds[]` with `exchangeCode`,
`pledgeAmount`, and `positionMargin`.

### EXCHANGES

`GET /api/reference-data/exchanges`

Returns `{ exchangeCode, exchangeName, sourcePrefix }[]`. Use `exchangeCode` as the value and `exchangeName` as the
label. Exchange changes must clear trading-code and product selections, then reload both dependent resources.

### TRADING_CODES

`GET /api/reference-data/futures-accounts/{accountNo}/trading-codes?exchangeCode={exchangeCode}`

The `accountNo` and `exchangeCode` path/query values come from `parameterBindings`. Returns
`{ accountNo, exchangeCode, tradingCode, tradingStatus }[]`, where status is `NORMAL` or `DORMANT`. Use the first
item returned by the server's stable order for a single read-only trading-code field; leave it blank when no item
exists.

### FUTURES_PRODUCTS

`GET /api/reference-data/futures-products?exchangeCode={exchangeCode}&productType=FUTURES&keyword={optional}`

Returns an array of:

```ts
interface FuturesProductOption {
  exchangeCode: string;
  exchangeName: string;
  productCode: string;
  productName: string;
  productType: "FUTURES";
  contractMultiplier: number;
  pledgeUnitQuantity: number;
  previousSettlementPrice: number;
  dataSource: "HTML_EXTRACTED" | "DEMO_GENERATED";
}
```

Use `productCode` as the value and `${productCode} - ${productName}` as the label. For a multiple field, preserve a
`string[]`. When selections change, use the last selected product as the source for `autofillBindings`. Empty
selections clear the autofill targets. `DEMO_GENERATED` values are synthetic demonstration data and must not be
presented as live or production market data.

## Client behavior

- Use same-origin `fetch`; report loading and request failures in the generated page.
- URL-encode path and query values.
- Do not issue a dependent request until every required binding has a non-empty value.
- Clear stale dependent values before a new request and ignore responses belonging to superseded dependencies.
- These APIs are queries only. Submission still goes through the generated business submit endpoint, and business
  calculations remain frontend business logic derived from the confirmed requirement.
