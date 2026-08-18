import { buildQuery, requestJson } from "./http";
import type {
  AdminRecord,
  AttachmentTemplate,
  BusinessEntryConfig,
  BusinessEntryConfigPayload,
  DefinitionQuery,
  InstanceQuery,
  PageResult,
  ProcessDefinition,
  ProcessDefinitionDetail,
  ProcessInstance,
  ProcessInstanceDetail,
  ProcessDefinitionOptions,
  SaveGraphRequest,
  TraceType,
  ValidationResult,
} from "../types/admin";

const DEFINITION_BASE = "/api/admin/process-definitions";
const INSTANCE_BASE = "/api/admin/process-instances";
const TEMPLATE_BASE = "/api/admin/attachment-templates";
const DEFINITION_OPTIONS = "/api/admin/process-definition-options";
const BUSINESS_ENTRY_CONFIG_BASE = "/api/admin/business-entry-configs";

function body(value: object): RequestInit {
  return { body: JSON.stringify(value) };
}

export function fetchDefinitions(query: DefinitionQuery): Promise<PageResult<ProcessDefinition>> {
  return requestJson(`${DEFINITION_BASE}${buildQuery(query)}`);
}

export function fetchDefinition(id: string): Promise<ProcessDefinitionDetail> {
  return requestJson(`${DEFINITION_BASE}/${encodeURIComponent(id)}`);
}

export function fetchProcessDefinitionOptions(): Promise<ProcessDefinitionOptions> {
  return requestJson(DEFINITION_OPTIONS);
}

export function fetchBusinessEntryConfigByDefinition(
  definitionId: string,
): Promise<BusinessEntryConfig> {
  return requestJson(
    `${BUSINESS_ENTRY_CONFIG_BASE}/by-definition/${encodeURIComponent(definitionId)}`,
  );
}

export function saveBusinessEntryConfigByDefinition(
  definitionId: string,
  payload: BusinessEntryConfigPayload,
): Promise<BusinessEntryConfig> {
  return requestJson(
    `${BUSINESS_ENTRY_CONFIG_BASE}/by-definition/${encodeURIComponent(definitionId)}`,
    { method: "PUT", ...body(payload) },
  );
}

export function createDefinition(payload: object): Promise<ProcessDefinition> {
  return requestJson(DEFINITION_BASE, { method: "POST", ...body(payload) });
}

export function saveDefinitionGraph(id: string, payload: SaveGraphRequest): Promise<ProcessDefinition> {
  return requestJson(`${DEFINITION_BASE}/${encodeURIComponent(id)}/graph`, {
    method: "PUT", ...body(payload),
  });
}

export function validateDefinition(id: string): Promise<ValidationResult> {
  return requestJson(`${DEFINITION_BASE}/${encodeURIComponent(id)}/publish-validation`);
}

export function operateDefinition(
  id: string,
  operation: "publish" | "activate" | "deactivate" | "archive",
  operationId: string,
): Promise<ProcessDefinition> {
  return requestJson(`${DEFINITION_BASE}/${encodeURIComponent(id)}/${operation}`, {
    method: "POST", ...body({ operationId }),
  });
}

export function copyDefinition(id: string, payload: object): Promise<ProcessDefinition> {
  return requestJson(`${DEFINITION_BASE}/${encodeURIComponent(id)}/copy`, {
    method: "POST", ...body(payload),
  });
}

export function deleteDefinition(id: string, operationId: string): Promise<void> {
  return requestJson(`${DEFINITION_BASE}/${encodeURIComponent(id)}`, {
    method: "DELETE", ...body({ operationId }),
  });
}

export function fetchAttachmentTemplates(params: object = {}): Promise<AttachmentTemplate[]> {
  return requestJson(`${TEMPLATE_BASE}${buildQuery(params)}`);
}

export function createAttachmentTemplate(payload: object): Promise<AttachmentTemplate> {
  return requestJson(TEMPLATE_BASE, { method: "POST", ...body(payload) });
}

export function fetchAdminInstances(query: InstanceQuery): Promise<PageResult<ProcessInstance>> {
  return requestJson(`${INSTANCE_BASE}${buildQuery(query)}`);
}

export function fetchAdminInstance(id: string): Promise<ProcessInstanceDetail> {
  return requestJson(`${INSTANCE_BASE}/${encodeURIComponent(id)}`);
}

export function fetchActiveTasks(id: string): Promise<AdminRecord[]> {
  return requestJson(`${INSTANCE_BASE}/${encodeURIComponent(id)}/active-tasks`);
}

export async function fetchInstanceTrace(
  id: string,
  type: TraceType,
  pageNo = 1,
  pageSize = 20,
): Promise<PageResult<AdminRecord>> {
  const result = await requestJson<PageResult<AdminRecord> | AdminRecord[]>(
    `${INSTANCE_BASE}/${encodeURIComponent(id)}/${type}${buildQuery({ pageNo, pageSize })}`,
  );
  if (Array.isArray(result)) {
    return { records: result, pageNo: 1, pageSize: result.length || pageSize, total: result.length, totalPages: 1 };
  }
  return result;
}

export function terminateInstance(id: string, operationId: string, comment: string): Promise<ProcessInstance> {
  return requestJson(`${INSTANCE_BASE}/${encodeURIComponent(id)}/terminate`, {
    method: "POST", ...body({ operationId, comment }),
  });
}

export function deleteInstance(id: string, operationId: string): Promise<void> {
  return requestJson(`${INSTANCE_BASE}/${encodeURIComponent(id)}`, {
    method: "DELETE", ...body({ operationId }),
  });
}



