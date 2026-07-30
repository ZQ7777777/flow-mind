import AjvModule, { type ErrorObject } from "ajv";
import {
  businessRequirementSchema,
  type BusinessRequirement,
  type ProcessNodeRequirement,
} from "@flowmind/agent-contracts";

export interface RequirementValidation {
  structurallyValid: boolean;
  readyForReview: boolean;
  missingItems: string[];
  ambiguities: string[];
  schemaErrors: ErrorObject[];
}

const ajv = new (AjvModule as any)({ allErrors: true, strict: false });
const validateSchema = ajv.compile(businessRequirementSchema);

export function validateRequirement(input: unknown): RequirementValidation {
  const structurallyValid = validateSchema(input);
  if (!structurallyValid) {
    return {
      structurallyValid: false,
      readyForReview: false,
      missingItems: [],
      ambiguities: [],
      schemaErrors: [...(validateSchema.errors || [])],
    };
  }
  const requirement = input as BusinessRequirement;
  const missing = new Set<string>();
  const ambiguities = new Set<string>();
  const nonEmpty = (value: string | undefined) => Boolean(value?.trim());
  if (!nonEmpty(requirement.businessCode)) missing.add("业务编码");
  if (!nonEmpty(requirement.businessName)) missing.add("业务名称");
  if (!nonEmpty(requirement.systemCode)) missing.add("系统编码");
  if (!nonEmpty(requirement.goal)) missing.add("办理目标");
  if (requirement.participants.length === 0) missing.add("参与角色");

  addDuplicateIssues(requirement.formFields.map((item) => item.fieldCode), "表单字段编码", ambiguities);
  addDuplicateIssues(requirement.attachments.map((item) => item.attachmentCode), "附件编码", ambiguities);
  addDuplicateIssues(requirement.nodes.map((item) => item.nodeCode), "节点编码", ambiguities);
  addDuplicateIssues(requirement.edges.map((item) => item.edgeCode), "连线编码", ambiguities);

  const nodeByCode = new Map(requirement.nodes.map((node) => [node.nodeCode, node]));
  const starts = requirement.nodes.filter((node) => node.nodeType === "START");
  const ends = requirement.nodes.filter((node) => node.nodeType === "END");
  if (starts.length !== 1) ambiguities.add("流程必须且只能有一个开始节点");
  if (ends.length === 0) missing.add("结束节点");

  for (const field of requirement.formFields) {
    if (!nonEmpty(field.fieldCode) || !nonEmpty(field.fieldName)) missing.add("完整的表单字段");
    if (field.fieldType === "select" && (!field.options || field.options.length === 0)) {
      missing.add(`下拉字段 ${field.fieldCode || field.fieldName} 的选项`);
    }
  }
  for (const attachment of requirement.attachments) {
    if (attachment.required && attachment.minCount < 1) {
      ambiguities.add(`必填附件 ${attachment.attachmentCode} 的最小数量必须大于等于 1`);
    }
    if (attachment.maxCount < attachment.minCount) {
      ambiguities.add(`附件 ${attachment.attachmentCode} 的最大数量不能小于最小数量`);
    }
    for (const code of attachment.applicableNodeCodes) {
      if (!nodeByCode.has(code)) ambiguities.add(`附件 ${attachment.attachmentCode} 引用了不存在的节点 ${code}`);
    }
  }
  for (const node of requirement.nodes) validateNode(node, nodeByCode, missing, ambiguities);
  for (const edge of requirement.edges) {
    if (!nodeByCode.has(edge.sourceNodeCode) || !nodeByCode.has(edge.targetNodeCode)) {
      ambiguities.add(`连线 ${edge.edgeCode} 引用了不存在的节点`);
    }
  }
  if (starts.length === 1) validateReachability(starts[0], requirement, missing);

  return {
    structurallyValid: true,
    readyForReview: missing.size === 0 && ambiguities.size === 0,
    missingItems: [...missing],
    ambiguities: [...ambiguities],
    schemaErrors: [],
  };
}

function addDuplicateIssues(values: string[], label: string, target: Set<string>): void {
  const seen = new Set<string>();
  for (const value of values) {
    if (seen.has(value)) target.add(`${label}重复：${value}`);
    seen.add(value);
  }
}

function validateNode(
  node: ProcessNodeRequirement,
  nodeByCode: Map<string, ProcessNodeRequirement>,
  missing: Set<string>,
  ambiguities: Set<string>,
): void {
  if (node.nodeType === "USER_TASK") {
    if (!node.approverRule) missing.add(`用户任务 ${node.nodeName} 的审批人规则`);
    if (!node.multiInstanceMode) missing.add(`用户任务 ${node.nodeName} 的多人模式`);
  }
  if (node.nodeType.startsWith("PARALLEL_")) {
    if (!node.pairedGatewayCode) {
      missing.add(`并行网关 ${node.nodeName} 的配对网关`);
    } else if (!nodeByCode.has(node.pairedGatewayCode)) {
      ambiguities.add(`并行网关 ${node.nodeName} 的配对节点不存在`);
    }
  }
}

function validateReachability(
  start: ProcessNodeRequirement,
  requirement: BusinessRequirement,
  missing: Set<string>,
): void {
  const outgoing = new Map<string, string[]>();
  for (const edge of requirement.edges) {
    outgoing.set(edge.sourceNodeCode, [...(outgoing.get(edge.sourceNodeCode) || []), edge.targetNodeCode]);
  }
  const visited = new Set<string>();
  const queue = [start.nodeCode];
  while (queue.length) {
    const current = queue.shift()!;
    if (visited.has(current)) continue;
    visited.add(current);
    queue.push(...(outgoing.get(current) || []));
  }
  for (const node of requirement.nodes) {
    if (!visited.has(node.nodeCode)) missing.add(`从开始节点可达的节点：${node.nodeName}`);
  }
  if (!requirement.nodes.some((node) => node.nodeType === "END" && visited.has(node.nodeCode))) {
    missing.add("从开始节点可达的结束路径");
  }
}
