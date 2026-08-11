import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import AttachmentPanel from "./AttachmentPanel.vue";

describe("AttachmentPanel", () => {
  it("groups backend attachment DTOs and emits attachmentCode on upload", async () => {
    const wrapper = mount(AttachmentPanel, {
      props: {
        canUpload: true,
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
    const textInputs = wrapper.findAll('input[type="text"]');
    await textInputs[0].setValue("proofField");
    await textInputs[1].setValue("proofAttachment");
    await wrapper.find("form").trigger("submit");

    expect(wrapper.emitted("upload")?.[0]?.[0]).toEqual({
      file,
      fieldCode: "proofField",
      attachmentCode: "proofAttachment",
    });
  });
});
