package com.flowmind.business.reference;

import com.flowmind.business.common.BusinessApiException;
import com.flowmind.business.reference.dto.ReferenceDataResponses.AccountFundResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.ExchangeResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesAccountResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesProductResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.TradingCodeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 统一账户演示参考数据查询与参数校验服务。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Service
public class ReferenceDataService {

    private final ReferenceDataRepository repository;

    public ReferenceDataService(ReferenceDataRepository repository) {
        this.repository = repository;
    }

    public List<FuturesAccountResponse> accounts(String keyword) {
        return repository.findAccounts(keywordPattern(keyword));
    }

    public AccountFundResponse funds(String accountNo, String currency) {
        String normalizedAccount = requireText(accountNo, "accountNo", 32);
        requireAccount(normalizedAccount);
        String normalizedCurrency = optionalText(currency, "currency", 8);
        if (normalizedCurrency == null) {
            normalizedCurrency = "CNY";
        } else {
            normalizedCurrency = normalizedCurrency.toUpperCase(Locale.ROOT);
        }
        if (!"CNY".equals(normalizedCurrency)) {
            throw invalid("演示资金数据仅支持 CNY");
        }
        Optional<AccountFundResponse> response = repository.findFund(normalizedAccount, normalizedCurrency);
        if (!response.isPresent()) {
            throw new BusinessApiException(HttpStatus.NOT_FOUND, "REFERENCE_FUND_SNAPSHOT_NOT_FOUND",
                    "账户资金快照不存在");
        }
        response.get().setExchangeFunds(repository.findExchangeFunds(normalizedAccount));
        return response.get();
    }

    public List<TradingCodeResponse> tradingCodes(String accountNo, String exchangeCode) {
        String normalizedAccount = requireText(accountNo, "accountNo", 32);
        requireAccount(normalizedAccount);
        String normalizedExchange = normalizeExchange(exchangeCode);
        requireExchange(normalizedExchange);
        return repository.findTradingCodes(normalizedAccount, normalizedExchange);
    }

    public List<ExchangeResponse> exchanges() {
        return repository.findExchanges();
    }

    public List<FuturesProductResponse> products(String exchangeCode, String keyword, String productType) {
        String normalizedExchange = normalizeExchange(exchangeCode);
        requireExchange(normalizedExchange);
        String normalizedType = optionalText(productType, "productType", 16);
        normalizedType = normalizedType == null ? "FUTURES" : normalizedType.toUpperCase(Locale.ROOT);
        if (!"FUTURES".equals(normalizedType)) {
            throw invalid("productType 仅支持 FUTURES");
        }
        return repository.findProducts(normalizedExchange, normalizedType, keywordPattern(keyword));
    }

    private FuturesAccountResponse requireAccount(String accountNo) {
        Optional<FuturesAccountResponse> account = repository.findAccount(accountNo);
        if (!account.isPresent()) {
            throw new BusinessApiException(HttpStatus.NOT_FOUND, "REFERENCE_FUTURES_ACCOUNT_NOT_FOUND",
                    "期货账户不存在");
        }
        return account.get();
    }

    private ExchangeResponse requireExchange(String exchangeCode) {
        Optional<ExchangeResponse> exchange = repository.findExchange(exchangeCode);
        if (!exchange.isPresent()) {
            throw new BusinessApiException(HttpStatus.NOT_FOUND, "REFERENCE_EXCHANGE_NOT_FOUND",
                    "交易所不存在或未启用");
        }
        return exchange.get();
    }

    private String normalizeExchange(String value) {
        return requireText(value, "exchangeCode", 16).toUpperCase(Locale.ROOT);
    }

    private String keywordPattern(String value) {
        String normalized = optionalText(value, "keyword", 100);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + normalized + "%";
    }

    private String requireText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, fieldName, maxLength);
        if (normalized == null) {
            throw invalid(fieldName + " 不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid(fieldName + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private BusinessApiException invalid(String message) {
        return new BusinessApiException(HttpStatus.BAD_REQUEST, "REFERENCE_QUERY_INVALID", message);
    }
}
