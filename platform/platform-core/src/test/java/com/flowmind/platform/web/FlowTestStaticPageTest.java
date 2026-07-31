package com.flowmind.platform.web;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static resource contract tests for the local flow function console.
 *
 * @author FlowMind
 * @since 2026-07-29
 */
class FlowTestStaticPageTest {

    @Test
    void saveDefinitionDraftValidationLoopShouldUseCurrentVueContext() throws IOException {
        String script = loadResource("/static/flow-test/flow-test-app.js");

        String validationBlock = substringBetween(script,
                "var nodeCodes = {};",
                "this.definitionDraft.edges.forEach(function (edge) {");

        assertThat(validationBlock)
                .contains("readListenerRuleEditor(node.listenerConfig)")
                .contains("this.isParallelGatewayNode(node)")
                .contains("paired = this.definitionDraft.nodes.find(function (candidate)")
                .contains("}, this);");
    }

    @Test
    void startSubmitShouldUseCurrentVariableAndAttachmentPayload() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        assertThat(html)
                .contains("data-testid=\"start-form-field\"")
                .contains(":required=\"isStartFieldRequired(field)\"")
                .contains("@click=\"startAndSubmitInstance\"")
                .contains("@change=\"handleInstanceAttachmentFileChange(attachment, $event)\"")
                .contains("v-model.trim=\"attachment.contentType\"");

