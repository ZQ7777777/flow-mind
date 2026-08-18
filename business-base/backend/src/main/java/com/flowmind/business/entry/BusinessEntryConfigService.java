package com.flowmind.business.entry;

import com.flowmind.business.common.BusinessApiException;
import com.flowmind.business.entry.dto.BusinessEntryConfigResponse;
import com.flowmind.business.entry.dto.BusinessEntryConfigWriteRequest;
import com.flowmind.business.entry.dto.ProcessEntryLinkResponse;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.GrayStatusEnum;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 业务入口配置与业务大厅查询服务。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Service
public class BusinessEntryConfigService {

    private final BusinessEntryConfigRepository repository;
    private final ProcessDefinitionService definitionService;
    private final CurrentBusinessUserProvider currentUserProvider;

    public BusinessEntryConfigService(BusinessEntryConfigRepository repository,
                                      ProcessDefinitionService definitionService,
                                      CurrentBusinessUserProvider currentUserProvider) {
        this.repository = repository;
        this.definitionService = definitionService;
        this.currentUserProvider = currentUserProvider;
    }

    public List<BusinessEntryConfigResponse> listAdminConfigs() {
        List<BusinessEntryConfigResponse> result = new ArrayList<BusinessEntryConfigResponse>();
        for (BusinessEntryConfigEntity entity : repository.findAll()) {
            result.add(toAdminResponse(entity, definitionService.getDefinition(entity.getDefinitionId())));
        }
        return result;
    }

    @Transactional
    public BusinessEntryConfigResponse create(BusinessEntryConfigWriteRequest request, String administratorId) {
        ProcessDefinitionDetailDTO definition = requireDefinition(requireText(request.getDefinitionId(), "definitionId"));
        if (repository.findByDefinitionId(definition.getId()).isPresent()) {
            throw duplicateConfig();
        }
        BusinessEntryConfigEntity entity = new BusinessEntryConfigEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setDefinitionId(definition.getId());
        entity.setCreatedBy(administratorId);
        apply(entity, request, BusinessEntrySource.MANUAL, false);
        entity.setUpdatedBy(administratorId);
        try {
            repository.insert(entity);
        } catch (DataAccessException exception) {
            if (isDefinitionUniqueConflict(exception)) {
                throw duplicateConfig();
            }
            throw exception;
        }
        return toAdminResponse(repository.findById(entity.getId()).get(), definition);
    }

    @Transactional
    public BusinessEntryConfigResponse update(String id, BusinessEntryConfigWriteRequest request,
                                               String administratorId) {
        BusinessEntryConfigEntity entity = requireConfig(id);
        ProcessDefinitionDetailDTO definition = requireDefinition(entity.getDefinitionId());
        apply(entity, request, entity.getEntrySource(), Boolean.TRUE.equals(entity.getEnabled()));
        entity.setUpdatedBy(administratorId);
        repository.updateById(entity);
        return toAdminResponse(repository.findById(id).get(), definition);
    }

    @Transactional
    public BusinessEntryConfigResponse upsertByDefinition(String definitionId,
                                                          BusinessEntryConfigWriteRequest request,
                                                          String administratorId) {
        ProcessDefinitionDetailDTO definition = requireDefinition(requireText(definitionId, "definitionId"));
        Optional<BusinessEntryConfigEntity> existing = repository.findByDefinitionId(definitionId);
        BusinessEntryConfigEntity entity = existing.orElseGet(BusinessEntryConfigEntity::new);
        if (!existing.isPresent()) {
            entity.setId(UUID.randomUUID().toString());
            entity.setDefinitionId(definitionId);
            entity.setCreatedBy(administratorId);
        }
        apply(entity, request,
                existing.isPresent() ? existing.get().getEntrySource() : BusinessEntrySource.AGENT_GENERATED,
                existing.isPresent() && Boolean.TRUE.equals(existing.get().getEnabled()));
        entity.setUpdatedBy(administratorId);
        repository.upsertByDefinitionId(entity);
        return toAdminResponse(repository.findByDefinitionId(definitionId).get(), definition);
    }

    @Transactional
    public void delete(String id) {
        repository.deleteById(requireText(id, "id"));
    }

    public List<ProcessEntryLinkResponse> listVisibleEntries() {
        currentUserProvider.currentUser();
        List<ProcessEntryLinkResponse> result = new ArrayList<ProcessEntryLinkResponse>();
        for (BusinessEntryConfigEntity entity : repository.findAll()) {
            if (!Boolean.TRUE.equals(entity.getEnabled()) || !hasText(entity.getEntryPageUrl())) {
                continue;
            }
            ProcessDefinitionDetailDTO definition = definitionService.getDefinition(entity.getDefinitionId());
            if (isStartable(definition)) {
                result.add(toEntryLink(entity, definition));
            }
        }
        return result;
    }

