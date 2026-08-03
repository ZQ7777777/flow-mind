import { HttpStatus, Injectable } from "@nestjs/common";
import type {
  AttachmentRequirement,
  BusinessRequirement,
  MockUser,
  ProcessPreview,
  ValidationResult,
} from "@flowmind/agent-contracts";
import { loadConfig } from "../config.js";
import { AgentError } from "../common/agent-error.js";

export interface PlatformOperations {
  createOperationId: string;
  saveOperationId: string;
  publishOperationId: string;
  activateOperationId: string;
}

@Injectable()
export class PlatformClientService {
  private readonly config = loadConfig();

  async ping(): Promise<void> {
    await this.request("GET", "/api/platform/definitions?pageNo=1&pageSize=1", undefined, {
      userId: "user_sales",
      userName: "Sales User",
    });
  }

  async createDefinition(requirement: BusinessRequirement, user: MockUser, operationId: string): Promise<any> {
    return this.request("POST", "/api/platform/definitions", {
      operationId,
      processCode: requirement.businessCode,
      processName: requirement.businessName,
      systemCode: requirement.systemCode,
      remark: requirement.goal,
      operatorUserId: user.userId,
    }, user);
  }

  async findAttachmentTemplates(attachmentCode: string, user: MockUser): Promise<any[]> {
    const query = new URLSearchParams({ attachmentCode, templateStatus: "ENABLED" });
    return this.request("GET", `/api/platform/attachment-templates?${query}`, undefined, user);
  }

  async createAttachmentTemplate(attachment: AttachmentRequirement, user: MockUser): Promise<any> {
    return this.request("POST", "/api/platform/attachment-templates", {
      attachmentCode: attachment.attachmentCode,
      attachmentName: attachment.attachmentName,
      description: attachment.description || "",
      allowedExtensions: normalizeExtensions(attachment.allowedExtensions),
      maxSizeBytes: attachment.maxSizeBytes,
    }, user);
  }

  async resolveAttachmentTemplates(requirement: BusinessRequirement, user: MockUser): Promise<Map<string, any>> {
    const result = new Map<string, any>();
    for (const attachment of requirement.attachments) {
      const candidates = await this.findAttachmentTemplates(attachment.attachmentCode, user);
      const match = selectAttachmentTemplate(candidates, attachment);
      result.set(
        attachment.attachmentCode,
        match || await this.createAttachmentTemplate(attachment, user),
      );
    }
    return result;
  }

  async saveGraph(
    definitionId: string,
    requirement: BusinessRequirement,
    templates: Map<string, any>,
    user: MockUser,
    operationId: string,
  ): Promise<any> {
    return this.request("PUT", `/api/platform/definitions/${encodeURIComponent(definitionId)}/graph`, {
      operationId,
      operatorUserId: user.userId,
      nodes: requirement.nodes.map((node) => ({
        nodeCode: node.nodeCode,
        nodeName: node.nodeName,
        nodeType: node.nodeType,
        pairedGatewayCode: node.pairedGatewayCode,
        approverRuleType: node.approverRule?.type,
        approverRuleConfig: node.approverRule ? stableJson(node.approverRule.config) : null,
        multiInstanceMode: node.nodeType === "USER_TASK" ? node.multiInstanceMode || "SINGLE" : "SINGLE",
        listenerConfig: node.nodeType === "USER_TASK" && node.listenerConfig
          ? stableJson(node.listenerConfig) : null,
        timeoutConfig: node.nodeType === "USER_TASK" && node.timeoutConfig
          ? stableJson(node.timeoutConfig) : null,
        reminderConfig: node.nodeType === "USER_TASK" && node.reminderConfig
          ? stableJson(node.reminderConfig) : null,
        positionX: node.positionX,
        positionY: node.positionY,
        sortOrder: node.sortOrder,
      })),
      edges: requirement.edges.map((edge) => ({
        edgeCode: edge.edgeCode,
        sourceNodeCode: edge.sourceNodeCode,
        targetNodeCode: edge.targetNodeCode,
        conditionExpression: edge.conditionExpression,
        defaultEdge: edge.defaultEdge,
        sortOrder: edge.sortOrder,
      })),
      formFields: requirement.formFields.map((field) => ({
        fieldCode: field.fieldCode,
        fieldName: field.fieldName,
        fieldType: field.fieldType,
        controlType: field.controlType,
        required: field.required,
        validationRule: stableJson({ ...field.validation, ...(field.options ? { options: field.options } : {}) }),
        defaultValue: field.defaultValue,
        sortOrder: field.sortOrder,
      })),
      attachmentConfigs: requirement.attachments.map((attachment) => ({
        definitionId,
        attachmentTemplateId: templateId(templates.get(attachment.attachmentCode)),
        attachmentCode: attachment.attachmentCode,
        required: attachment.required,
        minCount: attachment.minCount,
        maxCount: attachment.maxCount,
        applicableNodeCodes: attachment.applicableNodeCodes,
        sortOrder: attachment.sortOrder,
      })),
    }, user);
  }

