package com.flowmind.business.entry;

import com.flowmind.business.common.BusinessApiException;
import com.flowmind.business.entry.dto.BusinessEntryConfigResponse;
import com.flowmind.business.entry.dto.BusinessEntryConfigWriteRequest;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessEntryConfigServiceTest {

    private ProcessDefinitionService definitionService;
    private BusinessEntryConfigService service;

    @BeforeEach
    void setUp() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(
                DriverManager.getConnection("jdbc:sqlite::memory:"), true));
        jdbc.execute("CREATE TABLE process_definition (id TEXT PRIMARY KEY)");
        jdbc.update("INSERT INTO process_definition (id) VALUES (?)", "definition-1");
        BusinessEntryConfigSchemaInitializer.initialize(jdbc);
        definitionService = mock(ProcessDefinitionService.class);
        CurrentBusinessUserProvider users = mock(CurrentBusinessUserProvider.class);
        when(users.currentUser()).thenReturn(new CurrentBusinessUserProvider.BusinessUser("user-1", "dept-1"));
        service = new BusinessEntryConfigService(new BusinessEntryConfigRepository(jdbc), definitionService, users);
    }

    @Test
    void createsManualDisabledConfigAndFallsBackToProcessName() {
        when(definitionService.getDefinition("definition-1")).thenReturn(definition(true));
        BusinessEntryConfigWriteRequest request = request("/generated/apply");

        BusinessEntryConfigResponse response = service.create(request, "admin-1");

        assertThat(response.getEntryDisplayName()).isEqualTo("入金申请");
        assertThat(response.getEntrySource()).isEqualTo(BusinessEntrySource.MANUAL);
        assertThat(response.getEnabled()).isFalse();
        assertThat(service.listVisibleEntries()).isEmpty();
    }

    @Test
    void exposesOnlyEnabledConfigWithStartableDefinition() {
        when(definitionService.getDefinition("definition-1")).thenReturn(definition(true));
        BusinessEntryConfigWriteRequest request = request("/generated/apply");
        request.setEnabled(Boolean.TRUE);
        service.create(request, "admin-1");

        assertThat(service.listVisibleEntries()).singleElement()
                .satisfies(entry -> assertThat(entry.getProcessCode()).isEqualTo("entry_application"));
        assertThat(service.visibleEntry("definition-1").getEntryPageUrl()).isEqualTo("/generated/apply");

        when(definitionService.getDefinition("definition-1")).thenReturn(definition(false));
        assertThat(service.listVisibleEntries()).isEmpty();
        assertThatThrownBy(() -> service.visibleEntry("definition-1"))
                .isInstanceOf(BusinessApiException.class)
                .extracting(exception -> ((BusinessApiException) exception).getCode())
                .isEqualTo("BUSINESS_ENTRY_CONFIG_NOT_FOUND");
    }

    @Test
    void rejectsExternalAndProtocolRelativeRoutes() {
        when(definitionService.getDefinition("definition-1")).thenReturn(definition(true));

        assertThatThrownBy(() -> service.create(request("https://example.com/apply"), "admin-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(request("//example.com/apply"), "admin-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateCreateAndAllowsAgentUpsert() {
        when(definitionService.getDefinition("definition-1")).thenReturn(definition(true));
        service.create(request("/generated/apply"), "admin-1");

        assertThatThrownBy(() -> service.create(request("/generated/duplicate"), "admin-1"))
                .isInstanceOf(BusinessApiException.class)
                .extracting(exception -> ((BusinessApiException) exception).getCode())
                .isEqualTo("BUSINESS_ENTRY_CONFIG_CONFLICT");

        BusinessEntryConfigResponse upserted = service.upsertByDefinition("definition-1",
                request("/generated/rebuilt"), "admin-2");
        assertThat(upserted.getEntryPageUrl()).isEqualTo("/generated/rebuilt");
        assertThat(service.listAdminConfigs()).hasSize(1);
    }

    private BusinessEntryConfigWriteRequest request(String url) {
        BusinessEntryConfigWriteRequest request = new BusinessEntryConfigWriteRequest();
        request.setDefinitionId("definition-1");
        request.setEntryPageUrl(url);
        return request;
    }

    private ProcessDefinitionDetailDTO definition(boolean startable) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-1");
        definition.setProcessCode("entry_application");
        definition.setProcessName("入金申请");
        definition.setVersion(Integer.valueOf(1));
        definition.setDefinitionStatus(DefinitionStatusEnum.PUBLISHED);
        definition.setActivationStatus(startable ? ActivationStatusEnum.ACTIVE : ActivationStatusEnum.INACTIVE);
        definition.setGrayStatus(GrayStatusEnum.OFF);
        return definition;
    }
}
