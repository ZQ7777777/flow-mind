package com.flowmind.platform.web;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流程平台功能测试前端静态资源契约测试。
 *
 * @author FlowMind
 * @since 2026-07-29
 */
class FlowTestStaticPageTest {

    @Test
    void instancePageShouldExposeReminderActionsWithoutRegressingExistingFunctions() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String instancePage = substringBetween(html,
                "<section v-show=\"activeView === 'instances'\"",
                "<section v-show=\"activeView === 'todo'\"");

        assertThat(instancePage)
                .contains("currentInstanceNodeCodes(row)")
                .contains("@click.stop=\"remindInstanceNode(row, nodeCode)\"")
                .contains("selectedInstanceActiveTasks")
                .contains("@click.stop=\"remindInstanceTask(task)\"")
                .contains("手动催办")
                .contains("data-testid=\"readonly-instance-field\"")
                .contains("@click=\"terminateSelectedInstance()\"")
                .contains("@click=\"deleteSelectedInstance()\"")
                .doesNotContain("保存表单字段")
                .doesNotContain("操作意见")
                .doesNotContain(">撤回实例</button>");

        assertThat(script)
                .contains("taskRemind: function (taskId)")
                .doesNotContain("remindTask: function (taskId)")
                .contains("loadSelectedInstanceDetail: function (instanceId)")
                .contains("activeTasks: normalizeList(tasksPayload)")
                .contains("canRemindInstanceTask: function (task)")
                .contains("task.expectedTaskVersion !== undefined")
                .contains("remindInstanceNode: function (row, nodeCode)")
                .contains("remindInstanceTask: function (task)")
                .contains("expectedTaskVersion: extractTaskVersion(task)")
                .contains("this.sendRequest(\"手动催办\", \"POST\", API_PATHS.taskRemind(taskId), body)")
                .contains("autoClaimSpecifiedUserTasks: function (instance)")
                .contains("this.autoClaimSpecifiedUserTasks(instance)")
                .contains("normalizeJsonConfigValue")
                .contains("readTimeoutReminderEditor")
                .contains("todoTimeoutPriority");
    }

    @Test
    void todoReminderBadgeShouldShowManualAndTimeoutRemindersWithoutTaskDialogReminderAction() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String taskDialog = substringBetween(html,
                "<section v-if=\"taskDialog.open\"",
                "</section>");

        assertThat(taskDialog)
                .doesNotContain("@click=\"remindCurrentTask\"")
                .doesNotContain(">手动催办</button>");

        assertThat(script)
                .contains("return this.enrichTodoReminders(this.todoRows)")
                .contains("enrichTodoReminders: function (rows)")
                .contains("return hasText(extractTaskId(task));")
                .contains("queryTaskReminders: function (task)")
                .contains("this.sendRequest(\"查询任务提醒\", \"GET\", API_PATHS.reminders + toQuery({")
                .contains("reminderBadgeLabel: function (reminder)")
                .contains("reminder.reminderType === \"MANUAL\"")
                .contains("return failed ? \"手动催办失败\" : \"手动催办\"")
                .contains("reminder.reminderType === \"TIMEOUT\"")
                .contains("return failed ? \"超时催办失败\" : \"超时催办\"")
                .contains("this.rememberTaskReminderStatus(task, [payload])")
                .doesNotContain("queryTaskTimeoutReminder")
                .doesNotContain("reminderType: \"TIMEOUT\"")
                .doesNotContain("remindCurrentTask: function ()");
    }

    @Test
    void todoListShouldKeepPagedQueryRecordsAfterStartingInstance() throws IOException {
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String todoQuery = substringBetween(script,
                "queryTodoTasks: function () {",
                "setTodoScope: function (scope) {");

        assertThat(todoQuery)
                .contains("this.todoRows = this.sortTodoRows(normalizeList(payload));")
                .doesNotContain("focusCurrentStartedTodos");
        assertThat(script)
                .doesNotContain("lastStartedInstanceId")
                .doesNotContain("focusCurrentStartedTodos");
    }

    @Test
    void instanceAndCompletedListsShouldUseBackendDefaultPageSize() throws IOException {
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String adminInstanceQuery = substringBetween(script,
                "queryAdminInstances: function () {",
                "autoClaimSpecifiedUserTasks: function (instance) {");
        String startedInstanceQuery = substringBetween(script,
                "queryInstances: function () {",
                "markSelectedInstanceRead: function (instanceId) {");
        String completedTaskQuery = substringBetween(script,
                "queryCompletedTasks: function () {",
                "openTodoTaskDialog: function (row) {");

        assertThat(script)
                .contains("var BACKEND_DEFAULT_PAGE_SIZE = 5;");
        assertThat(adminInstanceQuery)
                .contains("pageSize: BACKEND_DEFAULT_PAGE_SIZE")
                .doesNotContain("pageSize: 50");
        assertThat(startedInstanceQuery)
                .contains("pageSize: BACKEND_DEFAULT_PAGE_SIZE")
                .doesNotContain("pageSize: 50");
        assertThat(completedTaskQuery)
                .contains("pageSize: BACKEND_DEFAULT_PAGE_SIZE")
                .doesNotContain("pageSize: 50");
    }

    @Test
    void instanceListShouldAppendNextPageWhenScrolled() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");
        String css = loadResource("/static/flow-test/flow-test-app.css");

        String instanceList = substringBetween(html,
                "<h3>实例列表</h3>",
                "<section v-if=\"selectedInstanceDetail\"");
        String queryInstances = substringBetween(script,
                "queryInstances: function () {",
                "markSelectedInstanceRead: function (instanceId) {");
        String loadInstancePage = substringBetween(script,
                "loadInstancePage: function (pageNo, append) {",
                "buildInstanceListPath: function (pageNo) {");

        assertThat(instanceList)
                .contains("class=\"table-wrap instance-list-wrap\"")
                .contains("@scroll.passive=\"handleInstanceListScroll\"")
                .contains("v-if=\"instanceLoading\"")
                .contains("{{ instanceRows.length }} / {{ instanceTotal }}");
        assertThat(script)
                .contains("instancePageNo: 1")
                .contains("instancePageSize: BACKEND_DEFAULT_PAGE_SIZE")
                .contains("instanceTotal: 0")
                .contains("instanceLoading: false")
                .contains("canLoadMoreInstances: function ()")
                .contains("loadMoreInstances: function ()")
                .contains("handleInstanceListScroll: function (event)")
                .contains("mergeInstanceRows: function (existingRows, nextRows)");
        assertThat(queryInstances)
                .contains("return this.loadInstancePage(1, false);");
        assertThat(loadInstancePage)
                .contains("pageSize: this.instancePageSize")
                .contains("this.instanceRows = append ? this.mergeInstanceRows(this.instanceRows, rows) : rows;")
                .contains("this.instanceTotal = extractTotalCount(payload, this.instanceRows.length);")
                .contains("this.instanceLoading = false;");
        assertThat(css)
                .contains(".instance-list-wrap")
                .contains("--instance-list-visible-rows: 5;")
                .contains("max-height: calc(44px + (var(--instance-list-visible-rows) * 72px) + 36px);");
    }

    @Test
    void completedListShouldExposePageControls() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String completedPanel = substringBetween(html,
                "<section v-show=\"activeView === 'completed'\"",
                "<section class=\"operation-log\">");
        String completedQuery = substringBetween(script,
                "queryCompletedTasks: function () {",
                "changeCompletedPage: function (delta) {");

        assertThat(completedPanel)
                .contains("class=\"query-dialog-footer completed-pagination\"")
                .contains(":disabled=\"completedPageNo <= 1 || completedLoading\"")
                .contains("@click=\"changeCompletedPage(-1)\"")
                .contains("v-model.number=\"completedPageNo\"")
                .contains("@change=\"queryCompletedTasks\"")
                .contains("{{ completedTotalPages }}")
                .contains("{{ completedTotal }}")
                .contains("v-model.number=\"completedPageSize\"")
                .contains("@click=\"changeCompletedPage(1)\"");
        assertThat(script)
                .contains("completedPageNo: 1")
                .contains("completedPageSize: BACKEND_DEFAULT_PAGE_SIZE")
                .contains("completedTotal: 0")
                .contains("completedLoading: false")
                .contains("completedTotalPages: function ()")
                .contains("changeCompletedPage: function (delta)");
        assertThat(completedQuery)
                .contains("pageNo: this.completedPageNo")
                .contains("pageSize: this.completedPageSize")
                .contains("this.completedRows = rows;")
                .contains("this.completedTotal = extractTotalCount(payload, rows.length);")
                .contains("this.completedLoading = false;");
    }

    @Test
    void instanceDetailShouldKeepFormReadonlyAndOnlyExposeTerminateAndDeleteOperations() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String instanceDetail = substringBetween(html,
                "<section v-if=\"selectedInstanceDetail\"",
                "</section>");

        assertThat(instanceDetail)
                .contains("<h4>表单字段</h4><span class=\"muted\">只读</span>")
                .contains("data-testid=\"readonly-instance-field\"")
                .contains("{{ formatJsonSummary(row.value) }}")
                .contains("@click=\"terminateSelectedInstance()\"")
                .contains("@click=\"deleteSelectedInstance()\"")
                .doesNotContain("保存表单字段")
                .doesNotContain("updateSelectedInstanceVariables()")
                .doesNotContain("v-model")
                .doesNotContain("操作意见");

        assertThat(script)
                .doesNotContain("updateSelectedInstanceVariables: function ()")
                .doesNotContain("updateVariables: \"/api/platform/runtime/instances/variables\"")
                .doesNotContain("instanceOperationComment")
                .contains("terminateSelectedInstance: function ()")
                .contains("deleteSelectedInstance: function ()")
                .contains("comment: \"\"");
    }

    @Test
    void completedTaskDetailShouldExposeWithdrawUsingPreviousHandlerRule() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        assertThat(html)
                .contains("data-testid=\"withdraw-completed-task\"")
                .contains(":disabled=\"!canWithdrawSelectedInstance\"")
                .contains("@click=\"withdrawSelectedInstance()\"")
                .doesNotContain(">撤回实例</button>");

        assertThat(script)
                .contains("taskWithdraw: \"/api/platform/runtime/tasks/withdraw\"")
                .contains("selectedActiveTask: function ()")
                .contains("selectedPreviousHandlerTask: function ()")
                .contains("[\"SEND\", \"APPROVE\", \"REJECT\", \"RETURN\", \"DIRECT_SEND\"]")
                .contains("history.activeTaskId !== currentTaskId")
                .contains("activeTasks.length === 1")
                .contains("task.taskStatus === \"ACTIVE\" || task.taskStatus === \"CLAIMED\"")
                .contains("!hasText(task.taskGroupId)")
                .contains("!hasText(task.branchKey)")
                .contains("previous.assigneeUserId === this.currentUserId")
                .contains("this.completedDialog.instanceDetail = payload;")
                .contains("withdrawSelectedInstance: function ()")
                .contains("operationId: this.createOperationId(\"withdraw\")")
                .contains("taskId: extractTaskId(task)")
                .contains("expectedTaskVersion: extractTaskVersion(task)")
                .contains("operatorUserId: this.currentUserId")
                .contains("comment: \"\"")
                .contains("this.sendRequest(\"撤回\", \"POST\", API_PATHS.taskWithdraw, body)")
                .contains("this.refreshAfterWithdraw(instanceId)")
                .contains("this.completedDialog.open = false")
                .contains("撤回要求流程恰好只有一个活动任务")
                .contains("会签、或签或并行任务不支持撤回")
                .contains("找不到可恢复的上一办理节点")
                .contains("仅运行中的流程实例可以撤回");
    }

    @Test
    void saveDefinitionDraftValidationLoopShouldBindVueInstance() throws IOException {
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String nodeValidationLoop = substringBetween(script,
                "var nodeCodes = {};",
                "var paired;");

        assertThat(nodeValidationLoop)
                .contains("this.validateNodeTimeoutConfig(node)")
                .contains("                }, this);");
    }

    @Test
    void startSubmitShouldRequireEveryStartFormField() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        assertThat(html)
                .contains(":required=\"isStartFieldRequired(field)\"")
                .contains(":class=\"{'field-invalid': startFormFieldError(field)}\"")
                .contains("{{ startFormFieldError(field) }}");

        assertThat(script)
                .contains("startFieldErrors: {}")
                .contains("validateStartFormFields: function ()")
                .contains("请先为流程定义配置表单字段")
                .contains("请填写必填表单字段：")
                .contains("this.validateStartFormFields();")
                .contains("this.startFieldErrors = errors;");
    }

    @Test
    void delegateActionShouldUseDelegateApiAndOwnTodoShouldHideTransferredTasks() throws IOException {
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String todoFilter = substringBetween(script,
                "filteredTodoRows: function () {",
                "instanceDefinitionAttachments: function () {");
        String ownTodoHelper = substringBetween(script,
                "isOwnTodoTask: function (row) {",
                "todoScopeLabel: function (task) {");

        assertThat(script)
                .contains("delegateTask: \"/api/platform/runtime/tasks/delegate\"")
                .contains("targetUserId: this.taskDialog.delegateUserId")
                .contains("todoSource: this.todoSourceForCurrentScope()")
                .contains("setTodoScope: function (scope)")
                .contains("todoSourceForCurrentScope: function ()")
                .contains("return \"DELEGATED\"")
                .doesNotContain("delegateUserId: this.taskDialog.delegateUserId");
        assertThat(todoFilter)
                .contains("return this.isOwnTodoTask(row);");
        assertThat(ownTodoHelper)
                .contains("!hasText(row.delegateFromUserId)")
                .contains("!hasText(row.assigneeUserId) || row.assigneeUserId === this.currentUserId");
    }

    @Test
    void selectingInstanceShouldMarkReadAndWithdrawShouldFollowPreviousHandlerRule() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String readRecordPermission = substringBetween(script,
                "canQuerySelectedInstanceReadRecords: function () {",
                "canWithdrawSelectedInstance: function () {");

        assertThat(html)
                .contains("data-testid=\"withdraw-completed-task\"")
                .doesNotContain(":disabled=\"!canStarterOperateSelectedInstance\"");
        assertThat(readRecordPermission)
                .contains("return !!extractInstanceId(this.selectedInstanceDetail);")
                .doesNotContain("starterUserId")
                .doesNotContain("isTestAdmin");

        assertThat(script)
                .contains("markRead: function (instanceId)")
                .contains("\"/api/platform/instances/\" + encodeURIComponent(instanceId) + \"/read\"")
                .contains("canWithdrawSelectedInstance: function ()")
                .contains("selectedPreviousHandlerTask")
                .contains("previous.assigneeUserId === this.currentUserId")
                .contains("return this.sendRequest(\"记录已阅\", \"POST\", API_PATHS.markRead(instanceId), {})")
                .contains("this.recordSelectedInstanceRead(instanceId);")
                .contains("recordTodoRowsRead: function (rows)")
                .contains("return this.recordTodoRowsRead(this.todoRows).then(function () {")
                .contains("return this.markSelectedInstanceRead(instanceId).catch(function () {")
                .doesNotContain("fetch(this.apiBaseUrl + path")
                .doesNotContain("this.markSelectedInstanceRead(instanceId).catch(function () {})")
                .doesNotContain("canStarterOperateSelectedInstance");
    }

    @Test
    void staticPageShouldExposeVue3FlowFunctionTestConsole() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");
        String css = loadResource("/static/flow-test/flow-test-app.css");

        assertThat(html)
                .contains("流程平台功能测试")
                .contains("id=\"app\"")
                .contains("vue.global.prod.js")
                .contains("flow-test-app.css")
                .contains("flow-test-app.js")
                .contains("v-if=\"definitionDialog.open\"")
                .contains("v-if=\"taskDialog.open\"")
                .contains("v-if=\"completedDialog.open\"")
                .contains("storage-badge")
                .contains("attachment-info")
                .contains("formatAttachmentSize(attachment.sizeBytes)")
                .contains("downloadAttachment(attachment)")
                .contains("@click.stop=\"claimTask(row)\"")
                .contains("@click.stop=\"unclaimTask(row)\"")
                .contains("taskClaimStatusText(row)")
                .contains("canHandleTask(taskDialog.task)")
                .contains("@keydown.delete=\"handleDesignerDelete\"")
                .contains("@click=\"startConnectionMode\"")
                .contains("@click=\"startAndSubmitInstance\"")
                .contains("data-testid=\"start-form-field\"")
                .contains("v-for=\"field in instanceDefinitionFields\"")
                .contains("v-model.trim=\"instanceFieldValues[formFieldCode(field)]\"")
                .doesNotContain("外部业务键")
                .doesNotContain("流程变量 JSON")
                .contains("data-testid=\"listener-reject-enabled\"")
                .contains("data-testid=\"listener-reject-targets\"")
                .contains("data-testid=\"listener-direct-send-enabled\"")
                .contains("data-testid=\"task-variables\"")
                .contains("data-testid=\"starter-resubmit\"")
                .contains("data-testid=\"direct-send\"")
                .contains("data-testid=\"replace-instance-attachment\"")
                .doesNotContain("data-testid=\"new-instance-attachment\"")
                .doesNotContain("新增实例附件")
                .contains(":readonly=\"!taskDialog.starterTask\"")
                .contains("@click=\"directSendCurrentTask\"")

                .contains("data-testid=\"listener-config-json\"")
                .contains("data-testid=\"timeout-enabled\"")
                .contains("data-testid=\"timeout-duration\"")
                .contains("data-testid=\"timeout-action\"")
                .contains("data-testid=\"timeout-target-node\"")
                .contains("data-testid=\"reminder-enabled\"")
                .contains("data-testid=\"reminder-max-count\"")
                .contains("data-testid=\"reminder-message-template\"")
                .contains("@change=\"syncSelectedNodeListenerRules\"")
                .contains("@change=\"syncSelectedNodeListenerJson\"")
                .contains("@change=\"syncSelectedNodeTimeoutConfig\"")
                .contains("data-testid=\"start-form-field\"")
                .contains("data-testid=\"instance-variable-editor\"")
                .contains("v-for=\"node in taskRejectTargetNodes()\"")
                .doesNotContain("v-for=\"node in selectedGraphNodes.filter(isUserTaskNode)\"")
                .contains("提醒标记")
                .contains("超时时间")
                .contains("data-testid=\"todo-timeout-badge\"")
                .contains("已阅记录")
                .contains("@click=\"openInstanceQueryDialog('readRecords')\"")
                .contains("@click=\"openInstanceQueryDialog('historyTasks')\"")
                .contains("@click=\"openInstanceQueryDialog('comments')\"")
                .contains("@click=\"openInstanceQueryDialog('callbackLogs')\"")
                .contains("@click=\"openInstanceQueryDialog('auditTrace')\"")
                .contains("v-if=\"instanceQueryDialog.open\"")
                .contains("class=\"modal-panel query-dialog\"")
                .contains("query-dialog-list")
                .contains("queryDialogRows")
                .contains("@click=\"closeInstanceQueryDialog\"")
                .contains("@click=\"changeInstanceQueryPage(-1)\"")
                .contains("@click=\"changeInstanceQueryPage(1)\"")
                .contains("v-model.number=\"instanceQueryDialog.pageSize\"")
                .doesNotContain("v-if=\"readRecordRows.length === 0\"")
                .doesNotContain("v-if=\"callbackLogRows.length === 0\"")
                .doesNotContain("v-if=\"historyTaskRows.length === 0\"")
                .doesNotContain("v-if=\"commentRows.length === 0\"")
                .doesNotContain("v-if=\"auditTraceRows.length === 0\"")
                .contains("instanceVariableRows")
                .contains("data-testid=\"readonly-instance-field\"")
                .doesNotContain("@click=\"updateSelectedInstanceVariables()\"")
                .contains("@click=\"withdrawSelectedInstance()\"")
                .contains("@click=\"queryAdminInstances()\"")
                .contains("@click=\"terminateSelectedInstance()\"")
                .contains("@click=\"deleteSelectedInstance()\"")
                .contains("canQuerySelectedInstanceReadRecords")
                .contains("class=\"property-panel property-panel-scroll\"")
                .contains("data-testid=\"save-definition-draft\"")
                .doesNotContain("恢复目标节点")
                .doesNotContain("恢复流转")
                .doesNotContain("adminRecoverTargetNodeCode")
                .doesNotContain("recoverTargetNodes")
                .doesNotContain("recoverSelectedInstance")
                .contains("data-testid=\"todo-scope-own\"")
                .contains("data-testid=\"todo-scope-delegated\"")
                .contains("@click=\"setTodoScope('own')\"")
                .contains("@click=\"setTodoScope('delegated')\"")
                .contains("data-testid=\"delegate-user\"")
                .contains("@click=\"delegateCurrentTask\"")
                .doesNotContain("外部业务键")
                .doesNotContain("流程变量 JSON")
                .contains("v-if=\"isParallelGatewayNode(selectedNode)\"")
                .contains("配对网关")
                .contains("parallelGatewayPairOptions(selectedNode)")
                .contains("@click.stop=\"copyDefinition(row)\"")
                .contains("class=\"canvas-surface\"")
                .contains(":viewBox=\"canvasViewBox(selectedGraphNodes, 480)\"")
                .contains(":viewBox=\"canvasViewBox(definitionDraft.nodes, 480)\"")
                .doesNotContain("viewBox=\"0 0 960 420\"")
                .doesNotContain("viewBox=\"0 0 960 480\"")
                .doesNotContain("灰度发布")
                .doesNotContain("/gray")
                .doesNotContain("businessKey: this.instanceForm.businessKey")
                .doesNotContain("instanceVariablesText");

        assertThat(script)
                .contains("Vue.createApp")
                .contains("FLOW_TEST_USERS")
                .contains("业务员")
                .contains("组长")
                .contains("部门经理1")
                .contains("部门经理2")
                .contains("财务1")
                .contains("财务2")
                .contains("CEO")
                .contains("测试管理员")
                .contains("u_sales_01")
                .contains("u_group_leader_01")
                .contains("u_dept_manager_01")
                .contains("u_dept_manager_02")
                .contains("u_finance_01")
                .contains("u_finance_02")
                .contains("u_ceo_01")
                .contains("u_admin_01")
                .contains("currentUserId: \"u_admin_01\"")
                .contains("starterDeptId: \"mock-dept\"")
                .contains("mock-dept")
                .contains("dept_sales")
                .contains("dept_manager")
                .contains("dept_finance")
                .doesNotContain("{departmentId: \"dept_manager\"")
                .contains("FLOW_TEST_DEPARTMENTS")
                .contains("FLOW_TEST_ROLES")
                .doesNotContain("user_sales")
                .doesNotContain("user_sales_manager")
                .doesNotContain("user_manager")
                .doesNotContain("user_finance")
                .doesNotContain("user_admin")
                .doesNotContain("mock-user")
                .contains("API_PATHS")
                .contains("\"/api/platform/definitions\"")
                .contains("publishDefinition: \"/api/platform/definitions/publish\"")
                .contains("activateDefinition: \"/api/platform/definitions/activate\"")
                .contains("deactivateDefinition: \"/api/platform/definitions/deactivate\"")
                .contains("archiveDefinition: \"/api/platform/definitions/archive\"")
                .contains("deleteDefinition: \"/api/platform/definitions\"")
                .contains("copyDefinition: function (definitionId)")
                .contains("\"/api/platform/definitions/\" + encodeURIComponent(definitionId) + \"/copy\"")
                .doesNotContain("publishDefinition: function (definitionId)")
                .doesNotContain("activateDefinition: function (definitionId)")
                .doesNotContain("deactivateDefinition: function (definitionId)")
                .doesNotContain("archiveDefinition: function (definitionId)")
                .doesNotContain("deleteDefinition: function (definitionId)")
                .contains("\"/api/platform/definitions/\" + encodeURIComponent(definitionId) + \"/graph\"")
                .contains("\"/api/platform/definitions/\" + encodeURIComponent(definitionId) + \"/publish-validation\"")
                .doesNotContain("\"/api/platform/definitions/\" + encodeURIComponent(definitionId) + \"/validate\"")
                .contains("startAndSubmit: \"/api/platform/runtime/instances/start-submit\"")
                .contains("taskSubmit: \"/api/platform/runtime/tasks/submit\"")
                .contains("taskApprove: \"/api/platform/runtime/tasks/approve\"")
                .contains("taskReject: \"/api/platform/runtime/tasks/reject\"")
                .contains("taskDirectSend: \"/api/platform/runtime/tasks/direct-send\"")
                .contains("taskTransfer: \"/api/platform/runtime/tasks/transfer\"")
                .contains("taskAddSign: \"/api/platform/runtime/tasks/add-sign\"")
                .contains("directSendContext: function (taskId)")
                .contains("\"/api/platform/tasks/\" + encodeURIComponent(taskId) + \"/direct-send-context\"")
                .contains("replaceInstanceAttachment: function (instanceId, attachmentId)")
                .contains("taskClaim: \"/api/platform/runtime/tasks/claim\"")
                .contains("taskUnclaim: \"/api/platform/runtime/tasks/unclaim\"")
                .contains("canClaimTask: function (task)")
                .contains("canUnclaimTask: function (task)")
                .contains("isCurrentUserTaskCandidate: function (task)")
                .contains("submitTaskClaimAction")
                .contains("taskWithdraw: \"/api/platform/runtime/tasks/withdraw\"")
                .contains("delegateTask: \"/api/platform/runtime/tasks/delegate\"")
                .doesNotContain("updateVariables: \"/api/platform/runtime/instances/variables\"")
                .contains("terminateInstance: \"/api/platform/runtime/instances/terminate\"")
                .contains("deleteInstance: \"/api/platform/runtime/instances\"")
                .contains("adminInstances: \"/api/platform/admin/instances\"")
                .doesNotContain("adminJump: function (instanceId)")
                .contains("callbackLogs: \"/api/platform/admin/callback-logs\"")
                .contains("auditLogs: \"/api/platform/admin/audit-logs\"")
                .contains("taskWithdraw: \"/api/platform/runtime/tasks/withdraw\"")
                .contains("delegateTask: \"/api/platform/runtime/tasks/delegate\"")
                .contains("reminders: \"/api/platform/reminders\"")
                .contains("taskRemind: function (taskId)")
                .contains("\"/api/platform/tasks/\" + encodeURIComponent(taskId) + \"/remind\"")
                .contains("adminTimeoutScan: \"/api/platform/admin/timeout-scan\"")
                .contains("readRecords: function (instanceId)")
                .contains("adminInstances: \"/api/platform/admin/instances\"")
                .contains("\"/api/platform/instances/\" + encodeURIComponent(instanceId) + \"/read-records\"")
                .doesNotContain("\"/api/platform/instances/start-and-submit\"")
                .contains("\"/api/platform/tasks/todo\"")
                .contains("\"/api/platform/tasks/completed\"")
                .contains("\"/api/platform/instances/started\"")
                .contains("\"/api/platform/attachments\"")
                .contains("attachmentQueryPath")
                .contains("API_PATHS.attachmentDownload(attachmentId)")
                .contains("\"/api/platform/attachment-templates\"")
                .contains("queryAttachmentTemplates")
                .contains("createAttachmentTemplate")
                .contains("selectExistingAttachmentTemplate")
                .contains("prepareCustomAttachmentTemplates")
                .contains("buildGraphRequest")
                .contains("timeoutConfig: \"\"")
                .contains("reminderConfig: \"\"")
                .contains("readTimeoutReminderEditor")
                .contains("syncNodeTimeoutConfig")
                .contains("validateNodeTimeoutConfig")
                .contains("timeoutReminderTargetNodes: function (node)")
                .contains("instanceFieldValues: {}")
                .contains("instanceVariableValues: {}")
                .contains("instanceDefinitionFields: function ()")
                .contains("selectedInstanceFields: function ()")
                .contains("buildVariablesFromFields")
                .contains("instanceQueryDialog:")
                .contains("queryDialogRows: function ()")
                .contains("queryDialogTotalPages: function ()")
                .contains("openInstanceQueryDialog: function (queryType)")
                .contains("queryInstanceDialogPage: function ()")
                .contains("changeInstanceQueryPage: function (delta)")
                .contains("closeInstanceQueryDialog: function ()")
                .contains("queryInstanceHistoryTasks")
                .contains("queryInstanceComments")
                .contains("queryInstanceCallbackLogs")
                .contains("queryInstanceAuditTrace")
                .doesNotContain("updateSelectedInstanceVariables")
                .contains("withdrawSelectedInstance")
                .contains("terminateSelectedInstance")
                .contains("deleteSelectedInstance")
                .doesNotContain("adminRecoverTargetNodeCode")
                .doesNotContain("recoverTargetNodes")
                .doesNotContain("recoverSelectedInstance")
                .doesNotContain("adminJump")
                .contains("filteredTodoRows: function ()")
                .contains("delegateCurrentTask")
                .contains("listenerConfig: \"\"")
                .contains("readListenerRuleEditor")
                .contains("syncNodeListenerRules")
                .contains("pruneListenerRejectTarget")
                .contains("taskRejectTargetNodes: function ()")
                .contains("editor.listenerRejectTargetNodeCodes.map")
                .contains("passedRejectHistoryNodeCodes: function ()")
                .contains("isRejectTargetPassedByInstance: function (nodeCode)")
                .contains("currentReachableRejectNodeCodes: function ()")
                .contains("collectReachableRejectTargetNodes: function (nodeCode, nodesByCode, outgoingBySource,")
                .contains("selectCurrentExclusiveEdge: function (node, outgoing, variables)")
                .contains("evaluateCurrentConditionExpression: function (expression, variables)")
                .contains("isRejectTargetCurrentlyReachable: function (nodeCode)")
                .contains("未通过该节点，请重新选择")
                .contains("!editor.listenerRejectEnabled")
                .contains("task.definitionId !== detailDefinitionId")
                .contains("delete copy.listenerConfigError")
                .contains("FLOW_FROZEN_MODEL_PARALLEL_GATEWAY_PAIR_INVALID")
                .contains("并行网关配对无效：请在流程图中为并行分支和并行汇聚设置互相配对，或删除会签定义里不需要的并行网关")
                .contains("isParallelGatewayNode: function (node)")
                .contains("parallelGatewayPairOptions: function (node)")
                .contains("并行网关必须选择配对网关")
                .contains("并行网关配对必须互相指向")
                .doesNotContain("pairFirstUnpairedParallelGateway")
                .contains("copyDefinition: function (row)")
                .contains("CANVAS_MIN_WIDTH")
                .contains("canvasSurfaceStyle: function (nodes, minHeight)")
                .contains("canvasViewBox: function (nodes, minHeight)")
                .contains("positionX")
                .contains("positionY")
                .contains("connectionClickQueue")
                .contains("deleteSelectedDesignerItem")
                .contains("persistDefinitionDraftAfterDesignerDelete")
                .contains("this.sendRequest(\"同步流程图\", \"PUT\", API_PATHS.saveGraph")
                .contains("连线已删除并同步到后端")
                .contains("buildUserApproverRule")
                .contains("normalizeMultiInstanceMode")
                .contains("node.multiInstanceMode = normalizeMultiInstanceMode(node.multiInstanceMode, ids.length)")
                .doesNotContain("node.multiInstanceMode = ids.length <= 1 ? \"SINGLE\" : (node.multiInstanceMode || \"OR_SIGN\")")
                .contains("buildStartSubmitBody")
                .contains("buildStartVariables")
                .contains("applyInstanceFormFields")
                .contains("buildInstanceVariableRows")
                .contains("attachments: this.buildInstanceAttachments()")
                .contains("startSubmitting")
                .doesNotContain("lastStartedInstanceId")
                .doesNotContain("focusCurrentStartedTodos")
                .contains("enrichTodoReminders")
                .contains("taskTimeoutBadge: function (task)")
                .contains("queryTaskReminders")
                .contains("queryInstanceReadRecords")
                .contains("reminderBadgeLabel")
                .contains("formatAttachmentSize")
                .contains("formatDateTime")
                .contains("sizeBytes: 0")
                .contains("content: \"\"")
                .doesNotContain("content: \"Zmxvdy1taW5kLXRlc3Q=\"")
                .doesNotContain("sizeBytes: 1280")
                .contains("var user = this.currentUser()")
                .contains("userDepartmentId(user)")
                .contains("starterDeptId: userDepartmentId(user)")
                .contains("\"X-Flow-Dept-Id\": userDepartmentId(this.currentUser())")
                .contains("openTodoTaskDialog")
                .contains("loadTaskDialogContext: function (task)")
                .contains("Promise.all([")
                .contains("submitCurrentStarterTask")
                .contains("directSendCurrentTask")
                .contains("savePendingTaskAttachmentReplacements")
                .contains("replacement.saved = true")
                .doesNotContain("handleTaskNewAttachmentFileChange")
                .doesNotContain("pendingAttachments")
                .contains("refreshAfterTaskAction")
                .contains("todoScope: \"own\"")
                .contains("filteredTodoRows: function ()")
                .contains("taskSourceLabel: function (task)")
                .contains("delegateCurrentTask")
                .contains("openCompletedTaskDialog")
                .contains("setOperationState")
                .contains("operationLogs")
                .contains("\"X-Flow-Dept-Id\"")
                .doesNotContain("\"X-Flow-Dept-Name\"")
                .contains("definitionId: definitionId")
                .contains("typeof pathBuilder === \"function\"")
                .contains("var path = typeof pathBuilder === \"function\" ? pathBuilder(taskId) : pathBuilder")
                .doesNotContain("return this.sendRequest(\"提交申请节点\", \"POST\", API_PATHS.taskSubmit, body)")
                .doesNotContain("submit_apply_task")
                .doesNotContain("instanceVariablesText")
                .doesNotContain("businessKey: this.instanceForm.businessKey")
                .contains("this.sendRequest(\"图校验\", \"GET\", API_PATHS.validateDefinition")
                .contains("appendRequestQuery: function (url, params)")
                .contains("methodName === \"GET\" || methodName === \"HEAD\"")
                .contains("options.body = JSON.stringify(body)")
                .doesNotContain("this.sendRequest(\"图校验\", \"POST\", API_PATHS.validateDefinition")
                .doesNotContain("/gray")
                .doesNotContain("businessKey: this.instanceForm.businessKey")
                .doesNotContain("instanceVariablesText");

        assertThat(css)
                .contains(".app-shell")
                .contains(".definition-table")
                .contains(".flow-canvas")
                .contains(".canvas-surface")
                .contains(".designer-node")
                .contains(".designer-edge")
                .contains(".attachment-table")
                .contains(".listener-config-panel")
                .contains(".timeout-config-panel")
                .contains(".timeout-badge")
                .contains(".instance-support-grid")
                .contains(".query-dialog")
                .contains(".query-dialog-list")
                .contains(".query-dialog-footer")
                .contains(".property-panel-scroll")
                .contains("grid-template-columns: minmax(140px, 170px) minmax(0, 1fr) 280px")
                .contains("width: 280px")
                .contains("height: calc(92vh - 210px)")
                .contains("max-height: calc(92vh - 210px)")
                .contains("overflow-y: auto")
                .doesNotContain("grid-template-columns: minmax(140px, 170px) minmax(0, 1fr) minmax(240px, 320px)")
                .contains(".audit-trace-list")
                .contains(".todo-segmented")
                .contains(".timeout-badge.overdue")
                .contains(".timeout-badge.reminded")
                .contains(".scope-tabs")
                .contains(".aux-query-actions")
                .contains(".audit-trace-table")
                .contains(".field-error")
                .contains(".storage-badge")
                .contains(".attachment-meta")
                .contains(".task-dialog")
                .contains(".status-bar")
                .contains(".operation-log")
                .contains(":focus-visible")
                .contains("@media (max-width: 960px)")
                .contains("overflow: auto")
                .doesNotContain("body {\n    overflow: hidden;")
                .doesNotContain(".right-rail");
    }

    private String loadResource(String path) throws IOException {
        URL resource = FlowTestStaticPageTest.class.getResource(path);
        assertThat(resource).as("resource " + path).isNotNull();
        try (InputStream inputStream = resource.openStream()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String substringBetween(String value, String start, String end) {
        int startIndex = value.indexOf(start);
        assertThat(startIndex).as("start marker").isNotNegative();
        int endIndex = value.indexOf(end, startIndex + start.length());
        assertThat(endIndex).as("end marker").isGreaterThan(startIndex);
        return value.substring(startIndex, endIndex);
    }
}
