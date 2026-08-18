package com.flowmind.business.reference.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一账户演示参考数据的只读响应类型。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
public final class ReferenceDataResponses {

    private ReferenceDataResponses() {
    }

    @Data
    public static class FuturesAccountResponse {
        /** 期货账号。 */
        private String accountNo;
        /** 客户名称。 */
        private String customerName;
        /** 账户状态：NORMAL、DORMANT。 */
        private String accountStatus;
    }

    @Data
    public static class ExchangeResponse {
        /** 交易所代码：CFFEX、CZCE、DCE、GFEX。 */
        private String exchangeCode;
        /** 交易所中文名称。 */
        private String exchangeName;
        /** 原始品种文件使用的单字母前缀。 */
        private String sourcePrefix;
    }

    @Data
    public static class ExchangeFundResponse {
        /** 交易所代码。 */
        private String exchangeCode;
        /** 交易所中文名称。 */
        private String exchangeName;
        /** 该交易所质押金额，单位为人民币元。 */
        private BigDecimal pledgeAmount;
        /** 该交易所持仓保证金，单位为人民币元。 */
        private BigDecimal positionMargin;
    }

    @Data
    public static class AccountFundResponse {
        /** 期货账号。 */
        private String accountNo;
        /** 客户名称。 */
        private String customerName;
        /** 币种，目前演示数据仅支持 CNY。 */
        private String currency;
        /** 当前权益，单位为人民币元。 */
        private BigDecimal currentEquity;
        /** 可用资金，单位为人民币元。 */
        private BigDecimal availableFunds;
        /** 质押金额，单位为人民币元。 */
        private BigDecimal pledgeAmount;
        /** 实有货币资金，单位为人民币元。 */
        private BigDecimal actualCash;
        /** 数据快照时间，ISO-8601 文本。 */
        private String snapshotAt;
        /** 按交易所拆分的质押和持仓保证金。 */
        private List<ExchangeFundResponse> exchangeFunds = new ArrayList<ExchangeFundResponse>();
    }

    @Data
    public static class TradingCodeResponse {
        /** 期货账号。 */
        private String accountNo;
        /** 交易所代码。 */
        private String exchangeCode;
        /** 交易编码。 */
        private String tradingCode;
        /** 编码状态：NORMAL、DORMANT。 */
        private String tradingStatus;
    }

    @Data
    public static class FuturesProductResponse {
        /** 交易所代码。 */
        private String exchangeCode;
        /** 交易所中文名称。 */
        private String exchangeName;
        /** 品种代码。 */
        private String productCode;
        /** 品种名称，以品种数据 txt 为准。 */
        private String productName;
        /** 产品类型，目前固定为 FUTURES。 */
        private String productType;
        /** 合约乘数。 */
        private Integer contractMultiplier;
        /** 质押品单位数量。 */
        private Integer pledgeUnitQuantity;
        /** 昨结算价，最多四位小数。 */
        private BigDecimal previousSettlementPrice;
        /** 数据来源：HTML_EXTRACTED、DEMO_GENERATED。 */
        private String dataSource;
    }
}
