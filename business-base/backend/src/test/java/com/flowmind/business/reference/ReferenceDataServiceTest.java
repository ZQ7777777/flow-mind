package com.flowmind.business.reference;

import com.flowmind.business.common.BusinessApiException;
import com.flowmind.business.reference.dto.ReferenceDataResponses.ExchangeResponse;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesAccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReferenceDataServiceTest {

    private ReferenceDataRepository repository;
    private ReferenceDataService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReferenceDataRepository.class);
        service = new ReferenceDataService(repository);
    }

    @Test
    void rejectsUnsupportedCurrencyAndProductType() {
        FuturesAccountResponse account = new FuturesAccountResponse();
        when(repository.findAccount("80000188")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.funds("80000188", "USD"))
                .isInstanceOf(BusinessApiException.class)
                .extracting("code").isEqualTo("REFERENCE_QUERY_INVALID");

        ExchangeResponse exchange = new ExchangeResponse();
        when(repository.findExchange("DCE")).thenReturn(Optional.of(exchange));
        assertThatThrownBy(() -> service.products("DCE", null, "OPTION"))
                .isInstanceOf(BusinessApiException.class)
                .extracting("code").isEqualTo("REFERENCE_QUERY_INVALID");
    }

    @Test
    void reportsUnknownAccountAndExchangeWithStableNotFoundCodes() {
        when(repository.findAccount("missing")).thenReturn(Optional.<FuturesAccountResponse>empty());
        assertThatThrownBy(() -> service.tradingCodes("missing", "DCE"))
                .isInstanceOf(BusinessApiException.class)
                .extracting("code").isEqualTo("REFERENCE_FUTURES_ACCOUNT_NOT_FOUND");

        when(repository.findExchange("DCE")).thenReturn(Optional.<ExchangeResponse>empty());
        assertThatThrownBy(() -> service.products("DCE", null, null))
                .isInstanceOf(BusinessApiException.class)
                .extracting("code").isEqualTo("REFERENCE_EXCHANGE_NOT_FOUND");
    }

    @Test
    void emptyRepositoryReturnsEmptyCollections() {
        when(repository.findAccounts(null)).thenReturn(Collections.<FuturesAccountResponse>emptyList());
        when(repository.findExchanges()).thenReturn(Collections.<ExchangeResponse>emptyList());
        service.accounts(null);
        service.exchanges();
    }
}
