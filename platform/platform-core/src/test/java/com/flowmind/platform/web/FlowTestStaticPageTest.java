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
                .contains("data-testid=\"listener-reject-enabled\"")
                .contains("data-testid=\"listener-reject-targets\"")
                .contains("data-testid=\"listener-direct-send-enabled\"")
                .contains("data-testid=\"listener-config-json\"")
                .contains("@change=\"syncSelectedNodeListenerRules\"")
                .contains("@change=\"syncSelectedNodeListenerJson\"")
                .contains("v-for=\"node in taskRejectTargetNodes()\"")
                .doesNotContain("v-for=\"node in selectedGraphNodes.filter(isUserTaskNode)\"")
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
                .doesNotContain("/gray");

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
                .contains("taskTransfer: \"/api/platform/runtime/tasks/transfer\"")
                .contains("taskAddSign: \"/api/platform/runtime/tasks/add-sign\"")
                .contains("taskClaim: \"/api/platform/runtime/tasks/claim\"")
                .contains("taskUnclaim: \"/api/platform/runtime/tasks/unclaim\"")
                .contains("canClaimTask: function (task)")
                .contains("canUnclaimTask: function (task)")
                .contains("isCurrentUserTaskCandidate: function (task)")
                .contains("submitTaskClaimAction")
                .doesNotContain("\"/api/platform/instances/start-and-submit\"")
                .doesNotContain("\"/api/platform/tasks/\" + encodeURIComponent(taskId)")
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
                .contains("listenerConfig: \"\"")
                .contains("readListenerRuleEditor")
                .contains("syncNodeListenerRules")
                .contains("pruneListenerRejectTarget")
                .contains("taskRejectTargetNodes: function ()")
                .contains("editor.listenerRejectTargetNodeCodes.map")
                .contains("!editor.listenerRejectEnabled")
                .contains("task.definitionId !== detailDefinitionId")
                .contains("delete copy.listenerConfigError")
                .contains("targetMode = \"REJECT_SOURCE\"")
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
                .contains("attachments: this.buildInstanceAttachments()")
                .contains("startSubmitting")
                .contains("lastStartedInstanceId")
                .contains("focusCurrentStartedTodos")
                .contains("formatAttachmentSize")
                .contains("formatDateTime")
                .contains("sizeBytes: 0")
                .contains("content: \"\"")
                .doesNotContain("content: \"Zmxvdy1taW5kLXRlc3Q=\"")
                .doesNotContain("sizeBytes: 1280")
                .contains("var user = this.currentUser()")
                .contains("starterDeptId: user.deptId")
                .contains("openTodoTaskDialog")
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
                .contains("this.sendRequest(\"图校验\", \"GET\", API_PATHS.validateDefinition")
                .doesNotContain("this.sendRequest(\"图校验\", \"POST\", API_PATHS.validateDefinition")
                .doesNotContain("/gray");

        assertThat(css)
                .contains(".app-shell")
                .contains(".definition-table")
                .contains(".flow-canvas")
                .contains(".canvas-surface")
                .contains(".designer-node")
                .contains(".designer-edge")
                .contains(".attachment-table")
                .contains(".listener-config-panel")
                .contains(".field-error")
                .contains(".storage-badge")
                .contains(".attachment-meta")
                .contains(".task-dialog")
                .contains(".status-bar")
                .contains(".operation-log")
                .contains(":focus-visible")
                .contains("@media (max-width: 960px)")
                .contains("overflow: auto")
                .doesNotContain("overflow: hidden;")
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
}