    public ProcessEntryLinkResponse visibleEntry(String definitionId) {
        currentUserProvider.currentUser();
        Optional<BusinessEntryConfigEntity> config = repository.findByDefinitionId(requireText(definitionId,
                "definitionId"));
        if (!config.isPresent() || !Boolean.TRUE.equals(config.get().getEnabled())
                || !hasText(config.get().getEntryPageUrl())) {
            throw notFound();
        }
        ProcessDefinitionDetailDTO definition = definitionService.getDefinition(definitionId);
        if (!isStartable(definition)) {
            throw notFound();
        }
        return toEntryLink(config.get(), definition);
    }

    private void apply(BusinessEntryConfigEntity entity, BusinessEntryConfigWriteRequest request,
                       BusinessEntrySource defaultSource, boolean defaultEnabled) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        entity.setEntryDisplayName(optionalText(request.getEntryDisplayName(), "entryDisplayName", 128));
        entity.setEntryPageUrl(validateEntryPageUrl(request.getEntryPageUrl()));
        entity.setEntrySource(request.getEntrySource() == null ? defaultSource : request.getEntrySource());
        entity.setEnabled(request.getEnabled() == null ? Boolean.valueOf(defaultEnabled) : request.getEnabled());
        entity.setRemark(optionalText(request.getRemark(), "remark", 1000));
        entity.setGenerationId(optionalText(request.getGenerationId(), "generationId", 128));
        entity.setArtifactRevision(optionalText(request.getArtifactRevision(), "artifactRevision", 128));
    }

    private String validateEntryPageUrl(String value) {
        String route = requireText(value, "entryPageUrl");
        if (route.length() > 512 || !route.startsWith("/") || route.startsWith("//")
                || route.indexOf('\\') >= 0 || containsControlCharacter(route)) {
            throw new IllegalArgumentException("entryPageUrl 必须是以单个 / 开头的同源前端路由");
        }
        return route;
    }

    private boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private BusinessEntryConfigEntity requireConfig(String id) {
        Optional<BusinessEntryConfigEntity> entity = repository.findById(requireText(id, "id"));
        if (!entity.isPresent()) {
            throw notFound();
        }
        return entity.get();
    }

    private ProcessDefinitionDetailDTO requireDefinition(String definitionId) {
        ProcessDefinitionDetailDTO definition = definitionService.getDefinition(definitionId);
        if (definition == null) {
            throw new BusinessApiException(HttpStatus.NOT_FOUND, "BUSINESS_PROCESS_DEFINITION_NOT_FOUND",
                    "流程定义不存在");
        }
        return definition;
    }

    private boolean isStartable(ProcessDefinitionDetailDTO definition) {
        return definition != null
                && DefinitionStatusEnum.PUBLISHED.equals(definition.getDefinitionStatus())
                && ActivationStatusEnum.ACTIVE.equals(definition.getActivationStatus())
                && GrayStatusEnum.OFF.equals(definition.getGrayStatus());
    }

    private BusinessEntryConfigResponse toAdminResponse(BusinessEntryConfigEntity entity,
                                                        ProcessDefinitionDetailDTO definition) {
        BusinessEntryConfigResponse response = new BusinessEntryConfigResponse();
        response.setId(entity.getId());
        response.setDefinitionId(entity.getDefinitionId());
        if (definition != null) {
            response.setProcessCode(definition.getProcessCode());
            response.setProcessName(definition.getProcessName());
            response.setDefinitionVersion(definition.getVersion());
        }
        response.setEntryDisplayName(hasText(entity.getEntryDisplayName()) ? entity.getEntryDisplayName()
                : definition == null ? null : definition.getProcessName());
        response.setEntryPageUrl(entity.getEntryPageUrl());
        response.setEntrySource(entity.getEntrySource());
        response.setEnabled(entity.getEnabled());
        response.setRemark(entity.getRemark());
        response.setGenerationId(entity.getGenerationId());
        response.setArtifactRevision(entity.getArtifactRevision());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedBy(entity.getUpdatedBy());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private ProcessEntryLinkResponse toEntryLink(BusinessEntryConfigEntity entity,
                                                 ProcessDefinitionDetailDTO definition) {
        ProcessEntryLinkResponse response = new ProcessEntryLinkResponse();
        response.setDefinitionId(definition.getId());
        response.setProcessCode(definition.getProcessCode());
        response.setProcessName(definition.getProcessName());
        response.setDefinitionVersion(definition.getVersion());
        response.setEntryDisplayName(hasText(entity.getEntryDisplayName())
                ? entity.getEntryDisplayName() : definition.getProcessName());
        response.setEntryPageUrl(entity.getEntryPageUrl());
        response.setEntrySource(entity.getEntrySource());
        response.setEnabled(Boolean.TRUE);
        return response;
    }

    private BusinessApiException notFound() {
        return new BusinessApiException(HttpStatus.NOT_FOUND, "BUSINESS_ENTRY_CONFIG_NOT_FOUND",
                "业务入口配置不存在或当前不可访问");
    }

    private BusinessApiException duplicateConfig() {
        return new BusinessApiException(HttpStatus.CONFLICT, "BUSINESS_ENTRY_CONFIG_CONFLICT",
                "该流程定义已经存在业务入口配置");
    }

    private boolean isDefinitionUniqueConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("business_entry_config.definition_id")
                    && message.contains("UNIQUE")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be <= " + maxLength);
        }
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
