export const EVALUATION_SPLITS = [
  "DEVELOPMENT",
  "HIDDEN_REGRESSION",
  "HIDDEN_ADVERSARIAL",
] as const;

export type EvaluationSplit = (typeof EVALUATION_SPLITS)[number];

export const EVALUATION_OUTCOMES = ["GENERATION_READY", "BLOCKED_REQUIREMENT"] as const;
export type EvaluationExpectedOutcome = (typeof EVALUATION_OUTCOMES)[number];

export interface RequirementIrField {
  fieldCode: string;
  fieldName: string;
  valueType: "STRING" | "NUMBER" | "DATE" | "BOOLEAN" | "ENUM";
  control: "INPUT" | "TEXTAREA" | "NUMBER" | "DATE_PICKER" | "CHECKBOX" | "SELECT";
  required: boolean;
  readOnly: boolean;
  multiple: boolean;
  validation: Record<string, unknown>;
  options?: Array<{ label: string; value: string }>;
  referenceData?: {
    resource: "FUTURES_ACCOUNTS" | "EXCHANGES" | "TRADING_CODES" | "FUTURES_PRODUCTS";
    parameterBindings: Record<string, string>;
    autofillBindings: Record<string, string>;
  };
}

export interface RequirementIrDraft {
  irVersion: "0.1";
  generationMode: "CREATE_STANDARD_MODULE" | "MODIFY_EXISTING_MODULE";
  identity: {
    businessCode: string;
    businessName: string;
    pageTitle: string;
    goal: string;
  };
  fields: RequirementIrField[];
  attachments: Array<{
    attachmentCode: string;
    attachmentName: string;
    required: boolean;
    allowedExtensions: string[];
    maxSizeBytes: number;
    minCount: number;
    maxCount: number;
  }>;
  sections: Array<{
    sectionCode: string;
    title: string;
    fieldCodes: string[];
  }>;
  dataQueries: Array<{
    queryCode: string;
    resource: "ACCOUNT_FUNDS";
    parameterBindings: Record<string, string>;
    loadMode: "ON_CHANGE" | "MANUAL";
    refreshable: boolean;
  }>;
  calculations: Array<{
    calculationCode: string;
    targetFieldCode: string;
    expression: string;
    dependencyFieldCodes: string[];
    decimalPlaces?: number;
  }>;
  checks: Array<{
    checkCode: string;
    name?: string;
    description: string;
    appliesWhen?: string;
    passWhen: string;
    dependencyFieldCodes: string[];
    dataQueryCodes: string[];
  }>;
  submission: {
    processCode: string;
    action: "START_AND_SUBMIT";
    payloadFieldCodes: string[];
  };
  ambiguities: Array<{
    ambiguityCode: string;
    impact: "BLOCKING" | "DEFAULTABLE";
    status: "OPEN" | "RESOLVED";
    question: string;
    resolution?: string;
  }>;
  sourceRefs: string[];
}

export interface EvaluationAssertion {
  assertionId: string;
  severity: "CRITICAL" | "NON_CRITICAL";
  area: "IR" | "FILES" | "UI" | "VALIDATION" | "DATA" | "CALCULATION" | "SUBMISSION" | "BOUNDARY";
  description: string;
  oracle: {
    kind: "IR_PATH" | "FILE_EXISTS" | "SOURCE_CONTAINS" | "UI_BEHAVIOR" | "QUALITY_GATE" | "GENERATION_BLOCKED";
    target: string;
    operator: "EQUALS" | "CONTAINS" | "MATCHES" | "EXISTS" | "TRUTHY";
    expected: string | number | boolean;
  };
}

export interface EvaluationTask {
  taskId: string;
  split: EvaluationSplit;
  title: string;
  difficulty: "BASIC" | "COMPOSITE" | "EXTENSION" | "ADVERSARIAL";
  tags: string[];
  input: {
    userRequest: string;
    existingModule?: string;
  };
  expectedOutcome: EvaluationExpectedOutcome;
  requirementIr: RequirementIrDraft;
  assertions: EvaluationAssertion[];
}

export interface EvaluationCatalog {
  catalogVersion: "1.0";
  irVersion: "0.1";
  frozenAt: string;
  tasks: EvaluationTask[];
}