        assertThat(script)
                .contains("buildStartSubmitBody: function ()")
                .contains("variables: this.buildStartVariables()")
                .contains("buildStartVariables: function ()")
                .contains("this.buildVariablesFromFields(this.instanceDefinitionFields, this.instanceFieldValues, true)")
                .contains("attachments: this.buildInstanceAttachments()")
                .contains("autoClaimSpecifiedUserTasks: function (instance)")
                .contains("isSpecifiedUserTask: function (task, definition)")
                .contains("autoClaimTask: function (task)")
                .contains("this.autoClaimSpecifiedUserTasks(instance)")
                .contains("attachmentUploadPayload: function (attachment)");
    }

    @Test
    void todoActionsShouldMatchCurrentStaticPage() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        assertThat(html)
                .contains("@click=\"queryTodoTasks\"")
                .contains("@click.stop=\"claimTask(row)\"")
                .contains("@click.stop=\"unclaimTask(row)\"")
                .contains("@click=\"openTodoTaskDialog(row)\"")
                .contains("v-model=\"taskDialog.transferUserId\"")
                .contains("@click=\"transferCurrentTask\"")
                .contains("@click=\"addSignCurrentTask\"")
                .contains("data-testid=\"todo-scope-delegated\"")
                .contains("@click=\"delegateCurrentTask\"");

        assertThat(script)
                .contains("todoTasks: \"/api/platform/tasks/todo\"")
                .contains("taskClaim: \"/api/platform/runtime/tasks/claim\"")
                .contains("taskUnclaim: \"/api/platform/runtime/tasks/unclaim\"")
                .contains("taskTransfer: \"/api/platform/runtime/tasks/transfer\"")
                .contains("taskAddSign: \"/api/platform/runtime/tasks/add-sign\"")
                .contains("queryTodoTasks: function ()")
                .contains("canClaimTask: function (task)")
                .contains("canUnclaimTask: function (task)")
                .contains("canHandleTask: function (task)")
                .contains("transferCurrentTask: function ()")
                .contains("targetUserId: this.taskDialog.transferUserId")
                .contains("delegateTask: \"/api/platform/runtime/tasks/transfer\"")
                .contains("delegateCurrentTask")
                .contains("filteredTodoRows: function ()")
                .contains("todoScope: \"own\"")
                .doesNotContain("delegateTask: \"/api/platform/runtime/tasks/delegate\"")
                .doesNotContain("delegateUserId: this.taskDialog.delegateUserId");
    }

    @Test
    void instanceSelectionShouldLoadDetailWithoutReadRecordOrWithdrawUi() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");

        assertThat(html)
                .contains("@click=\"selectInstance(row)\"")
                .contains("selectedInstanceActiveTasks")
                .contains("currentInstanceNodeCodes(row)")
                .contains("@click.stop=\"remindInstanceNode(row, nodeCode)\"")
                .contains("@click.stop=\"remindInstanceTask(task)\"")
                .contains("手动催办")
                .contains("@click=\"withdrawSelectedInstance()\"")
                .contains("@click=\"openInstanceQueryDialog('readRecords')\"");

        assertThat(script)
                .contains("selectInstance: function (row)")
                .contains("loadSelectedInstanceDetail: function (instanceId)")
                .contains("API_PATHS.instanceDetail(instanceId)")
                .contains("this.selectedInstanceDetail = enrichedDetail")
                .contains("activeTasks: normalizeList(tasksPayload)")
                .contains("activeTasks: function (instanceId)")
                .contains("remindTask: function (taskId)")
                .contains("\"/api/platform/tasks/\" + encodeURIComponent(taskId) + \"/remind\"")
                .contains("currentInstanceNodeCodes: function (row)")
                .contains("findInstanceActiveTaskByNode: function (tasks, nodeCode)")
                .contains("remindInstanceNode: function (row, nodeCode)")
                .contains("remindInstanceTask: function (task)")
                .contains("this.sendRequest(\"手动催办\", \"POST\", API_PATHS.remindTask(taskId), body)")
                .contains("historyTasks: function (instanceId)")
                .contains("comments: function (instanceId)")
                .contains("markRead: function (instanceId)")
                .contains("recordSelectedInstanceRead: function (instanceId)")
                .contains("canWithdrawSelectedInstance: function ()")
                .contains("recordTodoRowsRead: function (rows)")
                .doesNotContain("recoverSelectedInstance");
    }

    @Test
    void staticPageShouldExposeVue3FlowFunctionTestConsole() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String script = loadResource("/static/flow-test/flow-test-app.js");
        String css = loadResource("/static/flow-test/flow-test-app.css");

        assertThat(html)
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
                .contains("@keydown.delete=\"handleDesignerDelete\"")
                .contains("@click=\"startConnectionMode\"")
                .contains("data-testid=\"listener-reject-enabled\"")
                .contains("data-testid=\"listener-reject-targets\"")
                .contains("data-testid=\"listener-direct-send-enabled\"")
                .contains("data-testid=\"task-variables\"")
                .contains("data-testid=\"starter-resubmit\"")
                .contains("data-testid=\"direct-send\"")
                .contains("data-testid=\"replace-instance-attachment\"")
                .contains("data-testid=\"listener-config-json\"")
                .contains("class=\"canvas-surface\"")
                .contains(":viewBox=\"canvasViewBox(selectedGraphNodes, 480)\"")
                .contains(":viewBox=\"canvasViewBox(definitionDraft.nodes, 480)\"")
                .doesNotContain("data-testid=\"new-instance-attachment\"")
                .doesNotContain("/gray")
                .doesNotContain("businessKey: this.instanceForm.businessKey");

        assertThat(script)
                .contains("Vue.createApp")
                .contains("FLOW_TEST_USERS")
                .contains("currentUserId: \"u_admin_01\"")
                .contains("starterDeptId: \"mock-dept\"")
                .contains("FLOW_TEST_DEPARTMENTS")
                .contains("FLOW_TEST_ROLES")
                .contains("API_PATHS")
                .contains("\"/api/platform/definitions\"")
                .contains("publishDefinition: \"/api/platform/definitions/publish\"")
                .contains("activateDefinition: \"/api/platform/definitions/activate\"")
                .contains("deactivateDefinition: \"/api/platform/definitions/deactivate\"")
                .contains("archiveDefinition: \"/api/platform/definitions/archive\"")
                .contains("deleteDefinition: \"/api/platform/definitions\"")
                .contains("copyDefinition: function (definitionId)")
                .contains("\"/api/platform/definitions/\" + encodeURIComponent(definitionId) + \"/graph\"")
                .contains("\"/api/platform/definitions/\" + encodeURIComponent(definitionId) + \"/publish-validation\"")
                .contains("startAndSubmit: \"/api/platform/runtime/instances/start-submit\"")
                .contains("taskSubmit: \"/api/platform/runtime/tasks/submit\"")
                .contains("taskApprove: \"/api/platform/runtime/tasks/approve\"")
                .contains("taskReject: \"/api/platform/runtime/tasks/reject\"")
                .contains("taskDirectSend: \"/api/platform/runtime/tasks/direct-send\"")
                .contains("directSendContext: function (taskId)")
                .contains("replaceInstanceAttachment: function (instanceId, attachmentId)")
                .contains("\"/api/platform/tasks/completed\"")
                .contains("\"/api/platform/instances/started\"")
                .contains("\"/api/platform/attachments\"")
                .contains("\"/api/platform/attachment-templates\"")
                .contains("queryAttachmentTemplates")
                .contains("createAttachmentTemplate")
                .contains("selectExistingAttachmentTemplate")
                .contains("prepareCustomAttachmentTemplates")
                .contains("buildGraphRequest")
                .contains("isParallelGatewayNode: function (node)")
                .contains("parallelGatewayPairOptions: function (node)")
                .contains("copyDefinition: function (row)")
                .contains("CANVAS_MIN_WIDTH")
                .contains("canvasSurfaceStyle: function (nodes, minHeight)")
                .contains("canvasViewBox: function (nodes, minHeight)")
                .contains("positionX")
                .contains("positionY")
                .contains("connectionClickQueue")
                .contains("deleteSelectedDesignerItem")
                .contains("persistDefinitionDraftAfterDesignerDelete")
                .contains("buildUserApproverRule")
                .contains("normalizeMultiInstanceMode")
                .contains("startSubmitting")
                .contains("lastStartedInstanceId")
                .contains("focusCurrentStartedTodos")
                .contains("formatAttachmentSize")
                .contains("formatDateTime")
                .contains("sizeBytes: 0")
                .contains("content: \"\"")
                .contains("var user = this.currentUser()")
                .contains("userDepartmentId(user)")
                .contains("\"X-Flow-Dept-Id\": userDepartmentId(this.currentUser())")
                .contains("openTodoTaskDialog")
                .contains("loadTaskDialogContext: function (task)")
                .contains("Promise.all([")
                .contains("submitCurrentStarterTask")
                .contains("directSendCurrentTask")
                .contains("savePendingTaskAttachmentReplacements")
                .contains("replacement.saved = true")
                .contains("refreshAfterTaskAction")
                .contains("openCompletedTaskDialog")
                .contains("setOperationState")
                .contains("operationLogs")
                .doesNotContain("\"X-Flow-Dept-Name\"")
                .doesNotContain("\"/api/platform/definitions/\" + encodeURIComponent(definitionId) + \"/validate\"")
                .doesNotContain("\"/api/platform/instances/start-and-submit\"")
                .doesNotContain("handleTaskNewAttachmentFileChange")
                .doesNotContain("pendingAttachments");

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
                .contains(".active-task-list")
                .contains(".instance-node-list")
                .contains(".task-dialog")
                .contains(".status-bar")
                .contains(".operation-log")
                .contains(":focus-visible")
                .contains("@media (max-width: 960px)")
                .contains("overflow: auto")
                .doesNotContain("body {\n    overflow: hidden;");
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
