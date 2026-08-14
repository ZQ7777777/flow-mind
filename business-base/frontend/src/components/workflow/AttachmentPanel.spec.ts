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
        currentUserId: "user-1",
        attachments: [
          { attachmentId: "a1", ownerType: "INSTANCE", fileName: "old.pdf", uploadedBy: "user-1" },
          { attachmentId: "a2", ownerType: "TASK", fileName: "task.txt", uploadedBy: "user-1" },
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

  it("offers modification only for attachments uploaded by the current user", async () => {
    const currentInstanceAttachment = {
      attachmentId: "instance-current",
      taskId: "task-current",
      ownerType: "INSTANCE",
      fileName: "current.pdf",
      uploadedBy: "user-1",
    };
    const wrapper = mount(AttachmentPanel, {
      props: {
        canDelete: true,
        canReplace: true,
        currentUserId: "user-1",
        attachments: [
          currentInstanceAttachment,
          {
            attachmentId: "task-current-attachment",
            taskId: "task-current",
            ownerType: "TASK",
            fileName: "current-task.txt",
            uploadedBy: "user-1",
          },
          {
            attachmentId: "history-attachment",
            taskId: "task-history",
            ownerType: "INSTANCE",
            fileName: "history.pdf",
            uploadedBy: "other-user",
          },
        ],
      },
    });

    expect(wrapper.find('button[aria-label="删除 current.pdf"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="删除 current-task.txt"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="删除 history.pdf"]').exists()).toBe(false);
    expect(wrapper.find('input[aria-label="替换 current.pdf"]').exists()).toBe(true);
    expect(wrapper.find('input[aria-label="替换 history.pdf"]').exists()).toBe(false);
    expect(wrapper.find('input[aria-label="替换 current-task.txt"]').exists()).toBe(false);
    expect(wrapper.findAll(".attachment-list button").filter((button) => button.text() === "下载")).toHaveLength(3);

    await wrapper.get('button[aria-label="删除 current.pdf"]').trigger("click");
    expect(wrapper.emitted("delete")?.[0]?.[0]).toEqual(currentInstanceAttachment);
  });

  it("disables delete actions while an attachment is being deleted", () => {
    const wrapper = mount(AttachmentPanel, {
      props: {
        canDelete: true,
        currentUserId: "user-1",
        deletingAttachmentId: "attachment-1",
        attachments: [
          { attachmentId: "attachment-1", taskId: "task-current", fileName: "one.pdf", uploadedBy: "user-1" },
          { attachmentId: "attachment-2", taskId: "task-current", fileName: "two.pdf", uploadedBy: "user-1" },
        ],
      },
    });

    const buttons = wrapper.findAll(".delete-button");
    expect(buttons).toHaveLength(2);
    expect(buttons[0].text()).toBe("删除中…");
    expect(buttons.every((button) => button.attributes("disabled") !== undefined)).toBe(true);
  });
});
