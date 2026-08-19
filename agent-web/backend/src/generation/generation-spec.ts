import type {
  AttachmentRequirement,
  BusinessRequirement,
  GenerationTargetContract,
  ProcessNodeType,
} from "@flowmind/agent-contracts";

const JAVA_RESERVED = new Set([
  "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
  "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
  "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
  "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
  "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
  "true", "false", "null", "record", "sealed", "permits", "var", "yield",
]);

const TYPESCRIPT_RESERVED = new Set([
  "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else",
  "enum", "export", "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof",
  "new", "null", "return", "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void",
  "while", "with", "as", "implements", "interface", "let", "package", "private", "protected", "public",
  "static", "yield", "any", "boolean", "constructor", "declare", "get", "module", "namespace", "never",
  "number", "readonly", "require", "set", "string", "symbol", "type", "undefined", "unknown", "from", "of",
]);

const CODE_PATTERN = /^[A-Za-z][A-Za-z0-9]*$/;
const BUSINESS_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9]*(?:[_-][A-Za-z0-9]+)*$/;
const APPLY_SUCCESSOR_NODE_TYPES = new Set<ProcessNodeType>([
  "USER_TASK",
  "NOTICE",
  "EXCLUSIVE_GATEWAY",
  "PARALLEL_SPLIT_GATEWAY",
]);

export interface GenerationSpec {
  processCode: string;
  businessName: string;
  classPrefix: string;
  camelPrefix: string;
  packageSegment: string;
  kebabCode: string;
  javaPackage: string;
  apiPath: string;
  hasBusinessApi: boolean;
  routePath: string;
  routeName: string;
  applyAttachments: AttachmentRequirement[];
  files: string[];
  paths: {
    controller: string;
    service: string;
    requestDto: string;
    responseDto: string;
    controllerTest: string;
    serviceTest: string;
    view: string;
    viewTest: string;
    api: string;
    apiTest: string;
    routeRegistry: string;
    businessForm: string;
    businessFormTest: string;
    applyView: string;
    applyViewTest: string;
  };
}

export class GenerationRequirementError extends Error {
  constructor(readonly issues: string[]) {
    super("confirmed requirement cannot be converted into a safe generation specification");
  }
}

export function deriveGenerationSpec(
  requirement: BusinessRequirement,
  contract: GenerationTargetContract,
): GenerationSpec {
  const issues = validateGenerationRequirement(requirement);
  if (issues.length) throw new GenerationRequirementError(issues);

  const tokens = requirement.businessCode.split(/[_-]/).map((token) => token.toLowerCase());
  const classPrefix = tokens.map(capitalize).join("");
  const camelPrefix = `${tokens[0]}${tokens.slice(1).map(capitalize).join("")}`;
  const packageSegment = tokens.join("");
  const kebabCode = tokens.join("-");
  if (JAVA_RESERVED.has(packageSegment)) {
    throw new GenerationRequirementError([`业务编码 ${requirement.businessCode} 派生出的 Java 包名是保留字`]);
  }

  if (isFrontendOnlyContract(contract)) {
    const frontendModule = joinPath(contract.frontend.rootDir, contract.frontend.generatedModuleDir);
    const frontendApi = joinPath(contract.frontend.rootDir, contract.frontend.generatedApiDir || "src/api/generated");
    const hasBusinessApi = requirement.formFields.some(({ referenceDataSource }) => Boolean(referenceDataSource))
      || Boolean(requirement.frontendBehavior?.dataQueries.length);
    const paths = {
      controller: "",
      service: "",
      requestDto: "",
      responseDto: "",
      controllerTest: "",
      serviceTest: "",
      view: joinPath(frontendModule, kebabCode, "Apply.vue"),
      viewTest: joinPath(frontendModule, kebabCode, "__tests__", "Apply.spec.ts"),
      api: joinPath(frontendApi, kebabCode, `${kebabCode}.ts`),
      apiTest: joinPath(frontendApi, kebabCode, `${kebabCode}.spec.ts`),
      routeRegistry: joinPath(contract.frontend.rootDir, contract.frontend.routeRegistry),
      businessForm: joinPath(frontendModule, kebabCode, "BusinessForm.vue"),
      businessFormTest: joinPath(frontendModule, kebabCode, "__tests__", "BusinessForm.spec.ts"),
      applyView: joinPath(frontendModule, kebabCode, "Apply.vue"),
      applyViewTest: joinPath(frontendModule, kebabCode, "__tests__", "Apply.spec.ts"),
    };
    return {
      processCode: requirement.businessCode,
      businessName: requirement.businessName,
      classPrefix,
      camelPrefix,
      packageSegment,
      kebabCode,
      javaPackage: "",
      apiPath: `/api/workflow/processes/${requirement.businessCode}/start-submit`,
      hasBusinessApi,
      routePath: `/generated/${kebabCode}/apply`,
      routeName: `generated-${kebabCode}-apply`,
      applyAttachments: requirement.attachments
        .filter((attachment) => attachment.applicableNodeCodes.includes("apply"))
        .sort((left, right) => left.sortOrder - right.sortOrder),
      files: [
        paths.businessForm,
        paths.businessFormTest,
        paths.applyView,
        paths.applyViewTest,
        ...(hasBusinessApi ? [paths.api, paths.apiTest] : []),
        paths.routeRegistry,
      ],
      paths,
    };
  }

  const backend = contract.backend!;
  const backendSource = joinPath(backend.rootDir, backend.generatedSourceDir, packageSegment);
  const backendTest = joinPath(backend.rootDir, backend.generatedTestDir, packageSegment);
  const frontendView = joinPath(contract.frontend.rootDir, contract.frontend.generatedViewDir!);
  const frontendApi = joinPath(contract.frontend.rootDir, contract.frontend.generatedApiDir!);
  const frontendTest = joinPath(contract.frontend.rootDir, contract.frontend.generatedTestDir!);
  const paths = {
    controller: joinPath(backendSource, `${classPrefix}Controller.java`),
    service: joinPath(backendSource, `${classPrefix}Service.java`),
    requestDto: joinPath(backendSource, "dto", `${classPrefix}SubmitRequest.java`),
    responseDto: joinPath(backendSource, "dto", `${classPrefix}SubmitResponse.java`),
    controllerTest: joinPath(backendTest, `${classPrefix}ControllerTest.java`),
    serviceTest: joinPath(backendTest, `${classPrefix}ServiceTest.java`),
    view: joinPath(frontendView, kebabCode, `${classPrefix}Apply.vue`),
    viewTest: joinPath(frontendTest, `${classPrefix}Apply.spec.ts`),
    api: joinPath(frontendApi, `${kebabCode}.ts`),
    apiTest: joinPath(frontendApi, `${kebabCode}.spec.ts`),
    routeRegistry: joinPath(contract.frontend.rootDir, contract.frontend.routeRegistry),
    businessForm: joinPath(frontendView, kebabCode, "BusinessForm.vue"),
    businessFormTest: joinPath(frontendView, kebabCode, "__tests__", "BusinessForm.spec.ts"),
    applyView: joinPath(frontendView, kebabCode, "Apply.vue"),
    applyViewTest: joinPath(frontendView, kebabCode, "__tests__", "Apply.spec.ts"),
  };

  return {
    processCode: requirement.businessCode,
    businessName: requirement.businessName,
    classPrefix,
    camelPrefix,
    packageSegment,
    kebabCode,
    javaPackage: `${backend.basePackage}.generated.${packageSegment}`,
    apiPath: `/api/generated/${kebabCode}/submit`,
    hasBusinessApi: true,
    routePath: `/generated/${kebabCode}/apply`,
    routeName: `generated-${kebabCode}-apply`,
    applyAttachments: requirement.attachments
      .filter((attachment) => attachment.applicableNodeCodes.includes("apply"))
      .sort((left, right) => left.sortOrder - right.sortOrder),
    files: Object.values(paths),
    paths,
  };
}

