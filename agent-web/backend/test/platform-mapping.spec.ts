import { describe, expect, it } from "vitest";
import { ENTRY_APPLICATION_REQUIREMENT } from "@flowmind/agent-contracts";
import {
  normalizeExtensions,
  mapProcessNode,
  selectAttachmentTemplate,
  templateMatches,
} from "../src/platform/platform-client.service.js";

describe("platform attachment mapping", () => {
  it("maps a NOTICE recipient and plain-text message configuration", () => {
    expect(mapProcessNode({
      nodeCode: "0199-notify-starter",
      nodeName: "知会经办",
      nodeType: "NOTICE",
      approverRule: { type: "STARTER", config: {} },
      multiInstanceMode: "SINGLE",
      noticeConfig: { title: "流程知会", content: "已办理完成，请知悉。" },
      positionX: 800,
      positionY: 300,
      sortOrder: 9,
    })).toEqual(expect.objectContaining({
      nodeType: "NOTICE",
      approverRuleType: "STARTER",
      approverRuleConfig: "{}",
      multiInstanceMode: "SINGLE",
      noticeConfig: "{\"content\":\"已办理完成，请知悉。\",\"title\":\"流程知会\"}",
    }));
  });

  it("normalizes extension sets deterministically", () => {
    expect(normalizeExtensions([".PNG", "pdf", " png ", "JPG"])).toEqual(["jpg", "pdf", "png"]);
  });

  it("reuses only an equivalent enabled template payload", () => {
    const requirement = ENTRY_APPLICATION_REQUIREMENT.attachments[0];
    expect(templateMatches({
      attachmentName: "银行回单",
      maxSizeBytes: 10485760,
      allowedExtensions: ["PNG", ".jpg", "pdf"],
    }, requirement)).toBe(true);
    expect(templateMatches({
      attachmentName: "银行回单",
      maxSizeBytes: 100,
      allowedExtensions: ["png", "jpg", "pdf"],
    }, requirement)).toBe(false);
  });

  it("selects the highest matching template version", () => {
    const attachment = ENTRY_APPLICATION_REQUIREMENT.attachments[0];
    const matching = {
      attachmentName: attachment.attachmentName,
      maxSizeBytes: attachment.maxSizeBytes,
      allowedExtensions: attachment.allowedExtensions,
    };
    expect(selectAttachmentTemplate([
      { ...matching, attachmentTemplateId: "tpl-v1", templateVersion: 1 },
      { ...matching, attachmentTemplateId: "tpl-v3", templateVersion: 3 },
      { ...matching, attachmentTemplateId: "tpl-v2", templateVersion: 2 },
    ], attachment)?.attachmentTemplateId).toBe("tpl-v3");
  });
});
