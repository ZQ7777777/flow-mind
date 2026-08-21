import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BusinessForm from "../BusinessForm.vue";
import * as api from "../../../../api/generated/sample/sample";

vi.mock("../../../../api/generated/sample/sample", () => ({
  searchFuturesAccounts: vi.fn(), getAccountFunds: vi.fn(), getExchanges: vi.fn(),
  getTradingCodes: vi.fn(), searchFuturesProducts: vi.fn(),
}));

const fields = [
  "futuresAccount", "customerName", "businessType", "exchangeCode", "tradingCode", "productCodes",
  "quantity", "contractMultiplier", "pledgeUnitQuantity", "previousSettlementPrice", "amount",
].map((fieldCode) => ({ fieldCode, fieldName: fieldCode, required: !["customerName", "tradingCode"].includes(fieldCode), visible: true, editable: true }));

function buttonByText(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll("button").find((item) => item.text() === text);
  if (!button) throw new Error(`button not found: ${text}`);
  return button;
}

describe("warehouse pledge golden BusinessForm", () => {
  beforeEach(() => {
    vi.mocked(api.searchFuturesAccounts).mockResolvedValue([{ accountNo: "80000188", customerName: "启明实业", accountStatus: "NORMAL" }]);
    vi.mocked(api.getExchanges).mockResolvedValue([{ exchangeCode: "DCE", exchangeName: "大商所", sourcePrefix: "D" }]);
    vi.mocked(api.getAccountFunds).mockResolvedValue({ accountNo: "80000188", customerName: "启明实业", currency: "CNY", currentEquity: 100000000, availableFunds: 50000000, pledgeAmount: 1000000, actualCash: 40000000, snapshotAt: "2026-08-19", exchangeFunds: [{ exchangeCode: "DCE", exchangeName: "大商所", pledgeAmount: 1000000, positionMargin: 20000000 }, { exchangeCode: "CZCE", exchangeName: "郑商所", pledgeAmount: 800000, positionMargin: 15000000 }] });
    vi.mocked(api.getTradingCodes).mockResolvedValue([{ accountNo: "80000188", exchangeCode: "DCE", tradingCode: "D-188", tradingStatus: "NORMAL" }]);
    vi.mocked(api.searchFuturesProducts).mockResolvedValue([{ exchangeCode: "DCE", exchangeName: "大商所", productCode: "m", productName: "豆粕", productType: "FUTURES", contractMultiplier: 10, pledgeUnitQuantity: 1, previousSettlementPrice: 3000, dataSource: "HTML_EXTRACTED" }]);
  });

  async function mounted(modelValue: Record<string, unknown> = {}): Promise<VueWrapper> {
    let wrapper: VueWrapper;
    wrapper = mount(BusinessForm, {
      props: {
        modelValue, fields, fieldPermissions: [],
        "onUpdate:modelValue": async (value: Record<string, unknown>) => wrapper.setProps({ modelValue: value }),
      },
    });
    await flushPromises();
    return wrapper;
  }

  it("loads cascaded reference data, emits numbers, calculates signed amount, and evaluates checks", async () => {
    const wrapper = await mounted({ quantity: 2 });
    const selects = wrapper.findAll("select");
    await selects[0].setValue("80000188");
    await selects[2].setValue("DCE");
    await flushPromises();
    await wrapper.find("select[multiple]").setValue("m");
    await flushPromises();
    await selects[1].setValue("仓单质押");
    await flushPromises();

    expect(api.getAccountFunds).toHaveBeenCalledWith("80000188");
    expect(api.searchFuturesProducts).toHaveBeenCalledWith("DCE");
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue).toEqual(expect.objectContaining({
      tradingCode: "D-188", contractMultiplier: 10, pledgeUnitQuantity: 1,
      previousSettlementPrice: 3000, amount: 48000,
    }));
    expect(typeof (wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.amount).toBe("number");
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.largeAmount).toBe(false);
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.businessCheckSnapshot).toContain("满足质押要求");
    expect(wrapper.text()).toContain("通过");

    await wrapper.findAll('input[type="number"]')[1].setValue("3000");
    await flushPromises();
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.amount).toBe(14_400_000);
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.largeAmount).toBe(true);
    await wrapper.findAll('input[type="number"]')[1].setValue("10");

    await selects[2].setValue("");
    await flushPromises();
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue).toEqual(expect.objectContaining({
      tradingCode: "", productCodes: [], contractMultiplier: undefined,
      pledgeUnitQuantity: undefined, previousSettlementPrice: undefined, amount: 0, largeAmount: false,
    }));

    await selects[2].setValue("DCE");
    await wrapper.find("select[multiple]").setValue("m");
    await flushPromises();

    await selects[1].setValue("仓单解质押");
    await flushPromises();
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.amount).toBe(-48000);
  });

  it("shows account funds in a dialog instead of a standalone customer-name field", async () => {
    const wrapper = await mounted();
    const detailButton = buttonByText(wrapper, "查看资金详情");

    expect(detailButton.attributes("disabled")).toBeDefined();
    expect(wrapper.findAll("label").some((label) => label.text().startsWith("客户名称"))).toBe(false);
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);

    await wrapper.findAll("select")[0].setValue("80000188");
    await flushPromises();
    expect(detailButton.attributes("disabled")).toBeUndefined();

    await detailButton.trigger("click");
    await flushPromises();
    const dialog = wrapper.get('[role="dialog"]');
    const money = (value: number) => new Intl.NumberFormat("zh-CN", {
      style: "currency", currency: "CNY", minimumFractionDigits: 2, maximumFractionDigits: 2,
    }).format(value);
    expect(dialog.attributes("aria-modal")).toBe("true");
    expect(dialog.text()).toContain("客户资金详情");
    expect(dialog.text()).toContain("80000188");
    expect(dialog.text()).toContain("启明实业");
    expect(dialog.text()).toContain("人民币");
    expect(dialog.text()).toContain("正常");
    expect(dialog.text()).toContain(money(100000000));
    expect(dialog.text()).toContain(money(50000000));
    expect(dialog.text()).toContain(money(1000000));
    expect(dialog.text()).toContain(money(40000000));
    expect(dialog.text()).toContain(money(20000000));
    expect(dialog.text()).toContain(money(800000));
    expect(dialog.text()).toContain(money(15000000));

    await wrapper.get('button[aria-label="关闭资金详情"]').trigger("click");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    await detailButton.trigger("click");
    await wrapper.get('[role="dialog"]').trigger("keydown", { key: "Escape" });
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    await detailButton.trigger("click");
    await wrapper.get(".dialog-backdrop").trigger("click");
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });

  it("discards stale account funds and closes details when the account is cleared", async () => {
    type Funds = Awaited<ReturnType<typeof api.getAccountFunds>>;
    let resolveFirst: (value: Funds) => void = () => undefined;
    let resolveSecond: (value: Funds) => void = () => undefined;
    vi.mocked(api.searchFuturesAccounts).mockResolvedValue([
      { accountNo: "80000188", customerName: "启明实业", accountStatus: "NORMAL" },
      { accountNo: "80000299", customerName: "远航贸易", accountStatus: "DORMANT" },
    ]);
    vi.mocked(api.getAccountFunds).mockImplementation((accountNo) => new Promise((resolve) => {
      if (accountNo === "80000188") resolveFirst = resolve;
      else resolveSecond = resolve;
    }));
    const wrapper = await mounted();
    const accountSelect = wrapper.findAll("select")[0];
    const detailButton = buttonByText(wrapper, "查看资金详情");

    await accountSelect.setValue("80000188");
    await accountSelect.setValue("80000299");
    resolveSecond({ accountNo: "80000299", customerName: "远航贸易", currency: "CNY", currentEquity: 2, availableFunds: 2, pledgeAmount: 2, actualCash: 2, snapshotAt: "2026-08-20", exchangeFunds: [] });
    await flushPromises();
    resolveFirst({ accountNo: "80000188", customerName: "启明实业", currency: "CNY", currentEquity: 1, availableFunds: 1, pledgeAmount: 1, actualCash: 1, snapshotAt: "2026-08-19", exchangeFunds: [] });
    await flushPromises();

    expect(detailButton.attributes("disabled")).toBeUndefined();
    await detailButton.trigger("click");
    expect(wrapper.get('[role="dialog"]').text()).toContain("远航贸易");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("启明实业");

    await accountSelect.setValue("");
    await flushPromises();
    expect(detailButton.attributes("disabled")).toBeDefined();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
  });

  it("honors runtime visibility/editability and exposes required validation", async () => {
    const wrapper = mount(BusinessForm, {
      props: {
        modelValue: {}, fields,
        fieldPermissions: [
          { fieldCode: "businessType", visible: false, editable: false, required: false },
          { fieldCode: "quantity", visible: true, editable: false, required: true },
        ],
      },
    });
    await flushPromises();
    expect(wrapper.text()).not.toContain("业务类型 *");
    expect(wrapper.find('input[type="number"]').attributes("disabled")).toBeDefined();
    expect(wrapper.findAll('input[type="number"]')[1].attributes("disabled")).toBeUndefined();
    expect(await (wrapper.vm as unknown as { validate(): Promise<boolean> }).validate()).toBe(false);
    expect(wrapper.text()).toContain("请填写");
  });

  it("shows a stable failure state when a business check refresh fails", async () => {
    const wrapper = await mounted({ futuresAccount: "80000188" });
    vi.mocked(api.getAccountFunds).mockRejectedValueOnce(new Error("资金服务暂不可用"));

    await buttonByText(wrapper, "刷新").trigger("click");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("资金服务暂不可用");
  });

  it("uses the saved check snapshot and disables refresh at delivery review", async () => {
    const snapshot = JSON.stringify([{ name: "满足质押要求", description: "快照结果", status: "pass", text: "通过" }]);
    const wrapper = mount(BusinessForm, {
      props: {
        modelValue: { businessCheckSnapshot: snapshot, futuresAccount: "80000188" },
        fields,
        fieldPermissions: [],
        currentNodeCode: "delivery_review",
        checkRefreshable: false,
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain("快照结果");
    expect(buttonByText(wrapper, "刷新").attributes("disabled")).toBeDefined();
  });
});
