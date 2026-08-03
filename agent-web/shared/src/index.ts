export const WORKFLOW_STATES = [
  "COLLECTING",
  "REQUIREMENT_REVIEW",
  "PROCESS_PROVISIONING",
  "PROCESS_PROVISION_FAILED",
  "PROCESS_REVIEW",
  "PROCESS_ACTIVATING",
  "PROCESS_ACTIVATION_FAILED",
  "PROCESS_ACTIVE",
] as const;

export type WorkflowState = (typeof WORKFLOW_STATES)[number];

export interface MockUser {
  userId: string;
  userName: string;
  departmentId?: string;
  departmentName?: string;
}

export interface Participant {
  roleCode: string;
  roleName: string;
  responsibility: string;
}

export interface FormFieldRequirement {
  fieldCode: string;
  fieldName: string;
  fieldType: "string" | "number" | "date" | "boolean" | "select";
  controlType: "input" | "textarea" | "number" | "datePicker" | "checkbox" | "select";
  required: boolean;
  validation: Record<string, unknown>;
  defaultValue?: string;
  options?: Array<{ label: string; value: string }>;
  sortOrder: number;
}

export interface AttachmentRequirement {
  attachmentCode: string;
  attachmentName: string;
  description?: string;
  allowedExtensions: string[];
  maxSizeBytes: number;
  required: boolean;
  minCount: number;
  maxCount: number;
  applicableNodeCodes: string[];
  sortOrder: number;
}

export type ProcessNodeType =
  | "START"
  | "USER_TASK"
  | "EXCLUSIVE_GATEWAY"
  | "PARALLEL_SPLIT_GATEWAY"
  | "PARALLEL_JOIN_GATEWAY"
  | "END";

export interface ProcessNodeRequirement {
  nodeCode: string;
  nodeName: string;
  nodeType: ProcessNodeType;
  pairedGatewayCode?: string;
  approverRule?: {
    type: "USER" | "STARTER" | "DEPARTMENT" | "ROLE" | "ROLE_IN_DEPARTMENT" | "APPROVER_EXPRESSION";
    config: Record<string, unknown>;
  };
  multiInstanceMode?: "SINGLE" | "OR_SIGN" | "COUNTERSIGN";
  /** Runtime task-action configuration persisted to the platform as listenerConfig JSON. */
  listenerConfig?: Record<string, unknown>;
  /** Runtime timeout configuration persisted to the platform as timeoutConfig JSON. */
  timeoutConfig?: Record<string, unknown>;
  /** Runtime reminder configuration persisted to the platform as reminderConfig JSON. */
  reminderConfig?: Record<string, unknown>;
  positionX: number;
  positionY: number;
  sortOrder: number;
}

export const DEFAULT_SYSTEM_CODE = "FINANCE_SYS_001";

/**
 * Creates independent default runtime-policy objects for a user task.  A factory
 * is used so editing one node in the UI cannot mutate the defaults of another.
 */
export function createDefaultUserTaskConfigs(): Pick<
  ProcessNodeRequirement,
  "listenerConfig" | "timeoutConfig" | "reminderConfig"
> {
  return {
    listenerConfig: {
      taskActionRules: {
        reject: { enabled: true, targetNodeCodes: ["apply"] },
        directSend: { enabled: true, targetMode: "REJECT_SOURCE" },
      },
    },
    timeoutConfig: {
      enabled: true,
      durationMinutes: 1440,
      action: "REMIND",
      severity: "MEDIUM",
    },
    reminderConfig: {
      enabled: true,
      maxCount: 2,
      messageTemplate: "您有代办，请及时处理。",
    },
  };
}

export interface ProcessEdgeRequirement {
  edgeCode: string;
  sourceNodeCode: string;
  targetNodeCode: string;
  conditionExpression?: string;
  defaultEdge: boolean;
  sortOrder: number;
}

export interface BusinessRule {
  ruleCode: string;
  description: string;
  expression?: string;
}

export interface BusinessRequirement {
  schemaVersion: "1.0";
  businessCode: string;
  businessName: string;
  systemCode: string;
  goal: string;
  participants: Participant[];
  formFields: FormFieldRequirement[];
  attachments: AttachmentRequirement[];
  nodes: ProcessNodeRequirement[];
  edges: ProcessEdgeRequirement[];
  businessRules: BusinessRule[];
}