const stringMapSchema = {
  type: "object",
  additionalProperties: { type: "string", minLength: 1 },
} as const;

const stringArraySchema = {
  type: "array",
  uniqueItems: true,
  items: { type: "string", minLength: 1 },
} as const;

export const requirementIrDraftSchema = {
  $id: "RequirementIrDraft",
  type: "object",
  additionalProperties: false,
  required: [
    "irVersion", "generationMode", "identity", "fields", "attachments", "sections", "dataQueries",
    "calculations", "checks", "submission", "ambiguities", "sourceRefs",
  ],
  properties: {
    irVersion: { const: "0.1" },
    generationMode: { enum: ["CREATE_STANDARD_MODULE", "MODIFY_EXISTING_MODULE"] },
    identity: {
      type: "object", additionalProperties: false,
      required: ["businessCode", "businessName", "pageTitle", "goal"],
      properties: {
        businessCode: { type: "string", pattern: "^[a-z][a-z0-9_-]*$" },
        businessName: { type: "string", minLength: 1 },
        pageTitle: { type: "string", minLength: 1 },
        goal: { type: "string", minLength: 1 },
      },
    },
    fields: {
      type: "array", minItems: 1,
      items: {
        type: "object", additionalProperties: false,
        required: ["fieldCode", "fieldName", "valueType", "control", "required", "readOnly", "multiple", "validation"],
        properties: {
          fieldCode: { type: "string", pattern: "^[a-z][A-Za-z0-9]*$" },
          fieldName: { type: "string", minLength: 1 },
          valueType: { enum: ["STRING", "NUMBER", "DATE", "BOOLEAN", "ENUM"] },
          control: { enum: ["INPUT", "TEXTAREA", "NUMBER", "DATE_PICKER", "CHECKBOX", "SELECT"] },
          required: { type: "boolean" }, readOnly: { type: "boolean" }, multiple: { type: "boolean" },
          validation: { type: "object" },
          options: {
            type: "array", minItems: 1,
            items: {
              type: "object", additionalProperties: false, required: ["label", "value"],
              properties: { label: { type: "string", minLength: 1 }, value: { type: "string", minLength: 1 } },
            },
          },
          referenceData: {
            type: "object", additionalProperties: false,
            required: ["resource", "parameterBindings", "autofillBindings"],
            properties: {
              resource: { enum: ["FUTURES_ACCOUNTS", "EXCHANGES", "TRADING_CODES", "FUTURES_PRODUCTS"] },
              parameterBindings: stringMapSchema, autofillBindings: stringMapSchema,
            },
          },
        },
      },
    },
    attachments: {
      type: "array",
      items: {
        type: "object", additionalProperties: false,
        required: ["attachmentCode", "attachmentName", "required", "allowedExtensions", "maxSizeBytes", "minCount", "maxCount"],
        properties: {
          attachmentCode: { type: "string", pattern: "^[a-z][A-Za-z0-9]*$" },
          attachmentName: { type: "string", minLength: 1 }, required: { type: "boolean" },
          allowedExtensions: stringArraySchema,
          maxSizeBytes: { type: "integer", minimum: 1 },
          minCount: { type: "integer", minimum: 0 }, maxCount: { type: "integer", minimum: 1 },
        },
      },
    },
    sections: {
      type: "array", minItems: 1,
      items: {
        type: "object", additionalProperties: false, required: ["sectionCode", "title", "fieldCodes"],
        properties: { sectionCode: { type: "string", minLength: 1 }, title: { type: "string", minLength: 1 }, fieldCodes: stringArraySchema },
      },
    },
    dataQueries: {
      type: "array",
      items: {
        type: "object", additionalProperties: false,
        required: ["queryCode", "resource", "parameterBindings", "loadMode", "refreshable"],
        properties: {
          queryCode: { type: "string", minLength: 1 }, resource: { const: "ACCOUNT_FUNDS" },
          parameterBindings: stringMapSchema, loadMode: { enum: ["ON_CHANGE", "MANUAL"] }, refreshable: { type: "boolean" },
        },
      },
    },
    calculations: {
      type: "array",
      items: {
        type: "object", additionalProperties: false,
        required: ["calculationCode", "targetFieldCode", "expression", "dependencyFieldCodes"],
        properties: {
          calculationCode: { type: "string", minLength: 1 }, targetFieldCode: { type: "string", minLength: 1 },
          expression: { type: "string", minLength: 1 }, dependencyFieldCodes: stringArraySchema,
          decimalPlaces: { type: "integer", minimum: 0, maximum: 12 },
        },
      },
    },
    checks: {
      type: "array",
      items: {
        type: "object", additionalProperties: false,
        required: ["checkCode", "description", "passWhen", "dependencyFieldCodes", "dataQueryCodes"],
        properties: {
          checkCode: { type: "string", minLength: 1 }, name: { type: "string", minLength: 1 }, description: { type: "string", minLength: 1 },
          appliesWhen: { type: "string", minLength: 1 }, passWhen: { type: "string", minLength: 1 },
          dependencyFieldCodes: stringArraySchema, dataQueryCodes: stringArraySchema,
        },
      },
    },
    submission: {
      type: "object", additionalProperties: false, required: ["processCode", "action", "payloadFieldCodes"],
      properties: {
        processCode: { type: "string", minLength: 1 }, action: { const: "START_AND_SUBMIT" },
        payloadFieldCodes: stringArraySchema,
      },
    },
    ambiguities: {
      type: "array",
      items: {
        type: "object", additionalProperties: false,
        required: ["ambiguityCode", "impact", "status", "question"],
        properties: {
          ambiguityCode: { type: "string", minLength: 1 }, impact: { enum: ["BLOCKING", "DEFAULTABLE"] },
          status: { enum: ["OPEN", "RESOLVED"] }, question: { type: "string", minLength: 1 }, resolution: { type: "string", minLength: 1 },
        },
      },
    },
    sourceRefs: stringArraySchema,
  },
} as const;