export function validateGenerationRequirement(requirement: BusinessRequirement): string[] {
  const issues: string[] = [];
  const businessCode = requirement.businessCode.trim();
  if (businessCode !== requirement.businessCode || !BUSINESS_CODE_PATTERN.test(businessCode)) {
    issues.push("业务编码必须以英文字母开头，且只能包含英文字母、数字、下划线或连字符");
  }
  if (!requirement.businessName.trim()) issues.push("业务名称不能为空");

  const seen = new Map<string, string>();
  for (const field of requirement.formFields) validatePropertyCode(field.fieldCode, `表单字段 ${field.fieldName}`, seen, issues);
  for (const attachment of requirement.attachments) validatePropertyCode(attachment.attachmentCode, `附件 ${attachment.attachmentName}`, seen, issues);

  const apply = requirement.nodes.find((node) => node.nodeCode === "apply");
  const outgoing = requirement.edges.filter((edge) => edge.sourceNodeCode === "apply");
  const next = outgoing.length === 1
    ? requirement.nodes.find((node) => node.nodeCode === outgoing[0].targetNodeCode)
    : undefined;
  if (apply?.nodeType !== "USER_TASK" || apply.approverRule?.type !== "STARTER") {
    issues.push("首个发起任务必须使用节点编码 apply、节点类型 USER_TASK 和 STARTER 审批人规则");
  }
  if (outgoing.length !== 1 || !next || !APPLY_SUCCESSOR_NODE_TYPES.has(next.nodeType)) {
    issues.push("apply 必须且只能流向一个后续用户任务、知会节点、排他网关或并行分支网关");
  }
  return [...new Set(issues)];
}

function validatePropertyCode(code: string, label: string, seen: Map<string, string>, issues: string[]): void {
  if (!CODE_PATTERN.test(code)) {
    issues.push(`${label}编码 ${code || "<空>"} 必须是以英文字母开头的 Java/TypeScript 标识符`);
    return;
  }
  const normalized = code.toLowerCase();
  if (JAVA_RESERVED.has(normalized) || TYPESCRIPT_RESERVED.has(normalized)) {
    issues.push(`${label}编码 ${code} 是 Java/TypeScript 保留字`);
  }
  const previous = seen.get(normalized);
  if (previous) issues.push(`${label}编码 ${code} 与 ${previous} 在代码生成后冲突`);
  else seen.set(normalized, `${label}编码 ${code}`);
}

function capitalize(value: string): string {
  return `${value.slice(0, 1).toUpperCase()}${value.slice(1)}`;
}

function joinPath(...parts: string[]): string {
  return parts.map((part) => part.replace(/\\/g, "/").replace(/^\/+|\/+$/g, "")).filter(Boolean).join("/");
}

function isFrontendOnlyContract(contract: GenerationTargetContract): contract is GenerationTargetContract & {
  contractVersion: "2.0" | "2.1";
  generationMode: "FRONTEND_FORM_ONLY" | "FRONTEND_ONLY";
  frontend: GenerationTargetContract["frontend"] & { generatedModuleDir: string; generatedApiDir?: string };
} {
  return ["FRONTEND_FORM_ONLY", "FRONTEND_ONLY"].includes((contract as { generationMode?: string }).generationMode || "");
}
