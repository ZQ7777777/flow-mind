import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BusinessForm from "../BusinessForm.vue";
import * as api from "../../../../api/generated/warehouse-treasury-pledge-application/warehouse-treasury-pledge-application";

vi.mock("../../../../api/generated/warehouse-treasury-pledge-application/warehouse-treasury-pledge-application", () => ({
  searchFuturesAccounts: vi.fn(), getAccountFunds: vi.fn(), getExchanges: vi.fn(),
  getTradingCodes: vi.fn(), searchFuturesProducts: vi.fn(),
}));

const fieldCodes = [
  "futuresAccount", "customerName", "businessType", "exchangeCode", "tradingCode", "productCodes",
  "quantity", "contractMultiplier", "pledgeUnitQuantity", "previousSettlementPrice", "amount", "largeAmount",
];
const fields = fieldCodes.map((fieldCode) => ({
  fieldCode,
  fieldName: fieldCode,
  required: !["largeAmount"].includes(fieldCode),
  visible: true,
  editable: !["customerName", "tradingCode", "amount", "largeAmount"].includes(fieldCode),
}));

describe("warehouse treasury pledge application BusinessForm", () => {
  beforeEach(() => {
    vi.mocked(api.searchFuturesAccounts).mockResolvedValue([{ accountNo: "70000299", customerName: "远航贸易", accountStatus: "NORMAL" }]);
    vi.mocked(api.getExchanges).mockResolvedValue([{ exchangeCode: "CZCE", exchangeName: "郑商所", sourcePrefix: "Z" }]);
    vi.mocked(api.getAccountFunds).mockResolvedValue({ accountNo: "70000299", currency: "CNY", currentEquity: 50000000, availableFunds: 30000000, pledgeAmount: 2000000, actualCash: 20000000, snapshotAt: "2026-08-19", exchangeFunds: [{ exchangeCode: "CZCE", pledgeAmount: 2000000, positionMargin: 30000000 }] });
    vi.mocked(api.getTradingCodes).mockResolvedValue([{ accountNo: "70000299", exchangeCode: "CZCE", tradingCode: "Z-299", tradingStatus: "NORMAL" }]);
    vi.mocked(api.searchFuturesProducts).mockResolvedValue([{ exchangeCode: "CZCE", exchangeName: "郑商所", productCode: "SR", productName: "白糖", productType: "FUTURES", contractMultiplier: 10, pledgeUnitQuantity: 1, previousSettlementPrice: 5000, dataSource: "HTML_EXTRACTED" }]);
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
    const wrapper = await mounted({ quantity: 3 });
    const selects = wrapper.findAll("select");
    await selects[0].setValue("70000299");
    await selects[2].setValue("CZCE");
    await flushPromises();
    await wrapper.find("select[multiple]").setValue("SR");
    await flushPromises();
    await selects[1].setValue("仓单质押");
    await flushPromises();

    expect(api.getAccountFunds).toHaveBeenCalledWith("70000299");
    expect(api.searchFuturesProducts).toHaveBeenCalledWith("CZCE");
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue).toEqual(expect.objectContaining({
      customerName: "远航贸易", tradingCode: "Z-299", contractMultiplier: 10, pledgeUnitQuantity: 1,
      previousSettlementPrice: 5000, amount: 120000, largeAmount: false,
    }));
    expect(typeof (wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.amount).toBe("number");
    expect(wrapper.text()).toContain("通过");

    await selects[2].setValue("");
    await flushPromises();
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue).toEqual(expect.objectContaining({
      tradingCode: "", productCodes: [], contractMultiplier: undefined,
      pledgeUnitQuantity: undefined, previousSettlementPrice: undefined, amount: 0, largeAmount: false,
    }));

    await selects[2].setValue("CZCE");
    await wrapper.find("select[multiple]").setValue("SR");
    await flushPromises();

    await selects[1].setValue("仓单解质押");
    await flushPromises();
    expect((wrapper.props() as { modelValue: Record<string, unknown> }).modelValue.amount).toBe(-120000);
  });

  it("calculates largeAmount flag when amount reaches ten million", async () => {
    const wrapper = await mounted({ quantity: 300 });
    const selects = wrapper.findAll("select");
    await selects[0].setValue("70000299");
    await selects[2].setValue("CZCE");
    await flushPromises();
    await wrapper.find("select[multiple]").setValue("SR");
    await flushPromises();
    await selects[1].setValue("仓单质押");
    await flushPromises();

    const modelValue = (wrapper.props() as { modelValue: Record<string, unknown> }).modelValue;
    expect(modelValue.amount).toBe(12000000);
    expect(modelValue.largeAmount).toBe(true);
    expect(wrapper.find('input[type="checkbox"]').attributes("disabled")).toBeDefined();
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
    expect(wrapper.text()).not.toContain("businessType");
    const numberInputs = wrapper.findAll('input[type="number"]');
    expect(numberInputs[0].attributes("disabled")).toBeDefined();
    expect(numberInputs[1].attributes("disabled")).toBeUndefined();
    expect(numberInputs[2].attributes("disabled")).toBeUndefined();
    expect(numberInputs[3].attributes("disabled")).toBeUndefined();
    expect(await (wrapper.vm as unknown as { validate(): Promise<boolean> }).validate()).toBe(false);
    expect(wrapper.text()).toContain("请填写");
  });

  it("shows a stable failure state when a business check refresh fails", async () => {
    const wrapper = await mounted({ futuresAccount: "70000299" });
    vi.mocked(api.getAccountFunds).mockRejectedValueOnce(new Error("资金服务暂不可用"));

    await wrapper.find("button").trigger("click");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("资金服务暂不可用");
  });
});
