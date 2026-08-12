package com.flowmind.business.config;

import com.flowmind.business.security.BusinessAuthorizationProvider;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.business.security.PlatformCurrentUserAdapter;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.api.service.AdminProcessService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.core.audit.AuditLogWriter;
import com.flowmind.platform.core.definition.ProcessAttachmentTemplateManager;
import com.flowmind.platform.core.monitor.ActionExceptionAlertWriter;
import com.flowmind.platform.core.query.RuntimeQueryAssembler;
import com.flowmind.platform.core.runtime.AdminPermissionGuard;
import com.flowmind.platform.core.runtime.DefaultAdminProcessService;
import com.flowmind.platform.core.runtime.InstanceTaskCancellationService;
import com.flowmind.platform.core.runtime.RuntimeDefinitionLoader;
import com.flowmind.platform.core.runtime.RuntimeNodeAdvancer;
import com.flowmind.platform.core.runtime.RuntimeOperationExecutor;
import com.flowmind.platform.core.runtime.RuntimeRequestValidator;
import com.flowmind.platform.core.runtime.RuntimeTransactionExecutor;
import com.flowmind.platform.core.validation.ProcessAttachmentTemplateValidator;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessAuditLogRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务身份与平台 Starter 的装配边界。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@Configuration
@EnableConfigurationProperties(BusinessBaseProperties.class)
public class BusinessPlatformConfiguration {

    @Bean
    @ConditionalOnBean(CurrentBusinessUserProvider.class)
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider platformCurrentUserAdapter(CurrentBusinessUserProvider businessUserProvider,
                                                          ObjectProvider<OrganizationProvider> organizationProvider) {
        return new PlatformCurrentUserAdapter(businessUserProvider, organizationProvider);
    }

    @Bean
    @ConditionalOnMissingBean(BusinessAuthorizationProvider.class)
    public BusinessAuthorizationProvider businessAuthorizationProvider() {
        return userId -> false;
    }

    @Bean
    @ConditionalOnMissingBean(ProcessAttachmentTemplateValidator.class)
    public ProcessAttachmentTemplateValidator processAttachmentTemplateValidator() {
        return new ProcessAttachmentTemplateValidator();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessAttachmentTemplateManager.class)
    public ProcessAttachmentTemplateManager processAttachmentTemplateManager(
            ProcessAttachmentTemplateRepository repository,
            ProcessAttachmentTemplateValidator validator) {
        return new ProcessAttachmentTemplateManager(repository, validator);
    }

    /** Exposes the platform administrator service through Starter dependencies without enabling platform Web MVC. */
    @Bean
    @ConditionalOnMissingBean(AdminProcessService.class)
    public AdminProcessService adminProcessService(
            ProcessInstanceRepository instanceRepository,
            RuntimeDefinitionLoader definitionLoader,
            RuntimeRequestValidator requestValidator,
            RuntimeOperationExecutor operationExecutor,
            RuntimeNodeAdvancer nodeAdvancer,
            InstanceTaskCancellationService taskCancellationService,
            ProcessDefinitionRepository definitionRepository,
            CallbackService callbackService,
            RuntimeTransactionExecutor transactionExecutor,
            ProcessAuditLogRepository auditLogRepository,
            ActiveTaskRepository activeTaskRepository,
            ProcessHistoryTaskRepository historyTaskRepository,
            TaskGroupRepository taskGroupRepository,
            RuntimeQueryAssembler queryAssembler,
            AuditLogWriter auditLogWriter,
            AdminPermissionGuard adminPermissionGuard,
            ActionExceptionAlertWriter actionExceptionAlertWriter) {
        return new DefaultAdminProcessService(instanceRepository, definitionLoader, requestValidator,
                operationExecutor, nodeAdvancer, taskCancellationService, definitionRepository,
                callbackService, transactionExecutor, auditLogRepository, activeTaskRepository,
                historyTaskRepository, taskGroupRepository, queryAssembler, auditLogWriter,
                adminPermissionGuard, actionExceptionAlertWriter);
    }
}
