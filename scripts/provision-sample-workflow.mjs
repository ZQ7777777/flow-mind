import { readFile } from "node:fs/promises";

const baseUrl = (process.env.FLOW_MIND_BASE_URL || "http://127.0.0.1:8081").replace(/\/$/, "");
const definitionPath = new URL("../outputs/%E4%BB%93%E5%8D%95%E5%9B%BD%E5%80%BA%E8%A7%A3%E8%B4%A8%E6%8A%BC%E6%B5%81%E7%A8%8B%E5%AE%9A%E4%B9%89.json", import.meta.url);
const requirement = JSON.parse(await readFile(definitionPath, "utf8"));

let cookie = "";

async function request(method, path, body) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(cookie ? { Cookie: cookie } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (path === "/api/auth/login") cookie = response.headers.get("set-cookie")?.split(";", 1)[0] || "";
  const text = await response.text();
  const payload = text ? JSON.parse(text) : undefined;
  if (!response.ok) throw new Error(`${method} ${path} failed (${response.status}): ${payload?.message || text}`);
  return payload;
}

await request("POST", "/api/auth/login", {
  username: process.env.FLOW_MIND_ADMIN_USERNAME || "admin01",
  password: process.env.FLOW_MIND_ADMIN_PASSWORD || "123456",
});

const query = new URLSearchParams({ processCode: requirement.businessCode, pageNo: "1", pageSize: "100" });
const definitions = await request("GET", `/api/admin/process-definitions?${query}`);
let definition = definitions.records.find((item) => item.definitionStatus === "PUBLISHED" && item.activationStatus === "ACTIVE");

if (!definition) {
  definition = definitions.records.find((item) => item.definitionStatus === "DRAFT");
  if (!definition) {
    definition = await request("POST", "/api/admin/process-definitions", {
      operationId: `sample-create-${Date.now()}`,
      processCode: requirement.businessCode,
      processName: requirement.businessName,
      systemCode: requirement.systemCode,
      remark: requirement.goal,
    });
  }

  const attachmentTemplates = new Map();
  for (const attachment of requirement.attachments) {
    const templateQuery = new URLSearchParams({ attachmentCode: attachment.attachmentCode, templateStatus: "ENABLED" });
    const candidates = await request("GET", `/api/admin/attachment-templates?${templateQuery}`);
    const expectedExtensions = normalizeExtensions(attachment.allowedExtensions);
    let template = candidates
      .filter((item) => item.attachmentName === attachment.attachmentName
        && Number(item.maxSizeBytes) === attachment.maxSizeBytes
        && JSON.stringify(normalizeExtensions(item.allowedExtensions || [])) === JSON.stringify(expectedExtensions))
      .sort((left, right) => Number(right.templateVersion || 0) - Number(left.templateVersion || 0))[0];
    if (!template) {
      template = await request("POST", "/api/admin/attachment-templates", {
        attachmentCode: attachment.attachmentCode,
        attachmentName: attachment.attachmentName,
        description: attachment.description || "",
        allowedExtensions: expectedExtensions,
        maxSizeBytes: attachment.maxSizeBytes,
      });
    }
    attachmentTemplates.set(attachment.attachmentCode, template);
  }

  await request("PUT", `/api/admin/process-definitions/${encodeURIComponent(definition.id)}/graph`, {
    operationId: `sample-save-${definition.id}`,
    nodes: requirement.nodes.map((node) => ({
      nodeCode: node.nodeCode,
      nodeName: node.nodeName,
      nodeType: node.nodeType,
      pairedGatewayCode: node.pairedGatewayCode,
      approverRuleType: node.approverRule?.type,
      approverRuleConfig: node.approverRule ? stableJson(node.approverRule.config) : null,
      multiInstanceMode: ["USER_TASK", "NOTICE"].includes(node.nodeType) ? node.multiInstanceMode || "SINGLE" : "SINGLE",
      listenerConfig: node.nodeType === "USER_TASK" && node.listenerConfig ? stableJson(node.listenerConfig) : null,
      timeoutConfig: node.nodeType === "USER_TASK" && node.timeoutConfig ? stableJson(node.timeoutConfig) : null,
      reminderConfig: node.nodeType === "USER_TASK" && node.reminderConfig ? stableJson(node.reminderConfig) : null,
      noticeConfig: node.nodeType === "NOTICE" && node.noticeConfig ? stableJson(node.noticeConfig) : null,
      positionX: node.positionX,
      positionY: node.positionY,
      sortOrder: node.sortOrder,
    })),
    edges: requirement.edges,
    formFields: requirement.formFields.map((field) => ({
      fieldCode: field.fieldCode,
      fieldName: field.fieldName,
      fieldType: field.fieldType,
      controlType: field.controlType,
      required: field.required,
      validationRule: stableJson({
        ...field.validation,
        ...(field.options ? { options: field.options } : {}),
        ...(field.multiple ? { multiple: true } : {}),
      }),
      defaultValue: field.defaultValue,
      sortOrder: field.sortOrder,
    })),
    attachmentConfigs: requirement.attachments.map((attachment) => ({
      definitionId: definition.id,
      attachmentTemplateId: attachmentTemplates.get(attachment.attachmentCode).attachmentTemplateId,
      attachmentCode: attachment.attachmentCode,
      required: attachment.required,
      minCount: attachment.minCount,
      maxCount: attachment.maxCount,
      applicableNodeCodes: attachment.applicableNodeCodes,
      sortOrder: attachment.sortOrder,
    })),
  });

  const validation = await request("GET", `/api/admin/process-definitions/${encodeURIComponent(definition.id)}/publish-validation`);
  if (!validation.valid) throw new Error(`warehouse_pledge publish validation failed: ${JSON.stringify(validation.issues)}`);
  definition = await request("POST", `/api/admin/process-definitions/${encodeURIComponent(definition.id)}/publish`, {
    operationId: `sample-publish-${definition.id}`,
  });
  definition = await request("POST", `/api/admin/process-definitions/${encodeURIComponent(definition.id)}/activate`, {
    operationId: `sample-activate-${definition.id}`,
  });
}

const entry = await request("PUT", `/api/admin/business-entry-configs/by-definition/${encodeURIComponent(definition.id)}`, {
  entryDisplayName: requirement.entryDisplayName || requirement.businessName,
  entryPageUrl: "/generated/warehouse-pledge/apply",
  entrySource: "MANUAL",
  enabled: true,
  remark: "sample 仓单、国债（解）质押完整流程入口",
});

console.log(JSON.stringify({
  definitionId: definition.id,
  processCode: definition.processCode,
  definitionStatus: definition.definitionStatus,
  activationStatus: definition.activationStatus,
  entryPageUrl: entry.entryPageUrl,
  enabled: entry.enabled,
}, null, 2));

function normalizeExtensions(values) {
  return [...new Set(values.map((value) => value.trim().replace(/^\./, "").toLowerCase()).filter(Boolean))].sort();
}

function stableJson(value) {
  return JSON.stringify(sortValue(value));
}

function sortValue(value) {
  if (Array.isArray(value)) return value.map(sortValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortValue(value[key])]));
  }
  return value;
}
