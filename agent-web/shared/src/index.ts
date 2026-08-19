export const WORKFLOW_STATES = [
  "COLLECTING",
  "REQUIREMENT_REVIEW",
  "PROCESS_PROVISIONING",
  "PROCESS_PROVISION_FAILED",
  "PROCESS_REVIEW",
  "PROCESS_ACTIVATING",
  "PROCESS_ACTIVATION_FAILED",
  "PROCESS_ACTIVE",
  "CODE_GENERATING",
  "CODE_VERIFYING",
  "CODE_REVIEWING",
  "CODE_REPAIRING",
  "CODE_REVIEW",
  "CODE_PIPELINE_FAILED",
  "WRITING_ARTIFACTS",
  "ARTIFACT_WRITE_FAILED",
  "BUSINESS_ENTRY_CONFIGURING",
  "BUSINESS_ENTRY_CONFIG_FAILED",
  "COMPLETED",
] as const;

export type WorkflowState = (typeof WORKFLOW_STATES)[number];

export interface MockUser {
  userId: string;
  userName: string;
  departmentId?: string;
  departmentName?: string;
}

export interface AgentAuthenticatedUser {
  userId: string;
  username: string;
  realName: string;
  departmentId: string;
  departmentName: string;
  userType: "ADMIN" | "USER";
  administrator: boolean;
}

export interface AgentLoginCredentials {
  username: string;
  password: string;
}

export interface AgentPublicConfig {
  defaultTargetRoot: string;
}

export interface Participant {
  roleCode: string;
  roleName: string;
  responsibility: string;
}

export const REFERENCE_DATA_RESOURCES = [
  "FUTURES_ACCOUNTS",
  "EXCHANGES",
  "TRADING_CODES",
  "FUTURES_PRODUCTS",
] as const;

export type ReferenceDataResource = (typeof REFERENCE_DATA_RESOURCES)[number];

export interface ReferenceDataSource {
  resource: ReferenceDataResource;
  /** API parameter or path variable name -> upstream form field code. */
  parameterBindings?: Record<string, string>;
  /** Selected response property -> target form field code. */
  autofillBindings?: Record<string, string>;
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
  multiple?: boolean;
  readOnly?: boolean;
  referenceDataSource?: ReferenceDataSource;
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

export interface NodeFieldPermissionRequirement {
  nodeCode: string;
  fieldCode: string;
  visible: boolean;
  editable: boolean;
  required: boolean;
}

export type ProcessNodeType =
  | "START"
  | "USER_TASK"
  | "NOTICE"
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
  /** Plain-text message configuration persisted to the platform for NOTICE nodes. */
  noticeConfig?: { title?: string; content?: string };
  positionX: number;
  positionY: number;
  sortOrder: number;
}

export const DEFAULT_SYSTEM_CODE = "FINANCE_SYS_001";

export interface UserTaskRuntimeDefaultOptions {
  rejectEnabled?: boolean;
  rejectTargetNodeCodes?: string[];
}

/**
 * Creates independent default runtime-policy objects for a user task.  A factory
 * is used so editing one node in the UI cannot mutate the defaults of another.
 */
export function createDefaultUserTaskConfigs(): Pick<
  ProcessNodeRequirement,
  "listenerConfig" | "timeoutConfig" | "reminderConfig"
>;
export function createDefaultUserTaskConfigs(options: UserTaskRuntimeDefaultOptions): Pick<
  ProcessNodeRequirement,
  "listenerConfig" | "timeoutConfig" | "reminderConfig"
