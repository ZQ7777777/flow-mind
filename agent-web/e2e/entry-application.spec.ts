import { expect, test } from "@playwright/test";
import { resolve } from "node:path";

test("入金申请从对话走到发布激活", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "新建 Agent 会话" })).toBeVisible();
  await page.getByRole("button", { name: "开始采集需求" }).click();
  await expect(page.getByText("COLLECTING", { exact: true })).toBeVisible();

  const composer = page.getByPlaceholder("描述业务流程，或回答 Agent 的问题…");
  await composer.fill("我要做一个入金申请流程");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText(/请补充参与角色/)).toBeVisible();

  await composer.fill("业务员提交，字段为申请人姓名、入金金额、入金账号，上传银行回单，部门经理审批后财务确认。");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText("REQUIREMENT_REVIEW", { exact: true })).toBeVisible();
  await expect(page.getByRole("textbox", { name: "业务名称" })).toHaveValue("入金申请");

  await page.getByRole("button", { name: "确认需求并创建流程" }).click();
  await expect(page.getByText("PROCESS_REVIEW", { exact: true })).toBeVisible({ timeout: 10000 });
  await page.getByRole("tab", { name: "流程预览" }).click();
  await expect(page.getByText("definition_entry_v1")).toBeVisible();
  await expect(page.getByText("部门经理审批", { exact: true })).toBeVisible();
  await expect(page.getByText("银行回单", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "确认流程并激活" }).click();
  await expect(page.getByText("PROCESS_ACTIVE", { exact: true })).toBeVisible({ timeout: 10000 });
  await page.getByPlaceholder("③ business-base 绝对路径").fill(resolve(".e2e-target", "business-base"));
  await page.getByRole("button", { name: "生成入金申请代码" }).click();
  await expect(page.getByText("CODE_REVIEW", { exact: true })).toBeVisible({ timeout: 10000 });
  await page.getByRole("tab", { name: "代码预览" }).click();
  await expect(page.getByText("generated-routes.ts", { exact: true })).toBeVisible();
});
