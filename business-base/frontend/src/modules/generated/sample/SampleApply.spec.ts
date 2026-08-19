import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SampleApply from "./SampleApply.vue";
import * as api from "../../../api/generated/sample/sample";

vi.mock("../../../api/generated/sample", async () => {
  const actual = await vi.importActual<typeof import("../../../api/generated/sample/sample")>(
    "../../../api/generated/sample",
  );
  return {
    ...actual,
    getStartContext: vi.fn(),
    getExchanges: vi.fn(),
    searchFuturesAccounts: vi.fn(),
    getAccountFunds: vi.fn(),
    getTradingCodes: vi.fn(),
    searchFuturesProducts: vi.fn(),
    startSubmit: vi.fn(),
  };
});

const startContext = {
  definitionId: "def-1",
  processCode: "sample",
  processName: "仓单、国债（解）质押申请",
  definitionVersion: 1,
  currentNodeCode: "0199-submit",
  startable: true,
  disabledReason: null,
  formFields: [
    { fieldCode: "futuresAccount", fieldName: "期货账号", fieldType: "string", controlType: "select", options: [], visible: true, editable: true, required: true, sortOrder: 1 },
    { fieldCode: "businessType", fieldName: "业务类型", fieldType: "select", controlType: "select", options: [], visible: true, editable: true, required: true, sortOrder: 2 },
    { fieldCode: "exchange", fieldName: "交易所", fieldType: "select", controlType: "select", options: [], visible: true, editable: true, required: true, sortOrder: 3 },
    { fieldCode: "amount", fieldName: "金额", fieldType: "number", controlType: "input", options: [], visible: true, editable: false, required: true, sortOrder: 10 },
  ],
  attachments: [
    {
      attachmentCode: "银行回单",
      attachmentName: "银行回单",
      description: "上传银行回单",
      required: true,
      minCount: 1,
      maxCount: 5,
      maxSizeBytes: 10 * 1024 * 1024,
      allowedExtensions: ["jpg", "png", "pdf"],
      sortOrder: 1,
    },
  ],
};

describe("SampleApply", () => {
  beforeEach(() => {
    vi.mocked(api.getStartContext).mockResolvedValue(startContext);
    vi.mocked(api.getExchanges).mockResolvedValue([
      { exchangeCode: "CFFEX", exchangeName: "中金所", sourcePrefix: "J" },
    ]);
    vi.mocked(api.searchFuturesAccounts).mockResolvedValue([
      { accountNo: "80000188", customerName: "上海启明实业有限公司", accountStatus: "NORMAL" },
    ]);
    vi.mocked(api.getAccountFunds).mockResolvedValue({
      accountNo: "80000188",
      customerName: "上海启明实业有限公司",
      currency: "CNY",
      currentEquity: 32500000,
      availableFunds: 16800000,
      pledgeAmount: 7800000,
      actualCash: 22400000,
      snapshotAt: "2026-08-18T00:00:00+08:00",
      exchangeFunds: [],
    });
    vi.mocked(api.getTradingCodes).mockResolvedValue([
      {
        accountNo: "80000188",
        exchangeCode: "CFFEX",
        tradingCode: "CFFEX-80000188",
        tradingStatus: "NORMAL",
      },
    ]);
    vi.mocked(api.searchFuturesProducts).mockResolvedValue([
      {
        exchangeCode: "CFFEX",
        exchangeName: "中金所",
        productCode: "IF",
        productName: "沪深300股指",
        productType: "FUTURES",
        contractMultiplier: 300,
        pledgeUnitQuantity: 1,
        previousSettlementPrice: 3952.6,
        dataSource: "HTML_EXTRACTED",
      },
    ]);
    vi.mocked(api.startSubmit).mockResolvedValue({ instanceId: "pi-1", status: "RUNNING" });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("loads start context and renders the form", async () => {
    const wrapper = mount(SampleApply);
    await flushPromises();

    expect(wrapper.text()).toContain("仓单、国债（解）质押申请");
    expect(wrapper.text()).toContain("业务信息");
    expect(wrapper.text()).toContain("业务核查");
    expect(api.getStartContext).toHaveBeenCalledWith("sample");
  });

  it("loads account funds after selecting an account", async () => {
    const wrapper = mount(SampleApply);
    await flushPromises();

    const accountInput = wrapper.find('input[placeholder="输入期货账号或客户名称"]');
    await accountInput.trigger("focus");
    await wrapper.find(".dropdown-item").trigger("click");
    await flushPromises();

    expect(api.getAccountFunds).toHaveBeenCalledWith("80000188");
    expect(wrapper.text()).toContain("查看资金详情");
  });

  it("switches to attachment tab and validates required receipt", async () => {
    const wrapper = mount(SampleApply);
    await flushPromises();

    const attachmentTab = wrapper.findAll(".tabs button")[1];
    await attachmentTab.trigger("click");

    expect(wrapper.text()).toContain("附件清单");
    expect(wrapper.text()).toContain("银行回单");
    expect(wrapper.text()).toContain("1-5 个");
    expect(wrapper.text()).toContain("jpg/png/pdf");
  });

  it("rejects unsupported attachment extension before submission", async () => {
    const wrapper = mount(SampleApply);
    await flushPromises();
    await wrapper.findAll(".tabs button")[1].trigger("click");

    const input = wrapper.find('input[type="file"]');
    const badFile = new File(["bad"], "bad.docx", {
      type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    });

    Object.defineProperty(input.element, "files", {
      value: [badFile],
      configurable: true,
    });
    await input.trigger("change");

    expect(wrapper.text()).toContain("格式不支持");
  });
});
