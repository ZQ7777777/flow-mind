import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import AttachmentPanel from "./AttachmentPanel.vue";

describe("AttachmentPanel", () => {
  it("groups backend attachment DTOs and emits the selected template on upload", async () => {
    const wrapper = mount(AttachmentPanel, {
      props: {
        canUpload: true,
        uploadableAttachments: [
          {
            attachmentCode: "managerNote",
            attachmentName: "审批补充材料",
            ownerType: "TASK",
            allowedExtensions: ["txt"],
          },
        ],
        attachments: [
          { attachmentId: "a1", ownerType: "INSTANCE", fileName: "申请.pdf", sizeBytes: 1024 },
          { attachmentId: "a2", ownerType: "TASK", fileName: "意见.txt", sizeBytes: 20 },
        ],
      },
    });

    expect(wrapper.text()).toContain("申请.pdf");
    expect(wrapper.text()).toContain("意见.txt");

    const file = new File(["content"], "proof.txt", { type: "text/plain" });
    const fileInput = wrapper.find('input[type="file"]');
    Object.defineProperty(fileInput.element, "files", { value: [file] });
    await fileInput.trigger("change");
    await wrapper.find("form").trigger("submit");

    expect(wrapper.emitted("upload")?.[0]?.[0]).toEqual({
      file,
      ownerType: "TASK",
      template: {
        attachmentCode: "managerNote",
        attachmentName: "审批补充材料",
        ownerType: "TASK",
        allowedExtensions: ["txt"],
      },
    });
  });

  it("offers atomic replacement only for existing instance attachments", async () => {
    const wrapper = mount(AttachmentPanel, {
      props: {
        canReplace: true,
        attachments: [
          { attachmentId: "a1", ownerType: "INSTANCE", fileName: "old.pdf" },
          { attachmentId: "a2", ownerType: "TASK", fileName: "task.txt" },
        ],
      },
    });
    const replacement = new File(["new"], "new.pdf", { type: "application/pdf" });
    const input = wrapper.get('input[aria-label="替换 old.pdf"]');
    Object.defineProperty(input.element, "files", { value: [replacement] });
    await input.trigger("change");

    expect(wrapper.emitted("replace")?.[0]?.[0]).toEqual({
      attachment: expect.objectContaining({ attachmentId: "a1" }),
      file: replacement,
    });
    expect(wrapper.find('input[aria-label="替换 task.txt"]').exists()).toBe(false);
    expect(wrapper.find("form").exists()).toBe(false);
  });

  it("disables upload when the current node has no uploadable templates", () => {
    const wrapper = mount(AttachmentPanel, {
      props: { canUpload: true, uploadableAttachments: [], attachments: [] },
    });

    expect(wrapper.text()).toContain("当前节点未配置可上传材料");
    expect(wrapper.find('input[type="file"]').attributes("disabled")).toBeDefined();
    expect(wrapper.find("button").attributes("disabled")).toBeDefined();
  });
});
