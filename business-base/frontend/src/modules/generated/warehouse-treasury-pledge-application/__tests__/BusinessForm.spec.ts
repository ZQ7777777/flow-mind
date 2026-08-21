import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BusinessForm from "../BusinessForm.vue";
import * as api from "../../../../api/generated/warehouse-treasury-pledge-application/warehouse-treasury-pledge-application";

vi.mock("../../../../api/generated/warehouse-treasury-pledge-application/warehouse-treasury-pledge-application", () => ({
  searchFuturesAccounts: vi.fn(), getAccountFunds: vi.fn(), getExchanges: vi.fn(),
  getTradingCodes: vi.fn(), searchFuturesProducts: vi.fn(),
}));

const fields = [
  "futuresAccount", "customerName", "businessType", "exchangeCode", "tradingCode", "productCodes",
  "quantity", "contractMultiplier", "pledgeUnitQuantity", "previousSettlementPrice", "amount",
].map((fieldCode) => ({ fieldCode, fieldName: fieldCode, required: !["customerName", "tradingCode"].includes(fieldCode), visible: true, editable: true }));

describe("仓单、国债（解）质押申请 BusinessForm", () => {
  beforeEach(() => {
    vi.mocked(api.searchFuturesAccounts).mockResolvedValue([{ accountNo: "88000166", customerName: "中粮粮油", accountStatus: "NORMAL" }]);
    vi.mocked(api.getExchanges).mockResolvedValue([{ exchangeCode: "DCE", exchangeName: "大商所", sourcePrefix: "D" }]);
    vi.mocked(api.getAccountFunds).mockResolvedValue({ accountNo: "88000166", customerName: "中粮粮油", currency: "CNY", currentEquity: 100000000, availableFunds: 50000000, pledgeAmount: 1000000, actualCash: 40000000, snapshotAt: "2026-08-19", exchangeFunds: [{ exchangeCode: "DCE", exchangeName: "大商所", pledgeAmount: 1000000, positionMargin: 20000000 }] });
    vi.mocked(api.getTradingCodes).mockResolvedValue([{ accountNo: "88000166", exchangeCode: "DCE", tradingCode: "D-166", tradingStatus: "NORMAL" }]);
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
    await selects[0].setValue("88000166");
    await selects[2].setValue("DCE");
    await flushPromises();
    await wrapper.find("select[multiple]").setValue("m");
    await flushPromises();
    await selects[1].setValue("仓单质押");
    await flushPromises();

    expect(api.getAccountFunds).toHaveBeenCalledWith("88000166");
    expect(api.searchFuturesProducts).toHaveBeenCalledWith("DCE");
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue).toEqual(expect.objectContaining({
      tradingCode: "D-166", contractMultiplier: 10, pledgeUnitQuantity: 1,
      previousSettlementPrice: 3000, amount: 48000,
    }));
    expect(typeof (wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.amount).toBe("number");
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.largeAmount).toBe(false);
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
    const wrapper = await mounted({ futuresAccount: "88000166" });
    vi.mocked(api.getAccountFunds).mockRejectedValueOnce(new Error("资金服务暂不可用"));

    await wrapper.find("button").trigger("click");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("资金服务暂不可用");
  });

  it("disables check refresh at delivery review", async () => {
    const wrapper = mount(BusinessForm, {
      props: {
        modelValue: { futuresAccount: "88000166" },
        fields,
        fieldPermissions: [],
        currentNodeCode: "delivery_review",
        checkRefreshable: false,
      },
    });
    await flushPromises();

    expect(wrapper.get("button").attributes("disabled")).toBeDefined();
  });
});
