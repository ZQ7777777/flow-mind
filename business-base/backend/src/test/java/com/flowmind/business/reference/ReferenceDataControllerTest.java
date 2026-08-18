package com.flowmind.business.reference;

import com.flowmind.business.common.BusinessExceptionHandler;
import com.flowmind.business.reference.dto.ReferenceDataResponses.FuturesAccountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReferenceDataControllerTest {

    private ReferenceDataService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReferenceDataService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReferenceDataController(service))
                .setControllerAdvice(new BusinessExceptionHandler()).build();
    }

    @Test
    void exposesAccountAndProductQueriesWithoutResponseEnvelope() throws Exception {
        FuturesAccountResponse account = new FuturesAccountResponse();
        account.setAccountNo("80000188");
        account.setCustomerName("上海启明实业有限公司");
        account.setAccountStatus("NORMAL");
        when(service.accounts("启明")).thenReturn(Collections.singletonList(account));
        when(service.products("DCE", "铁矿", "FUTURES")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/reference-data/futures-accounts").param("keyword", "启明"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNo").value("80000188"))
                .andExpect(jsonPath("$[0].accountStatus").value("NORMAL"));
        mockMvc.perform(get("/api/reference-data/futures-products")
                        .param("exchangeCode", "DCE").param("keyword", "铁矿")
                        .param("productType", "FUTURES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }
}