export interface RequirementRevision {
  sessionId: string;
  revision: number;
  requirement: BusinessRequirement;
  missingItems: string[];
  ambiguities: string[];
  readyForReview: boolean;
  source: "AGENT" | "USER_EDIT";
  confirmedBy?: string;
  confirmedAt?: string;
  createdAt: string;
}

export interface ConversationMessage {
  id: string;
  role: "user" | "assistant" | "tool";
  content: string;
  createdAt?: string;
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

export interface ProcessPreview {
  platformDefinitionId: string;
  processCode: string;
  processName: string;
  definitionVersion?: number;
  definitionStatus?: string;
  activationStatus?: string;
  nodes: Array<Record<string, unknown>>;
  edges: Array<Record<string, unknown>>;
  formFields: Array<Record<string, unknown>>;
  attachmentTemplates: Array<Record<string, unknown>>;
  validation: ValidationResult;
}

export interface WorkflowSnapshot {
  sessionId: string;
  ownerUserId: string;
  state: WorkflowState;
  rowVersion: number;
  targetRoot?: string;
  businessName?: string;
  messages: ConversationMessage[];
  requirement?: RequirementRevision;
  processPreview?: ProcessPreview;
  lastError?: { code: string; message: string };
  allowedActions: string[];
}

export interface AgentErrorBody {
  code: string;
  message: string;
  sessionId?: string;
  requestId: string;
  details: Record<string, unknown>;
}

export const businessRequirementSchema = {
  $id: "BusinessRequirement",
  type: "object",
  additionalProperties: false,
  required: [
    "schemaVersion", "businessCode", "businessName", "systemCode", "goal",
    "participants", "formFields", "attachments", "nodes", "edges", "businessRules",
  ],
  properties: {
    schemaVersion: { const: "1.0" },
    businessCode: { type: "string" },
    businessName: { type: "string" },
    systemCode: { type: "string" },
    goal: { type: "string" },
    participants: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["roleCode", "roleName", "responsibility"],
        properties: {
          roleCode: { type: "string" },
          roleName: { type: "string" },
          responsibility: { type: "string" },
        },
      },
    },
    formFields: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["fieldCode", "fieldName", "fieldType", "controlType", "required", "validation", "sortOrder"],
        properties: {
          fieldCode: { type: "string" },
          fieldName: { type: "string" },
          fieldType: { enum: ["string", "number", "date", "boolean", "select"] },
          controlType: { enum: ["input", "textarea", "number", "datePicker", "checkbox", "select"] },
          required: { type: "boolean" },
          validation: { type: "object" },
          defaultValue: { type: "string" },
          options: {
            type: "array",
            items: {
              type: "object",
              additionalProperties: false,
              required: ["label", "value"],
              properties: { label: { type: "string" }, value: { type: "string" } },
            },
          },
          sortOrder: { type: "integer" },
        },
      },
    },
    attachments: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: [
          "attachmentCode", "attachmentName", "allowedExtensions", "maxSizeBytes",
          "required", "minCount", "maxCount", "applicableNodeCodes", "sortOrder",
        ],
        properties: {
          attachmentCode: { type: "string" },
          attachmentName: { type: "string" },
          description: { type: "string" },
          allowedExtensions: { type: "array", items: { type: "string" } },
          maxSizeBytes: { type: "integer", minimum: 1 },
          required: { type: "boolean" },
          minCount: { type: "integer", minimum: 0 },
          maxCount: { type: "integer", minimum: 1 },
          applicableNodeCodes: { type: "array", items: { type: "string" } },
          sortOrder: { type: "integer" },
        },
      },
    },
    nodes: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["nodeCode", "nodeName", "nodeType", "positionX", "positionY", "sortOrder"],
        properties: {
          nodeCode: { type: "string" },
          nodeName: { type: "string" },
          nodeType: { enum: ["START", "USER_TASK", "EXCLUSIVE_GATEWAY", "PARALLEL_SPLIT_GATEWAY", "PARALLEL_JOIN_GATEWAY", "END"] },
          pairedGatewayCode: { type: "string" },
          approverRule: {
            type: "object",
            additionalProperties: false,
            required: ["type", "config"],
            properties: {
              type: { enum: ["USER", "STARTER", "DEPARTMENT", "ROLE", "ROLE_IN_DEPARTMENT", "APPROVER_EXPRESSION"] },
              config: { type: "object" },
            },
          },
          multiInstanceMode: { enum: ["SINGLE", "OR_SIGN", "COUNTERSIGN"] },
          listenerConfig: { type: "object" },
          timeoutConfig: { type: "object" },
          reminderConfig: { type: "object" },
          positionX: { type: "number" },
          positionY: { type: "number" },
          sortOrder: { type: "integer" },
        },
      },
    },
    edges: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["edgeCode", "sourceNodeCode", "targetNodeCode", "defaultEdge", "sortOrder"],
        properties: {
          edgeCode: { type: "string" },
          sourceNodeCode: { type: "string" },
          targetNodeCode: { type: "string" },
          conditionExpression: { type: "string" },
          defaultEdge: { type: "boolean" },
          sortOrder: { type: "integer" },
        },
      },
    },
    businessRules: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["ruleCode", "description"],
        properties: {
          ruleCode: { type: "string" },
          description: { type: "string" },
          expression: { type: "string" },
        },
      },
    },
  },
} as const;

