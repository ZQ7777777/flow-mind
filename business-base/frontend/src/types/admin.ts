export interface PageResult<T> {
  records: T[];
  pageNo: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface DefinitionQuery {
  pageNo: number;
  pageSize: number;
  processCode?: string;
  processName?: string;
  systemCode?: string;
  definitionStatus?: string;
  activationStatus?: string;
}

export interface ProcessDefinition {
  id: string;
  definitionId?: string;
  processCode: string;
  processName: string;
  systemCode?: string;
  version?: number;
  definitionStatus?: string;
  activationStatus?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProcessNode {
  id?: string;
  definitionId?: string;
  nodeCode: string;
  nodeName: string;
  nodeType: string;
  pairedGatewayCode?: string;
  approverRuleType?: string;
  approverRuleConfig?: string;
  multiInstanceMode?: string;
  listenerConfig?: string;
  timeoutConfig?: string;
  reminderConfig?: string;
  positionX?: number;
  positionY?: number;
  sortOrder?: number;
}

export interface ProcessEdge {
  id?: string;
  definitionId?: string;
  edgeCode: string;
  sourceNodeCode: string;
  targetNodeCode: string;
  conditionExpression?: string;
  defaultEdge?: boolean;
  sortOrder?: number;
}

export interface ProcessFormField {
  id?: string;
  definitionId?: string;
  fieldCode: string;
  fieldName: string;
  fieldType?: string;
  controlType?: string;
  required?: boolean;
  validationRule?: string;
  defaultValue?: string;
  sortOrder?: number;
}

export interface AttachmentTemplate {
  id?: string;
  attachmentConfigId?: string;
  definitionId?: string;
  attachmentTemplateId: string;
  attachmentCode: string;
  templateVersion?: number;
  attachmentName?: string;
  description?: string;
  allowedExtensions?: string[];
  maxSizeBytes?: number;
  templateStatus?: string;
  required?: boolean;
  minCount?: number;
  maxCount?: number;
  applicableNodeCodes?: string[];
  sortOrder?: number;
}

export interface AttachmentConfig {
  configId?: string;
  attachmentConfigId?: string;
  definitionId?: string;
  attachmentTemplateId: string;
  attachmentCode: string;
  required?: boolean;
  minCount?: number;
  maxCount?: number;
  applicableNodeCodes?: string[];
  sortOrder?: number;
}

export interface ProcessDefinitionDetail extends ProcessDefinition {
  nodes: ProcessNode[];
  edges: ProcessEdge[];
  formFields: ProcessFormField[];
  attachmentTemplates: AttachmentTemplate[];
}

export interface SaveGraphRequest {
  operationId: string;
  nodes: ProcessNode[];
  edges: ProcessEdge[];
  formFields: ProcessFormField[];
  attachmentConfigs: AttachmentConfig[];
}

export interface ProcessDefinitionUserOption {
  userId: string;
  userName?: string;
  departmentId?: string;
  departmentName?: string;
  roleCodes: string[];
}

export interface ProcessDefinitionDepartmentOption {
  departmentId: string;
  departmentName?: string;
  parentDepartmentId?: string;
}

export interface ProcessDefinitionRoleOption {
  roleCode: string;
  roleName?: string;
}

export interface ProcessDefinitionOptions {
  users: ProcessDefinitionUserOption[];
  departments: ProcessDefinitionDepartmentOption[];
  roles: ProcessDefinitionRoleOption[];
}

export interface ValidationIssue {
  code: string;
  message: string;
  nodeCode?: string;
  edgeCode?: string;
}

export interface ValidationResult {
  valid: boolean;
  issues: ValidationIssue[];
}

export interface InstanceQuery {
  pageNo: number;
  pageSize: number;
  processCode?: string;
  instanceStatus?: string;
  instanceTitle?: string;
  businessKey?: string;
  starterUserId?: string;
  currentNodeCode?: string;
  startedFrom?: string;
  startedTo?: string;
}

export interface ProcessInstance {
  instanceId: string;
  definitionId?: string;
  processCode?: string;
  processName?: string;
  version?: number;
  instanceTitle: string;
  businessKey?: string;
  starterUserId?: string;
  starterUserName?: string;
  instanceStatus?: string;
  currentNodeCodes?: string[];
  variables?: Record<string, unknown>;
  startedAt?: string;
  endedAt?: string;
}

export interface ProcessInstanceDetail extends ProcessInstance {
  historyTasks?: AdminRecord[];
  comments?: AdminRecord[];
}

export type TraceType = "history-tasks" | "comments" | "callback-logs" | "read-records" | "audit-logs";
export type AdminRecord = Record<string, unknown>;
