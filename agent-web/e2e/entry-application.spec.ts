import { expect, test } from "@playwright/test";
import { resolve } from "node:path";

test("入金申请从对话走到发布激活", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("用户名").fill("admin01");
  await page.getByLabel("密码").fill("123456");
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.locator(".create-card input")).toHaveValue(resolve(".e2e-target", "business-base"));
  await expect(page.getByRole("heading", { name: "新建 Agent 会话" })).toBeVisible();
  await page.getByRole("button", { name: "开始采集需求" }).click();
  const workflowState = page.locator(".workflow-header .el-tag");
  await expect(workflowState).toHaveText("COLLECTING");

  const conversationPanel = page.locator(".conversation-panel");
  const panelDivider = page.getByRole("separator", { name: "调整 Agent 对话与功能面板宽度" });
  const initialConversationBox = await conversationPanel.boundingBox();
  const dividerBox = await panelDivider.boundingBox();
  expect(initialConversationBox).not.toBeNull();
  expect(dividerBox).not.toBeNull();
  await page.mouse.move(dividerBox!.x + dividerBox!.width / 2, dividerBox!.y + 100);
  await page.mouse.down();
  await page.mouse.move(dividerBox!.x + dividerBox!.width / 2 + 80, dividerBox!.y + 100);
  await page.mouse.up();
  const resizedConversationBox = await conversationPanel.boundingBox();
  expect(resizedConversationBox!.width).toBeGreaterThan(initialConversationBox!.width + 70);
  expect(await page.evaluate(() => localStorage.getItem("flowmind.agent.conversationPanelRatio"))).not.toBeNull();

  await page.reload();
  await expect(workflowState).toHaveText("COLLECTING");
  const restoredConversationBox = await conversationPanel.boundingBox();
  expect(Math.abs(restoredConversationBox!.width - resizedConversationBox!.width)).toBeLessThan(2);
  await panelDivider.focus();
  await page.keyboard.press("ArrowLeft");
  const keyboardResizedConversationBox = await conversationPanel.boundingBox();
  expect(Math.abs(keyboardResizedConversationBox!.width - (restoredConversationBox!.width - 16))).toBeLessThan(2);

  const composer = page.getByPlaceholder("描述业务流程，或回答 Agent 的问题…");
  await composer.fill("我要做一个入金申请流程");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText(/请补充参与角色/)).toBeVisible();

  await composer.fill("业务员提交，字段为申请人姓名、入金金额、入金账号，上传银行回单，部门经理审批后财务确认。");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(workflowState).toHaveText("REQUIREMENT_REVIEW");
  await expect(page.getByRole("textbox", { name: "业务名称" })).toHaveValue("入金申请");

  await page.getByRole("button", { name: "确认需求并创建流程" }).click();
  await expect(workflowState).toHaveText("PROCESS_REVIEW", { timeout: 10000 });
  await page.getByRole("tab", { name: "流程预览" }).click();
  await expect(page.getByText("入金申请_v1")).toBeVisible();
  await expect(page.getByRole("button", { name: "部门经理审批 用户任务" })).toBeVisible();
  await expect(page.getByText("银行回单", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "确认流程并激活" }).click();
  await expect(workflowState).toHaveText("PROCESS_ACTIVE", { timeout: 10000 });
  await page.getByPlaceholder("③ business-base 绝对路径").fill(resolve(".e2e-target", "business-base"));
  await page.getByRole("button", { name: "生成业务发起代码" }).click();
  await expect(workflowState).toHaveText("CODE_REVIEW", { timeout: 10000 });
  await page.getByRole("tab", { name: "代码预览" }).click();
  await expect(page.getByText("generated-routes.ts", { exact: true })).toBeVisible();
  const previewFrame = page.frameLocator('iframe[title="Agent 生成前端界面静态预览"]');
  await expect(previewFrame.locator("section")).toBeVisible();
  await expect(previewFrame.locator('[data-preview-action="submit"]')).toHaveCount(0);
});

test("质押动态需求生成同源参考数据调用与多选 DTO", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("用户名").fill("admin01");
  await page.getByLabel("密码").fill("123456");
  await page.getByRole("button", { name: "登录" }).click();

  const sessionResponse = page.waitForResponse((response) =>
    response.url().endsWith("/api/agent/sessions") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "开始采集需求" }).click();
  const { sessionId } = await (await sessionResponse).json() as { sessionId: string };
  const workflowState = page.locator(".workflow-header .el-tag");
  const composer = page.getByPlaceholder("描述业务流程，或回答 Agent 的问题…");
  await composer.fill("我要演示仓单、国债（解）质押申请，所有账户、交易所和品种都从参考数据 API 查询。");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText(/请补充参与角色/)).toBeVisible();
  await composer.fill("账号和交易所级联交易编码，多选品种并带出三个合约参数，提交后由风控审核。");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(workflowState).toHaveText("REQUIREMENT_REVIEW");
  await expect(page.getByRole("textbox", { name: "业务名称" })).toHaveValue("仓单、国债（解）质押申请");

  await page.getByRole("button", { name: "确认需求并创建流程" }).click();
  await expect(workflowState).toHaveText("PROCESS_REVIEW", { timeout: 10000 });
  await page.getByRole("button", { name: "确认流程并激活" }).click();
  await expect(workflowState).toHaveText("PROCESS_ACTIVE", { timeout: 10000 });
  await page.getByPlaceholder("③ business-base 绝对路径").fill(resolve(".e2e-target", "business-base"));
  const generationResponse = page.waitForResponse((response) =>
    response.url().endsWith("/code-generations") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "生成业务发起代码" }).click();
  const { generationId } = await (await generationResponse).json() as { generationId: string };
  await expect(workflowState).toHaveText("CODE_REVIEW", { timeout: 10000 });

  const apiFile = await page.request.get(
    `/api/agent/sessions/${sessionId}/code-generations/${generationId}/files/frontend/src/api/generated/warehouse-pledge/warehouse-pledge.ts`,
  );
  expect(apiFile.ok()).toBe(true);
  const apiContent = (await apiFile.json() as { content: string }).content;
  expect(apiContent).toContain("/api/reference-data/futures-accounts");
  expect(apiContent).toContain("/api/reference-data/futures-products?");
  expect(apiContent).toContain("/trading-codes?exchangeCode=");
  expect(apiContent).not.toContain('method: "POST"');

  const viewFile = await page.request.get(
    `/api/agent/sessions/${sessionId}/code-generations/${generationId}/files/frontend/src/modules/generated/warehouse-pledge/BusinessForm.vue`,
  );
  expect(viewFile.ok()).toBe(true);
  const viewContent = (await viewFile.json() as { content: string }).content;
  expect(viewContent).toContain('"productCodes"');
  expect(viewContent).toContain('"accountFunds"');
  expect(viewContent).toContain('"amount"');
  expect(viewContent).not.toContain("fetch(");

  const applyFile = await page.request.get(
    `/api/agent/sessions/${sessionId}/code-generations/${generationId}/files/frontend/src/modules/generated/warehouse-pledge/Apply.vue`,
  );
  expect(applyFile.ok()).toBe(true);
  const applyContent = (await applyFile.json() as { content: string }).content;
  expect(applyContent).toContain("WorkflowStartShell");
  expect(applyContent).toContain('process-code="warehouse_pledge"');
  expect(applyContent).not.toContain("fetch(");
});