  async validate(definitionId: string, user: MockUser): Promise<ValidationResult> {
    return this.request(
      "GET",
      `/api/platform/definitions/${encodeURIComponent(definitionId)}/publish-validation`,
      undefined,
      user,
    );
  }

  async getDefinition(definitionId: string, user: MockUser): Promise<any> {
    return this.request("GET", `/api/platform/definitions/${encodeURIComponent(definitionId)}`, undefined, user);
  }

  async publish(definitionId: string, user: MockUser, operationId: string): Promise<any> {
    return this.definitionOperation("/api/platform/definitions/publish", definitionId, user, operationId);
  }

  async activate(definitionId: string, user: MockUser, operationId: string): Promise<any> {
    return this.definitionOperation("/api/platform/definitions/activate", definitionId, user, operationId);
  }

  toPreview(snapshot: any, validation: ValidationResult): ProcessPreview {
    return {
      platformDefinitionId: snapshot.id || snapshot.definitionId,
      processCode: snapshot.processCode,
      processName: snapshot.processName,
      definitionVersion: snapshot.version,
      definitionStatus: snapshot.definitionStatus,
      activationStatus: snapshot.activationStatus,
      nodes: snapshot.nodes || [],
      edges: snapshot.edges || [],
      formFields: snapshot.formFields || [],
      attachmentTemplates: snapshot.attachmentTemplates || [],
      validation,
    };
  }

  private async definitionOperation(path: string, definitionId: string, user: MockUser, operationId: string): Promise<any> {
    return this.request("POST", path, {
      operationId,
      definitionId,
      operatorUserId: user.userId,
    }, user);
  }

  private async request(method: string, path: string, body: unknown, user: MockUser): Promise<any> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.config.platformTimeoutMs);
    try {
      const response = await fetch(`${this.config.platformBaseUrl}${path}`, {
        method,
        signal: controller.signal,
        headers: {
          "Content-Type": "application/json",
          "X-Flow-User-Id": user.userId,
          "X-Flow-User-Name": user.userName,
          ...(user.departmentId ? { "X-Flow-Dept-Id": user.departmentId } : {}),
          ...(user.departmentName ? { "X-Flow-Dept-Name": user.departmentName } : {}),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await response.text();
      const payload = text ? safeJson(text) : undefined;
      if (!response.ok) {
        throw new AgentError(
          response.status >= 500 ? HttpStatus.BAD_GATEWAY : response.status,
          "FLOW_PLATFORM_ERROR",
          payload?.message || `flow platform returned ${response.status}`,
          undefined,
          { platformStatus: response.status, platformBody: payload },
        );
      }
      return payload;
    } catch (error) {
      if (error instanceof AgentError) throw error;
      const message = error instanceof Error && error.name === "AbortError"
        ? "flow platform request timed out"
        : error instanceof Error ? error.message : String(error);
      throw new AgentError(HttpStatus.BAD_GATEWAY, "FLOW_PLATFORM_UNAVAILABLE", message);
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function normalizeExtensions(values: string[]): string[] {
  return [...new Set(values.map((value) => value.trim().replace(/^\./, "").toLowerCase()).filter(Boolean))].sort();
}

export function templateMatches(candidate: any, requirement: AttachmentRequirement): boolean {
  return candidate?.attachmentName?.trim() === requirement.attachmentName.trim()
    && Number(candidate?.maxSizeBytes) === requirement.maxSizeBytes
    && JSON.stringify(normalizeExtensions(candidate?.allowedExtensions || []))
      === JSON.stringify(normalizeExtensions(requirement.allowedExtensions));
}

export function selectAttachmentTemplate(candidates: any[], requirement: AttachmentRequirement): any | undefined {
  return candidates
    .filter((candidate) => templateMatches(candidate, requirement))
    .sort((left, right) => templateVersion(right) - templateVersion(left))[0];
}

function templateId(template: any): string | undefined {
  return template?.attachmentTemplateId || template?.id;
}

function templateVersion(template: any): number {
  return Number(template?.templateVersion ?? template?.version ?? 0);
}

function stableJson(value: unknown): string {
  return JSON.stringify(sortValue(value));
}

function sortValue(value: any): any {
  if (Array.isArray(value)) return value.map(sortValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortValue(value[key])]));
  }
  return value;
}

function safeJson(text: string): any {
  try { return JSON.parse(text); } catch { return { message: text }; }
}