export const ENTRY_APPLICATION_REQUIREMENT: BusinessRequirement = {
  schemaVersion: "1.0",
  businessCode: "entry_application",
  businessName: "入金申请",
  systemCode: DEFAULT_SYSTEM_CODE,
  goal: "业务员提交入金申请，由部门经理审批并由财务确认后完成。",
  participants: [
    { roleCode: "sales", roleName: "业务员", responsibility: "提交入金申请" },
    { roleCode: "department_manager", roleName: "部门经理", responsibility: "审批申请" },
    { roleCode: "finance", roleName: "财务", responsibility: "确认入金" },
  ],
  formFields: [
    { fieldCode: "applicantName", fieldName: "申请人姓名", fieldType: "string", controlType: "input", required: true, validation: {}, sortOrder: 1 },
    { fieldCode: "amount", fieldName: "入金金额", fieldType: "number", controlType: "number", required: true, validation: { minimum: 0.01 }, sortOrder: 2 },
    { fieldCode: "accountNo", fieldName: "入金账号", fieldType: "string", controlType: "input", required: true, validation: {}, sortOrder: 3 },
  ],
  attachments: [
    {
      attachmentCode: "bankReceipt",
      attachmentName: "银行回单",
      description: "入金申请银行回单",
      allowedExtensions: ["pdf", "jpg", "png"],
      maxSizeBytes: 10485760,
      required: true,
      minCount: 1,
      maxCount: 5,
      applicableNodeCodes: ["apply"],
      sortOrder: 1,
    },
  ],
  nodes: [
    { nodeCode: "start", nodeName: "开始", nodeType: "START", positionX: 80, positionY: 120, sortOrder: 1 },
    { nodeCode: "apply", nodeName: "申请", nodeType: "USER_TASK", approverRule: { type: "STARTER", config: {} }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs(), positionX: 260, positionY: 120, sortOrder: 2 },
    { nodeCode: "manager_approve", nodeName: "部门经理审批", nodeType: "USER_TASK", approverRule: { type: "ROLE_IN_DEPARTMENT", config: { roleCode: "department_manager", departmentFrom: "starter" } }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs(), positionX: 460, positionY: 120, sortOrder: 3 },
    { nodeCode: "finance_confirm", nodeName: "财务确认", nodeType: "USER_TASK", approverRule: { type: "ROLE", config: { roleCode: "finance" } }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs(), positionX: 680, positionY: 120, sortOrder: 4 },
    { nodeCode: "end", nodeName: "结束", nodeType: "END", positionX: 880, positionY: 120, sortOrder: 5 },
  ],
  edges: [
    { edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply", defaultEdge: false, sortOrder: 1 },
    { edgeCode: "e2", sourceNodeCode: "apply", targetNodeCode: "manager_approve", defaultEdge: false, sortOrder: 2 },
    { edgeCode: "e3", sourceNodeCode: "manager_approve", targetNodeCode: "finance_confirm", defaultEdge: false, sortOrder: 3 },
    { edgeCode: "e4", sourceNodeCode: "finance_confirm", targetNodeCode: "end", defaultEdge: false, sortOrder: 4 },
  ],
  businessRules: [],
};
