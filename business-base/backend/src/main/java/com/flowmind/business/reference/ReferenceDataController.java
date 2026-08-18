package com.flowmind.business.reference;

import com.flowmind.business.reference.dto.ReferenceDataResponses.AccountFundResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.ExchangeResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesAccountResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesProductResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.TradingCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 生成业务页面可调用的统一账户演示参考数据接口。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@RestController
@RequestMapping("/api/reference-data")
public class ReferenceDataController {

    private final ReferenceDataService service;

    public ReferenceDataController(ReferenceDataService service) {
        this.service = service;
    }

    @GetMapping("/futures-accounts")
    public List<FuturesAccountResponse> accounts(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return service.accounts(keyword);
    }

    @GetMapping("/futures-accounts/{accountNo}/funds")
    public AccountFundResponse funds(@PathVariable("accountNo") String accountNo,
                                     @RequestParam(value = "currency", required = false,
                                             defaultValue = "CNY") String currency) {
        return service.funds(accountNo, currency);
    }

    @GetMapping("/futures-accounts/{accountNo}/trading-codes")
    public List<TradingCodeResponse> tradingCodes(
            @PathVariable("accountNo") String accountNo,
            @RequestParam("exchangeCode") String exchangeCode) {
        return service.tradingCodes(accountNo, exchangeCode);
    }

    @GetMapping("/exchanges")
    public List<ExchangeResponse> exchanges() {
        return service.exchanges();
    }

    @GetMapping("/futures-products")
    public List<FuturesProductResponse> products(
            @RequestParam("exchangeCode") String exchangeCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "productType", required = false,
                    defaultValue = "FUTURES") String productType) {
        return service.products(exchangeCode, keyword, productType);
    }
}
