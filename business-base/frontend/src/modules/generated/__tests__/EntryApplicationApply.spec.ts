import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import type { ComponentPublicInstance } from "vue";
import type { VueWrapper } from "@vue/test-utils";
import ElementPlus, { ElButton, ElInput, ElInputNumber, ElUpload } from "element-plus";
import EntryApplicationApply from "../entry-application/EntryApplicationApply.vue";

/**
 * jsdom 环境不保证 Blob.text() 存在，统一使用 FileReader 读取表单中的 Blob/File 内容。
 */
function readBlobText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

function okResponse(body: unknown): Response {
  return { ok: true, status: 200, json: async () => body } as unknown as Response;
}

function errorResponse(status: number, code: string, message: string): Response {
  return { ok: false, status, json: async () => ({ code, message }) } as unknown as Response;
}

function fakeUploadFile(
  name: string,
  content = "abc"
): { uid: number; name: string; raw: File; status: string; size: number } {
  return {
    uid: Math.floor(Math.random() * 100000) + 1,
    name,
    raw: new File([content], name),
    status: "ready",
    size: content.length,
  };
}

/**
 * 读取组件公开 prop（如 disabled / loading），通过 props() 返回对象索引取值。
 * props() 的类型在此版本中为 {}，先转换为带字符串索引的记录再读取，避免隐式 any。
 */
function componentProp<T extends ComponentPublicInstance>(wrapper: VueWrapper<T>, propName: string): unknown {
  return (wrapper.props() as Record<string, unknown>)[propName];
}

/**
 * 通过公开组件交互驱动表单：el-input / el-input-number 使用 setValue 触发
 * v-model 更新事件；el-upload 通过公开的 update:file-list 事件（对应
 * v-model:file-list 绑定）模拟选择文件，附件进入 form.bankReceipt。
 */
async function fillValidForm<T extends ComponentPublicInstance>(wrapper: VueWrapper<T>): Promise<void> {
  await wrapper.findComponent(ElInput).setValue("张三");
  await wrapper.findComponent(ElInputNumber).setValue(1000);
  const upload = wrapper.findComponent(ElUpload);
  const file = fakeUploadFile("receipt.pdf");
  await upload.vm.$emit("update:file-list", [file]);
}

function findSubmitButton<T extends ComponentPublicInstance>(
  wrapper: VueWrapper<T>
): VueWrapper<ComponentPublicInstance> {
  const button = wrapper.findAllComponents(ElButton).find((candidate) => /提交/.test(candidate.text()));
  if (!button) {
    throw new Error("未找到提交按钮");
  }
  return button;
}

describe("EntryApplicationApply", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows validation errors and does not submit an empty form", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const wrapper = mount(EntryApplicationApply, { global: { plugins: [ElementPlus] } });

    await findSubmitButton(wrapper).trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("请输入申请人姓名");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("validates amount and required attachment before submitting", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const wrapper = mount(EntryApplicationApply, { global: { plugins: [ElementPlus] } });

    await wrapper.findComponent(ElInput).setValue("张三");
    await findSubmitButton(wrapper).trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("入金金额必须大于0");

    await wrapper.findComponent(ElInputNumber).setValue(1000);
    await findSubmitButton(wrapper).trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("请至少上传1个付款凭证");

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects attachments with unsupported extensions", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const wrapper = mount(EntryApplicationApply, { global: { plugins: [ElementPlus] } });

    await wrapper.findComponent(ElInput).setValue("张三");
    await wrapper.findComponent(ElInputNumber).setValue(1000);
    const upload = wrapper.findComponent(ElUpload);
    const badFile = fakeUploadFile("receipt.exe");
    await upload.vm.$emit("update:file-list", [badFile]);

    await findSubmitButton(wrapper).trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("付款凭证仅支持 pdf、jpg、png 格式");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("submits the multipart form and shows the success state", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      okResponse({
        instanceId: "inst-1",
        instanceStatus: "RUNNING",
        tasks: [{ taskId: "task-apply-1", nodeCode: "apply", taskName: "提交申请" }],
      })
    );
    vi.stubGlobal("fetch", fetchMock);
    const wrapper = mount(EntryApplicationApply, { global: { plugins: [ElementPlus] } });
    await fillValidForm(wrapper);

    await findSubmitButton(wrapper).trigger("click");
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/generated/entry-application/submit");
    expect(init.method).toBe("POST");
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBeTruthy();

    const form = init.body as FormData;
    const payloadBlob = form.get("payload") as Blob;
    expect(payloadBlob.type).toBe("application/json");
    expect(JSON.parse(await readBlobText(payloadBlob))).toEqual({ applicantName: "张三", amount: 1000 });
    const bankReceipts = form.getAll("bankReceipt") as File[];
    expect(bankReceipts).toHaveLength(1);
    expect(bankReceipts[0].name).toBe("receipt.pdf");

    expect(wrapper.text()).toContain("提交成功");
    expect(wrapper.text()).toContain("inst-1");
  });

  it("suppresses duplicate submission and exposes disabled/loading while in flight", async () => {
    let resolveRequest: ((value: unknown) => void) | undefined;
    const fetchMock = vi.fn().mockReturnValue(
      new Promise<unknown>((resolve) => {
        resolveRequest = resolve;
      })
    );
    vi.stubGlobal("fetch", fetchMock);
    const wrapper = mount(EntryApplicationApply, { global: { plugins: [ElementPlus] } });
    await fillValidForm(wrapper);

    const submitButton = findSubmitButton(wrapper);
    await submitButton.trigger("click");
    // 等待校验完成且 fetch mock 已被调用后再断言进行中的禁用/加载状态。
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(componentProp(submitButton, "disabled")).toBe(true);
    expect(componentProp(submitButton, "loading")).toBe(true);

    await submitButton.trigger("click");
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveRequest!(okResponse({ instanceId: "inst-1", instanceStatus: "RUNNING", tasks: [] }));
    await flushPromises();
    expect(componentProp(submitButton, "disabled")).toBe(false);
    expect(componentProp(submitButton, "loading")).toBe(false);
    expect(wrapper.text()).toContain("提交成功");
  });

  it("shows the backend error text on failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(errorResponse(400, "VALIDATION_ERROR", "付款凭证至少需要上传1个文件"))
    );
    const wrapper = mount(EntryApplicationApply, { global: { plugins: [ElementPlus] } });
    await fillValidForm(wrapper);

    await findSubmitButton(wrapper).trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("付款凭证至少需要上传1个文件");
    expect(componentProp(findSubmitButton(wrapper), "disabled")).toBe(false);
  });

  it("keeps the Idempotency-Key stable across sequential submissions", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      okResponse({ instanceId: "inst-1", instanceStatus: "RUNNING", tasks: [] })
    );
    vi.stubGlobal("fetch", fetchMock);
    const wrapper = mount(EntryApplicationApply, { global: { plugins: [ElementPlus] } });
    await fillValidForm(wrapper);

    const submitButton = findSubmitButton(wrapper);
    await submitButton.trigger("click");
    await flushPromises();
    await submitButton.trigger("click");
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const firstKey = (fetchMock.mock.calls[0][1].headers as Record<string, string>)["Idempotency-Key"];
    const secondKey = (fetchMock.mock.calls[1][1].headers as Record<string, string>)["Idempotency-Key"];
    expect(firstKey).toBeTruthy();
    expect(secondKey).toBe(firstKey);
  });
});