>;
export function createDefaultUserTaskConfigs(
  options: UserTaskRuntimeDefaultOptions = {},
): Pick<ProcessNodeRequirement, "listenerConfig" | "timeoutConfig" | "reminderConfig"> {
  const rejectEnabled = options.rejectEnabled ?? true;
  const rejectTargetNodeCodes = options.rejectTargetNodeCodes?.length ? options.rejectTargetNodeCodes : ["apply"];
  const taskActionRules: Record<string, unknown> = {
    directSend: { enabled: true, targetMode: "REJECT_SOURCE" },
  };
  if (rejectEnabled) {
    taskActionRules.reject = { enabled: true, targetNodeCodes: rejectTargetNodeCodes };
  }
  return {
    listenerConfig: {
      taskActionRules,
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
      messageTemplate: "您有待办，请及时处理。",
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

export interface FrontendSectionRequirement {
  sectionCode: string;
  title: string;
  fieldCodes: string[];
  sortOrder: number;
}

export interface FrontendDataQueryRequirement {
  queryCode: string;
  resource: "ACCOUNT_FUNDS";
  parameterBindings: Record<string, string>;
  loadMode: "ON_CHANGE" | "MANUAL";
  refreshable: boolean;
}

export interface FrontendCalculationRequirement {
  calculationCode: string;
  targetFieldCode: string;
  expression: string;
  dependencyFieldCodes: string[];
  decimalPlaces?: number;
  sortOrder: number;
}

export interface FrontendCheckRequirement {
  checkCode: string;
  checkName: string;
  description: string;
  appliesWhen?: string;
  passWhen: string;
  dependencyFieldCodes: string[];
  dataQueryCodes: string[];
  sortOrder: number;
}

export interface FrontendBehaviorRequirement {
  sections: FrontendSectionRequirement[];
  dataQueries: FrontendDataQueryRequirement[];
  calculations: FrontendCalculationRequirement[];
  checks: FrontendCheckRequirement[];
}

export interface BusinessRequirement {
  schemaVersion: "1.0" | "1.1" | "1.2";
  businessCode: string;
  businessName: string;
  entryDisplayName?: string;
  entryPageTitle?: string;
  systemCode: string;
  goal: string;
  participants: Participant[];
  formFields: FormFieldRequirement[];
  attachments: AttachmentRequirement[];
  nodes: ProcessNodeRequirement[];
  edges: ProcessEdgeRequirement[];
  nodeFieldPermissions?: NodeFieldPermissionRequirement[];
  businessRules: BusinessRule[];
  frontendBehavior?: FrontendBehaviorRequirement;
}

export type LegacyBusinessRequirement = Omit<
  BusinessRequirement,
  "schemaVersion" | "entryDisplayName" | "entryPageTitle" | "nodeFieldPermissions"
> & { schemaVersion: "1.0" };

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
  activeGeneration?: CodeGenerationSummary;
  lastError?: { code: string; message: string };
  allowedActions: string[];
}

export * from "./generation.js";
import type { CodeGenerationSummary } from "./generation.js";

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
    schemaVersion: { enum: ["1.0", "1.1", "1.2"] },
    businessCode: { type: "string" },
    businessName: { type: "string" },
    entryDisplayName: { type: "string" },
    entryPageTitle: { type: "string" },
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
          multiple: { type: "boolean" },
          readOnly: { type: "boolean" },
          referenceDataSource: {
            type: "object",
            additionalProperties: false,
            required: ["resource"],
            properties: {
              resource: { enum: REFERENCE_DATA_RESOURCES },
              parameterBindings: {
                type: "object",
                additionalProperties: { type: "string", minLength: 1 },
              },
              autofillBindings: {
                type: "object",
                additionalProperties: { type: "string", minLength: 1 },
              },
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
          nodeType: { enum: ["START", "USER_TASK", "NOTICE", "EXCLUSIVE_GATEWAY", "PARALLEL_SPLIT_GATEWAY", "PARALLEL_JOIN_GATEWAY", "END"] },
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
          noticeConfig: {
            type: "object",
            additionalProperties: false,
            properties: { title: { type: "string" }, content: { type: "string" } },
          },
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
    nodeFieldPermissions: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["nodeCode", "fieldCode", "visible", "editable", "required"],
        properties: {
          nodeCode: { type: "string" },
          fieldCode: { type: "string" },
          visible: { type: "boolean" },
          editable: { type: "boolean" },
          required: { type: "boolean" },
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
    frontendBehavior: {
      type: "object",
      additionalProperties: false,
      required: ["sections", "dataQueries", "calculations", "checks"],
      properties: {
        sections: {
          type: "array",
          items: {
            type: "object", additionalProperties: false,
            required: ["sectionCode", "title", "fieldCodes", "sortOrder"],
            properties: {
              sectionCode: { type: "string", minLength: 1 }, title: { type: "string", minLength: 1 },
              fieldCodes: { type: "array", items: { type: "string", minLength: 1 } },
              sortOrder: { type: "integer" },
            },
          },
        },
        dataQueries: {
          type: "array",
          items: {
            type: "object", additionalProperties: false,
            required: ["queryCode", "resource", "parameterBindings", "loadMode", "refreshable"],
            properties: {
              queryCode: { type: "string", minLength: 1 }, resource: { const: "ACCOUNT_FUNDS" },
              parameterBindings: { type: "object", additionalProperties: { type: "string", minLength: 1 } },
              loadMode: { enum: ["ON_CHANGE", "MANUAL"] }, refreshable: { type: "boolean" },
            },
          },
        },
        calculations: {
          type: "array",
          items: {
            type: "object", additionalProperties: false,
            required: ["calculationCode", "targetFieldCode", "expression", "dependencyFieldCodes", "sortOrder"],
            properties: {
              calculationCode: { type: "string", minLength: 1 }, targetFieldCode: { type: "string", minLength: 1 },
              expression: { type: "string", minLength: 1 },
              dependencyFieldCodes: { type: "array", items: { type: "string", minLength: 1 } },
              decimalPlaces: { type: "integer", minimum: 0, maximum: 12 }, sortOrder: { type: "integer" },
            },
          },
        },
        checks: {
          type: "array",
          items: {
            type: "object", additionalProperties: false,
            required: ["checkCode", "checkName", "description", "passWhen", "dependencyFieldCodes", "dataQueryCodes", "sortOrder"],
            properties: {
              checkCode: { type: "string", minLength: 1 }, checkName: { type: "string", minLength: 1 },
              description: { type: "string", minLength: 1 }, appliesWhen: { type: "string", minLength: 1 },
              passWhen: { type: "string", minLength: 1 },
              dependencyFieldCodes: { type: "array", items: { type: "string", minLength: 1 } },
              dataQueryCodes: { type: "array", items: { type: "string", minLength: 1 } },
              sortOrder: { type: "integer" },
            },
          },
        },
      },
    },
  },
} as const;

const ENTRY_APPLICATION_USER_TASK_CODES = ["apply", "manager_approve", "finance_confirm"];

export const ENTRY_APPLICATION_REQUIREMENT: BusinessRequirement = {
  schemaVersion: "1.2",
  businessCode: "entry_application",
  businessName: "入金申请",
  entryDisplayName: "入金申请",
  entryPageTitle: "发起入金申请",
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
    { nodeCode: "apply", nodeName: "申请", nodeType: "USER_TASK", approverRule: { type: "STARTER", config: {} }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs({ rejectEnabled: false, rejectTargetNodeCodes: ENTRY_APPLICATION_USER_TASK_CODES }), positionX: 260, positionY: 120, sortOrder: 2 },
    { nodeCode: "manager_approve", nodeName: "部门经理审批", nodeType: "USER_TASK", approverRule: { type: "ROLE_IN_DEPARTMENT", config: { roleCode: "department_manager", departmentFrom: "starter" } }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs({ rejectTargetNodeCodes: ENTRY_APPLICATION_USER_TASK_CODES }), positionX: 460, positionY: 120, sortOrder: 3 },
    { nodeCode: "finance_confirm", nodeName: "财务确认", nodeType: "USER_TASK", approverRule: { type: "ROLE", config: { roleCode: "finance" } }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs({ rejectTargetNodeCodes: ENTRY_APPLICATION_USER_TASK_CODES }), positionX: 680, positionY: 120, sortOrder: 4 },
    { nodeCode: "end", nodeName: "结束", nodeType: "END", positionX: 880, positionY: 120, sortOrder: 5 },
  ],
  edges: [
    { edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply", defaultEdge: false, sortOrder: 1 },
    { edgeCode: "e2", sourceNodeCode: "apply", targetNodeCode: "manager_approve", defaultEdge: false, sortOrder: 2 },
    { edgeCode: "e3", sourceNodeCode: "manager_approve", targetNodeCode: "finance_confirm", defaultEdge: false, sortOrder: 3 },
    { edgeCode: "e4", sourceNodeCode: "finance_confirm", targetNodeCode: "end", defaultEdge: false, sortOrder: 4 },
  ],
  nodeFieldPermissions: ENTRY_APPLICATION_USER_TASK_CODES.flatMap((nodeCode) =>
    [
      { fieldCode: "applicantName", required: true },
      { fieldCode: "amount", required: true },
      { fieldCode: "accountNo", required: true },
    ].map((field) => ({
      nodeCode,
      fieldCode: field.fieldCode,
      visible: true,
      editable: nodeCode === "apply",
      required: nodeCode === "apply" && field.required,
    })),
  ),
  businessRules: [],
};

const WAREHOUSE_PLEDGE_USER_TASK_CODES = ["apply", "risk_review"];

/** Fake Agent/E2E 使用的质押申请动态参考数据需求。 */
export const WAREHOUSE_PLEDGE_REQUIREMENT: BusinessRequirement = {
  schemaVersion: "1.2",
  businessCode: "warehouse_pledge",
  businessName: "仓单、国债（解）质押申请",
  entryDisplayName: "仓单、国债（解）质押申请",
  entryPageTitle: "发起仓单、国债（解）质押申请",
  systemCode: DEFAULT_SYSTEM_CODE,
  goal: "客户经理选择统一账户、交易所和多个期货品种，系统带出交易编码及合约参数后提交风控审核。",
  participants: [
    { roleCode: "account_manager", roleName: "客户经理", responsibility: "提交质押申请" },
    { roleCode: "risk_reviewer", roleName: "风控审核员", responsibility: "审核质押申请" },
  ],
  formFields: [
    {
      fieldCode: "futuresAccount", fieldName: "期货账号", fieldType: "string", controlType: "select",
      required: true, validation: {}, sortOrder: 1,
      referenceDataSource: {
        resource: "FUTURES_ACCOUNTS",
        autofillBindings: { customerName: "customerName" },
      },
    },
    { fieldCode: "customerName", fieldName: "客户名称", fieldType: "string", controlType: "input", required: true, readOnly: true, validation: {}, sortOrder: 2 },
    {
      fieldCode: "businessType", fieldName: "业务类型", fieldType: "select", controlType: "select",
      required: true, validation: {}, sortOrder: 3,
      options: ["仓单质押", "仓单解质押", "国债质押", "国债解质押"].map((value) => ({ label: value, value })),
    },
    {
      fieldCode: "exchangeCode", fieldName: "交易所", fieldType: "string", controlType: "select",
      required: true, validation: {}, sortOrder: 4,
      referenceDataSource: { resource: "EXCHANGES" },
    },
    {
      fieldCode: "tradingCode", fieldName: "交易编码", fieldType: "string", controlType: "input",
      required: true, readOnly: true, validation: {}, sortOrder: 5,
      referenceDataSource: {
        resource: "TRADING_CODES",
        parameterBindings: { accountNo: "futuresAccount", exchangeCode: "exchangeCode" },
      },
    },
    {
      fieldCode: "productCodes", fieldName: "期货品种", fieldType: "select", controlType: "select",
      required: true, multiple: true, validation: {}, sortOrder: 6,
      referenceDataSource: {
        resource: "FUTURES_PRODUCTS",
        parameterBindings: { exchangeCode: "exchangeCode" },
        autofillBindings: {
          contractMultiplier: "contractMultiplier",
          pledgeUnitQuantity: "pledgeUnitQuantity",
          previousSettlementPrice: "previousSettlementPrice",
        },
      },
    },
    { fieldCode: "quantity", fieldName: "数量（张）", fieldType: "number", controlType: "number", required: true, validation: { minimum: 1 }, sortOrder: 7 },
    { fieldCode: "contractMultiplier", fieldName: "合约乘数", fieldType: "number", controlType: "number", required: true, readOnly: true, validation: { minimum: 1 }, sortOrder: 8 },
    { fieldCode: "pledgeUnitQuantity", fieldName: "质押品单位数量", fieldType: "number", controlType: "number", required: true, readOnly: true, validation: { minimum: 1 }, sortOrder: 9 },
    { fieldCode: "previousSettlementPrice", fieldName: "昨结算价", fieldType: "number", controlType: "number", required: true, readOnly: true, validation: { minimum: 0.0001, maxDecimalPlaces: 4 }, sortOrder: 10 },
    { fieldCode: "amount", fieldName: "金额", fieldType: "number", controlType: "number", required: true, readOnly: true, validation: { maxDecimalPlaces: 4 }, sortOrder: 11 },
  ],
  attachments: [],
  nodes: [
    { nodeCode: "start", nodeName: "开始", nodeType: "START", positionX: 80, positionY: 120, sortOrder: 1 },
    { nodeCode: "apply", nodeName: "申请", nodeType: "USER_TASK", approverRule: { type: "STARTER", config: {} }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs({ rejectEnabled: false, rejectTargetNodeCodes: WAREHOUSE_PLEDGE_USER_TASK_CODES }), positionX: 280, positionY: 120, sortOrder: 2 },
    { nodeCode: "risk_review", nodeName: "风控审核", nodeType: "USER_TASK", approverRule: { type: "ROLE", config: { roleCode: "risk_reviewer" } }, multiInstanceMode: "SINGLE", ...createDefaultUserTaskConfigs({ rejectTargetNodeCodes: WAREHOUSE_PLEDGE_USER_TASK_CODES }), positionX: 500, positionY: 120, sortOrder: 3 },
    { nodeCode: "end", nodeName: "结束", nodeType: "END", positionX: 720, positionY: 120, sortOrder: 4 },
  ],
  edges: [
    { edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply", defaultEdge: false, sortOrder: 1 },
    { edgeCode: "e2", sourceNodeCode: "apply", targetNodeCode: "risk_review", defaultEdge: false, sortOrder: 2 },
    { edgeCode: "e3", sourceNodeCode: "risk_review", targetNodeCode: "end", defaultEdge: false, sortOrder: 3 },
  ],
  businessRules: [
    { ruleCode: "last_product_autofill", description: "多选品种最后一次选择负责带出合约乘数、质押品单位数量和昨结算价" },
  ],
  frontendBehavior: {
    sections: [
      { sectionCode: "customer", title: "客户信息", fieldCodes: ["futuresAccount", "customerName"], sortOrder: 1 },
      { sectionCode: "business", title: "业务信息", fieldCodes: ["businessType", "exchangeCode", "tradingCode", "productCodes", "quantity", "contractMultiplier", "pledgeUnitQuantity", "previousSettlementPrice", "amount"], sortOrder: 2 },
    ],
    dataQueries: [
      { queryCode: "accountFunds", resource: "ACCOUNT_FUNDS", parameterBindings: { accountNo: "futuresAccount" }, loadMode: "ON_CHANGE", refreshable: true },
    ],
    calculations: [
      {
        calculationCode: "amount", targetFieldCode: "amount",
        expression: "previousSettlementPrice * quantity * pledgeUnitQuantity * contractMultiplier * 0.8",
        dependencyFieldCodes: ["previousSettlementPrice", "quantity", "pledgeUnitQuantity", "contractMultiplier", "businessType"],
        decimalPlaces: 4, sortOrder: 1,
      },
    ],
    checks: [
      { checkCode: "pledge", checkName: "满足质押要求", description: "当前权益 + 本次金额满足质押比例，或实有货币资金满足比例", appliesWhen: "businessType == '仓单质押' || businessType == '国债质押'", passWhen: "accountFunds.currentEquity + amount >= 1.25 * (accountFunds.pledgeAmount + amount) || accountFunds.actualCash >= 0.25 * (accountFunds.pledgeAmount + amount)", dependencyFieldCodes: ["businessType", "amount"], dataQueryCodes: ["accountFunds"], sortOrder: 1 },
      { checkCode: "release", checkName: "满足解质押要求", description: "可用资金 + 本次金额大于等于零", appliesWhen: "businessType == '仓单解质押' || businessType == '国债解质押'", passWhen: "accountFunds.availableFunds + amount >= 0", dependencyFieldCodes: ["businessType", "amount"], dataQueryCodes: ["accountFunds"], sortOrder: 2 },
      { checkCode: "dce", checkName: "满足大商所特定要求", description: "大商所质押金额 + 本次金额不超过持仓保证金", appliesWhen: "(businessType == '仓单质押' || businessType == '国债质押') && exchangeCode == 'DCE'", passWhen: "accountFunds.dcePledgeAmount + amount <= accountFunds.dcePositionMargin", dependencyFieldCodes: ["businessType", "exchangeCode", "amount"], dataQueryCodes: ["accountFunds"], sortOrder: 3 },
      { checkCode: "czce", checkName: "满足郑商所特定要求", description: "郑商所质押金额 + 本次金额不超过 1.2 倍持仓保证金", appliesWhen: "(businessType == '仓单质押' || businessType == '国债质押') && exchangeCode == 'CZCE'", passWhen: "accountFunds.czcePledgeAmount + amount <= 1.2 * accountFunds.czcePositionMargin", dependencyFieldCodes: ["businessType", "exchangeCode", "amount"], dataQueryCodes: ["accountFunds"], sortOrder: 4 },
    ],
  },
};

export function normalizeBusinessRequirement(input: BusinessRequirement | LegacyBusinessRequirement): BusinessRequirement {
  const requirement = structuredClone(input) as BusinessRequirement;
  requirement.schemaVersion = "1.2";
  requirement.entryDisplayName ||= requirement.businessName;
  requirement.entryPageTitle ||= `发起${requirement.businessName}`;
  requirement.nodeFieldPermissions ||= requirement.nodes.flatMap((node) =>
    requirement.formFields.map((field) => ({
      nodeCode: node.nodeCode,
      fieldCode: field.fieldCode,
      visible: node.nodeType !== "START" && node.nodeType !== "END",
      editable: node.nodeCode === "apply",
      required: node.nodeCode === "apply" && field.required,
    })),
  );
  requirement.frontendBehavior ||= {
    sections: [{
      sectionCode: "business-info",
      title: "业务信息",
      fieldCodes: requirement.formFields.slice().sort((left, right) => left.sortOrder - right.sortOrder).map(({ fieldCode }) => fieldCode),
      sortOrder: 1,
    }],
    dataQueries: [],
    calculations: [],
    checks: [],
  };
  return requirement;
}

export function renderBusinessRequirementMarkdown(input: BusinessRequirement): string {
  const requirement = normalizeBusinessRequirement(input);
  const lines = [
    `# ${requirement.businessName}需求文档`,
    "",
    `- 业务编码：${requirement.businessCode}`,
    `- 入口名称：${requirement.entryDisplayName}`,
    `- 页面标题：${requirement.entryPageTitle}`,
    `- 发起接口：POST /api/workflow/processes/${requirement.businessCode}/start-submit`,
    "",
    "## 表单字段",
    ...requirement.formFields
      .slice()
      .sort((left, right) => left.sortOrder - right.sortOrder)
      .map((field) => `- ${field.fieldCode}：${field.fieldName}（${field.controlType}${field.required ? "，必填" : ""}）`),
    "",
    "## 附件",
    ...(requirement.attachments.length
      ? requirement.attachments
        .slice()
        .sort((left, right) => left.sortOrder - right.sortOrder)
        .map((attachment) => `- ${attachment.attachmentCode}：${attachment.attachmentName}`)
      : ["- 无"]),
    "",
    "## 节点字段权限",
    ...(requirement.nodeFieldPermissions || [])
      .map((permission) =>
        `- ${permission.nodeCode}.${permission.fieldCode}：visible=${permission.visible}, editable=${permission.editable}, required=${permission.required}`,
      ),
    "",
    "## 页面行为",
    ...requirement.frontendBehavior!.sections.slice().sort((a, b) => a.sortOrder - b.sortOrder)
      .map((section) => `- 分区 ${section.sectionCode}：${section.title}（${section.fieldCodes.join("、")}）`),
    ...requirement.frontendBehavior!.dataQueries.map((query) => `- 查询 ${query.queryCode}：${query.resource}`),
    ...requirement.frontendBehavior!.calculations.slice().sort((a, b) => a.sortOrder - b.sortOrder)
      .map((calculation) => `- 计算 ${calculation.calculationCode} → ${calculation.targetFieldCode}：${calculation.expression}`),
    ...requirement.frontendBehavior!.checks.slice().sort((a, b) => a.sortOrder - b.sortOrder)
      .map((check) => `- 核查 ${check.checkCode}：${check.checkName}；通过条件=${check.passWhen}`),
  ];
  return `${lines.join("\n")}\n`;
}
