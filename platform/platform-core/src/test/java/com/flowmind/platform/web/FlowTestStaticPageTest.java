package com.flowmind.platform.web;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frontend flow operation test page static resource contract test.
 */
class FlowTestStaticPageTest {

    @Test
    void staticPageShouldExposeFlowConsoleDesignForM0ToM3() throws IOException {
        String html = loadResource("/static/flow-test/index.html");
        String normalizedHtml = html.replace("\r\n", "\n");
        String script = loadResource("/static/flow-test/flow-test-app.js");
        String css = loadResource("/static/flow-test/flow-test-app.css");
        int historyTaskButtonIndex = normalizedHtml.indexOf("@click=\"taskTab = 'history'; queryHistoryTasks()\"");
        int commentsButtonIndex = normalizedHtml.indexOf("<button type=\"button\" @click=\"queryComments\">", historyTaskButtonIndex);
        int tabRowEndIndex = normalizedHtml.indexOf("</div>", historyTaskButtonIndex);

        assertThat(html)
                .contains("Vue")
                .contains("id=\"app\"")
                .contains("flow-test-app.css?v=20260724-definition-dialog-fix")
                .contains("flow-test-app.js?v=20260724-definition-dialog-fix")
                .contains("<button type=\"button\" @click=\"activeSection = 'definitions'\">新建定义</button>")
                .contains("<button type=\"button\" class=\"primary\" @click=\"openDefinitionDialog\">新建定义</button>")
                .doesNotContain("<button type=\"button\" @click=\"openDefinitionDialog\">")
                .doesNotContain("<button type=\"button\" @click=\"saveGraph\">")
                .doesNotContain("flow-test.js")
                .doesNotContain("data-action")
                .doesNotContain("onclick")
                .contains("activeSection === 'home'")
                .contains("activeSection === 'definitions'")
                .contains("activeSection === 'instances'")
                .contains("activeSection === 'tasks'")
                .contains("activeSection === 'tracking'")
                .contains("activeSection === 'debug'")
                .contains("activeSection === 'logs'")
                .contains("@click=\"taskTab = 'todo'; queryTodoTasks()\"")
                .contains("@click=\"taskTab = 'done'; queryCompletedTasks()\"")
                .contains("@click=\"taskTab = 'started'; queryStartedInstances()\"")
                .contains("@click=\"taskTab = 'active'; queryActiveTasks()\"")
                .contains("@click=\"taskTab = 'history'; queryHistoryTasks()\"")
                .contains("@click=\"queryComments\"")
                .doesNotContain("@click=\"queryOperationLogs\"")
                .contains("@click=\"startAndSubmitInstance\"")
                .contains("selectedTaskFieldRows")
                .contains("@row-select=\"selectTaskRow\"")
                .contains("表单字段内容")
                .contains("readonly-field")
                .contains("{{ selectedTaskVersionText }}")
                .doesNotContain("instanceForm.businessKey")
                .doesNotContain("@click=\"submitApplyTask\"")
                .doesNotContain("v-model.number=\"taskForm.taskVersion\"")
                .doesNotContain("taskForm.fileName")
                .doesNotContain("taskForm.contentType")
                .doesNotContain("taskForm.fileSize")
                .doesNotContain("银行回单附件模板校验")
                .doesNotContain("<button type=\"button\" @click=\"queryTodoTasks\">")
                .doesNotContain("<button type=\"button\" @click=\"queryCompletedTasks\">")
                .doesNotContain("<button type=\"button\" @click=\"queryStartedInstances\">")
                .doesNotContain("<button type=\"button\" @click=\"queryActiveTasks\">")
                .doesNotContain("<button type=\"button\" @click=\"queryHistoryTasks\">")
                .contains("eventId");

        assertThat(historyTaskButtonIndex).isGreaterThanOrEqualTo(0);
        assertThat(commentsButtonIndex).isGreaterThan(historyTaskButtonIndex);
        assertThat(commentsButtonIndex).isLessThan(tabRowEndIndex);

        assertThat(script)
                .contains("createApp")
                .contains("/api/platform")
                .contains("FLOW_CONSOLE_SECTIONS")
                .contains("BANK_RECEIPT_TEMPLATE")
                .contains("validateBankReceiptAttachment")
                .contains("deleteInstanceChecks")
                .contains("definitionDraft")
                .contains("definitionRows")
                .contains("showDefinitionDialog")
                .contains("definitionConfigTab")
                .contains("instanceTitleTemplate")
                .contains("loadDepositTemplate")
                .contains("validateDefinitionDraft")
                .contains("confirmCreateAndSave")
                .contains("buildGraphRequestBody")
                .contains("definitionCreateSteps")
                .contains("selectNodeByCode")
                .contains("layoutDraftGraph")
                .contains("beginNodeDrag")
                .contains("dragDraftNode")
                .contains("finishNodeDrag")
                .contains("beginEdgeConnect")
                .contains("handleCanvasNodeClick")
                .contains("saveDraftGraphAfterCanvasChange")
                .contains("showToast")
                .contains("attachmentTemplateId: \"template-bank-receipt\"")
                .contains("attachmentCode: \"bankReceipt\"")
                .contains("minCount: 1")
                .contains("maxCount: 1")
                .contains("applicableNodeCodes: [\"APPLY\"]")
                .contains("contentType: \"application/pdf\"")
                .contains("queryDefinitions")
                .contains("queryInstances")
                .contains("buildStartAndSubmitBody")
                .contains("body.attachments = [this.buildAttachmentMeta()]")
                .contains("selectedTaskVariables")
                .contains("selectedTaskFieldRows")
                .contains("loadSelectedTaskVariables")
                .contains("applySelectedTaskVariables")
                .contains("this.instanceRows = normalizeList(payload)")
                .contains("operationId")
                .contains("expectedTaskVersion")
                .contains("return this.saveGraph().then")
                .contains("eventId")
                .contains("nodeName")
                .contains("/publish-validation")
                .contains("/definitions/publish")
                .contains("/definitions/activate")
                .contains("/definitions/deactivate")
                .contains("/definitions/archive")
                .contains("/runtime/instances/start")
                .contains("/runtime/instances/start-submit")
                .contains("/runtime/tasks/approve")
                .contains("/runtime/instances/variables")
                .contains("/runtime/instances/terminate")
                .contains("/runtime/instances")
                .contains("/callbacks/logs")
                .contains("/tasks/todo")
                .contains("/tasks/completed")
                .contains("/instances/started")
                .contains("/history-tasks")
                .contains("/comments")
                .contains("DELETE")
                .contains("Idempotency-Key")
                .contains("X-Flow-User-Id")
                .doesNotContain("实例列表接口当前代码暂未提供")
                .doesNotContain("businessKey")
                .doesNotContain("/runtime/tasks/submit")
                .doesNotContain("bankReceiptIssues")
                .doesNotContain("fileName: DEFAULT_ATTACHMENT_META.fileName")
                .doesNotContain("contentType: DEFAULT_ATTACHMENT_META.contentType")
                .doesNotContain("fileSize: DEFAULT_ATTACHMENT_META.fileSize")
                .doesNotContain("/admin/callback-logs")
                .doesNotContain("/admin/operation-logs")
                .doesNotContain("/gray");

        assertThat(css)
                .contains("grid-template-columns: 218px minmax(0, 1fr) minmax(300px, 360px)")
                .contains(".definition-workbench")
                .contains(".definition-basic-row")
                .contains(".definition-step-row")
                .contains(".definition-canvas-panel")
                .contains(".definition-canvas")
                .contains(".connector-svg")
                .contains(".designer-canvas")
                .contains(".designer-node")
                .contains(".readonly-field")
                .contains("@media (max-width: 1500px)")
                .contains("grid-column: 2")
                .contains("order: 3");

        assertThat(html)
                .contains("definition-workbench")
                .contains("definition-basic-row")
                .contains("definition-step-row")
                .contains("definition-canvas-panel")
                .contains("connector-svg")
                .contains("beginNodeDrag")
                .contains("finishNodeDrag")
                .contains("handleCanvasNodeClick")
                .contains("可视化流程设计器")
                .contains("实例标题模板")
                .contains("创建步骤")
                .contains("definitionConfigTab === 'fields'")
                .contains("definitionConfigTab === 'attachments'")
                .contains("definitionConfigTab === 'json'")
                .contains("layoutDraftGraph");
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