export const evaluationCatalogSchema = {
  $id: "EvaluationCatalog",
  type: "object",
  additionalProperties: false,
  required: ["catalogVersion", "irVersion", "frozenAt", "tasks"],
  properties: {
    catalogVersion: { const: "1.0" }, irVersion: { const: "0.1" },
    frozenAt: { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" },
    tasks: {
      type: "array", minItems: 1,
      items: {
        type: "object", additionalProperties: false,
        required: ["taskId", "split", "title", "difficulty", "tags", "input", "expectedOutcome", "requirementIr", "assertions"],
        properties: {
          taskId: { type: "string", pattern: "^[a-z0-9][a-z0-9-]*$" },
          split: { enum: EVALUATION_SPLITS }, title: { type: "string", minLength: 1 },
          difficulty: { enum: ["BASIC", "COMPOSITE", "EXTENSION", "ADVERSARIAL"] },
          tags: stringArraySchema,
          input: {
            type: "object", additionalProperties: false, required: ["userRequest"],
            properties: { userRequest: { type: "string", minLength: 1 }, existingModule: { type: "string", minLength: 1 } },
          },
          expectedOutcome: { enum: EVALUATION_OUTCOMES }, requirementIr: requirementIrDraftSchema,
          assertions: {
            type: "array", minItems: 1,
            items: {
              type: "object", additionalProperties: false,
              required: ["assertionId", "severity", "area", "description", "oracle"],
              properties: {
                assertionId: { type: "string", minLength: 1 }, severity: { enum: ["CRITICAL", "NON_CRITICAL"] },
                area: { enum: ["IR", "FILES", "UI", "VALIDATION", "DATA", "CALCULATION", "SUBMISSION", "BOUNDARY"] },
                description: { type: "string", minLength: 1 },
                oracle: {
                  type: "object", additionalProperties: false, required: ["kind", "target", "operator", "expected"],
                  properties: {
                    kind: { enum: ["IR_PATH", "FILE_EXISTS", "SOURCE_CONTAINS", "UI_BEHAVIOR", "QUALITY_GATE", "GENERATION_BLOCKED"] },
                    target: { type: "string", minLength: 1 }, operator: { enum: ["EQUALS", "CONTAINS", "MATCHES", "EXISTS", "TRUTHY"] },
                    expected: { type: ["string", "number", "boolean"] },
                  },
                },
              },
            },
          },
        },
      },
    },
  },
} as const;
