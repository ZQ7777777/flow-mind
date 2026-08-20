package com.flowmind.business.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.business.BusinessBaseApplication;
import com.flowmind.business.workflow.WorkflowWithdrawContextResolver;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.DefinitionOperationRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BusinessBaseApplication.class,
        properties = "flow-mind.platform.sqlite.path=./target/b2-b3-e2e.db")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WorkflowB2B3IntegrationTest.AttachmentAccessTestConfiguration.class)
class WorkflowB2B3IntegrationTest {
    @Autowired private ProcessDefinitionService definitionService;
    @Autowired private ProcessRuntimeService runtimeService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void businessHallAndGenericStartApiCreateAndReplayARealInstance() throws Exception {
        MockHttpSession salesSession = login("sales01");
        bindSession(salesSession);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String processCode = "entry_hall_" + suffix;
        ProcessDefinitionDTO definition = createDefinition(processCode, suffix);

        MockHttpSession adminSession = login("admin01");
        mockMvc.perform(post("/api/admin/business-entry-configs")
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + definition.getId() + "\","
                                + "\"entryDisplayName\":\"大厅入金申请\","
                                + "\"entryPageUrl\":\"/generated/" + processCode + "/apply\","
                                + "\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entrySource").value("MANUAL"));

        mockMvc.perform(get("/api/workflow/process-entry-links").session(salesSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.definitionId == '" + definition.getId() + "')].entryDisplayName")
                        .value("大厅入金申请"));
        mockMvc.perform(get("/api/workflow/processes/{processCode}/start-context", processCode)
                        .session(salesSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startable").value(true))
                .andExpect(jsonPath("$.currentNodeCode").value("apply"))
                .andExpect(jsonPath("$.formFields.length()").value(3));

        String payloadJson = "{\"businessKey\":\"" + suffix.substring(0, 8) + "\",\"variables\":{"
                + "\"applicationNo\":\"" + suffix.substring(0, 8) + "\","
                + "\"amount\":1000,\"currency\":\"CNY\"}}";
        MockMultipartFile payload = new MockMultipartFile("payload", "payload.json",
                MediaType.APPLICATION_JSON_VALUE, payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String first = mockMvc.perform(multipart("/api/workflow/processes/{processCode}/start-submit", processCode)
                        .file(payload).session(salesSession).header("Idempotency-Key", "hall-start-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionId").value(definition.getId()))
                .andExpect(jsonPath("$.createdTasks[0].nodeCode").value("manager"))
                .andReturn().getResponse().getContentAsString();

        MockMultipartFile replayPayload = new MockMultipartFile("payload", "payload.json",
                MediaType.APPLICATION_JSON_VALUE, payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String replay = mockMvc.perform(multipart("/api/workflow/processes/{processCode}/start-submit", processCode)
                        .file(replayPayload).session(salesSession).header("Idempotency-Key", "hall-start-" + suffix))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replay).path("instanceId").asText())
                .isEqualTo(objectMapper.readTree(first).path("instanceId").asText());
    }

    @Test
    void generatedInitiationAndGenericActionsCompleteARealWorkflow() throws Exception {
        MockHttpSession salesSession = login("sales01");
        bindSession(salesSession);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String processCode = "entry_e2e_" + suffix;
        ProcessDefinitionDTO definition = createDefinition(processCode, suffix);
        ProcessInstanceDTO instance = start(processCode, suffix);

        MockHttpSession managerSession = login("manager_sales");
        TaskView manager = todo(managerSession, processCode);
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/approve", manager.taskId)
                        .session(managerSession).header("Idempotency-Key", "manager-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + manager.version + ",\"comment\":\"经理同意\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false));

        MockHttpSession financeSession = login("finance01");
        TaskView finance = todo(financeSession, processCode);
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/approve", finance.taskId)
                        .session(financeSession).header("Idempotency-Key", "finance-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + finance.version + ",\"comment\":\"财务确认\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance.instanceStatus").value("COMPLETED"));

        mockMvc.perform(get("/api/workflow/instances/{instanceId}", instance.getInstanceId()).session(salesSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definition.processCode").value(processCode))
                .andExpect(jsonPath("$.instance.variables.amount").value(1000))
                .andExpect(jsonPath("$.instance.variables.internalApprover").doesNotExist())
                .andExpect(jsonPath("$.historyTasks.length()").value(3))
                .andExpect(jsonPath("$.comments.length()").value(2));
        mockMvc.perform(get("/api/workflow/read-records").session(salesSession)
                        .param("instanceId", instance.getInstanceId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        assertThat(runtimeService.getInstance(instance.getInstanceId()).getDefinitionId()).isEqualTo(definition.getId());
    }

    @Test
    void agentGeneratedUppercaseRoleCodesCanSubmitApplication() throws Exception {
        MockHttpSession salesSession = login("sales01");
        bindSession(salesSession);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String processCode = "entry_agent_roles_" + suffix;
        createAgentRoleDefinition(processCode, suffix);
        start(processCode, suffix);

        MockHttpSession managerSession = login("manager_sales");
        TaskView manager = todo(managerSession, processCode);
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/approve", manager.taskId)
                        .session(managerSession).header("Idempotency-Key", "manager-role-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + manager.version + ",\"comment\":\"经理同意\"}"))
                .andExpect(status().isOk());

        MockHttpSession financeSession = login("finance01");
        todo(financeSession, processCode);
    }

    @Test
    void completedListWithdrawsToEditableApplyAndResubmitsVariables() throws Exception {
        MockHttpSession salesSession = login("sales01");
        bindSession(salesSession);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String processCode = "entry_withdraw_" + suffix;
        createDefinition(processCode, suffix);
        ProcessInstanceDTO started = start(processCode, suffix);

        String completedBody = mockMvc.perform(get("/api/workflow/tasks/completed")
                        .session(salesSession).param("processCode", processCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].withdrawContext.targetNodeCode").value("apply"))
                .andReturn().getResponse().getContentAsString();
        JsonNode withdrawContext = objectMapper.readTree(completedBody).path("records").get(0).path("withdrawContext");
        String managerTaskId = withdrawContext.path("taskId").asText();
        long managerTaskVersion = withdrawContext.path("expectedTaskVersion").asLong();

        mockMvc.perform(post("/api/workflow/tasks/{taskId}/withdraw", managerTaskId)
                        .session(salesSession).header("Idempotency-Key", "withdraw-conflict-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + (managerTaskVersion + 1) + "}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/workflow/tasks/{taskId}/withdraw", managerTaskId)
                        .session(salesSession).header("Idempotency-Key", "withdraw-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + managerTaskVersion + ",\"comment\":\"主动撤回\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTasks[0].nodeCode").value("apply"));

        mockMvc.perform(post("/api/workflow/tasks/{taskId}/withdraw", managerTaskId)
                        .session(salesSession).header("Idempotency-Key", "withdraw-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + managerTaskVersion
                                + ",\"comment\":\"主动撤回\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.createdTasks.length()").value(1));

        mockMvc.perform(get("/api/workflow/tasks/completed").session(salesSession)
                        .param("processCode", processCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].withdrawContext").doesNotExist());

        TaskView apply = todo(salesSession, processCode);
        mockMvc.perform(get("/api/workflow/tasks/{taskId}", apply.taskId).session(salesSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions").isArray())
                .andExpect(jsonPath("$.allowedActions[0]").value("SUBMIT"));
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/submit", apply.taskId)
                        .session(salesSession).header("Idempotency-Key", "resubmit-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + apply.version
                                + ",\"comment\":\"修改后重提\",\"variables\":{"
                                + "\"applicationNo\":\"" + suffix.substring(0, 8) + "\","
                                + "\"amount\":2000,\"currency\":\"CNY\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTasks[0].nodeCode").value("manager"));

        bindSession(salesSession);
        assertThat(runtimeService.getInstance(started.getInstanceId()).getVariables().get("amount"))
                .isEqualTo(2000);
    }

    @Test
    void warehousePledgeDefinitionRoutesReleaseAndLargePledgeBranchesToCompletion() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String processCode = "warehouse_pledge_" + suffix;
        createWarehouseDefinition(processCode, suffix);
        MockHttpSession salesSession = login("sales01");
        bindSession(salesSession);

        ProcessInstanceDTO release = startWarehouse(processCode, suffix + "r", "仓单解质押", false);
        assertNode(approve("manager_sales", processCode, "release-manager-" + suffix), "delivery_operation");
        assertNode(approve("delivery01", processCode, "release-operation-" + suffix), "delivery_review");
        approve("delivery04", processCode, "release-review-" + suffix);
        bindSession(salesSession);
        assertThat(runtimeService.getInstance(release.getInstanceId()).getInstanceStatus().name()).isEqualTo("COMPLETED");

        bindSession(salesSession);
        ProcessInstanceDTO pledge = startWarehouse(processCode, suffix + "p", "国债质押", true);
        assertNode(approve("manager_sales", processCode, "pledge-manager-" + suffix), "finance_operation");
        assertNode(approveClaimed("finance01", processCode, "pledge-finance-" + suffix), "delivery_confirm");
        assertNode(approve("delivery01", processCode, "pledge-confirm-" + suffix), "operations_leader_approve");
        assertNode(approve("manager_operations", processCode, "pledge-operations-" + suffix), "settlement_confirm");
        assertNode(approve("settlement01", processCode, "pledge-settlement-" + suffix), "delivery_operation");
        assertNode(approve("delivery01", processCode, "pledge-operation-" + suffix), "delivery_review");
        approve("delivery04", processCode, "pledge-review-" + suffix);
        bindSession(salesSession);
        assertThat(runtimeService.getInstance(pledge.getInstanceId()).getInstanceStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void warehousePledgeOrSignNodesCanBeWithdrawnByPreviousHandler() throws Exception {
        assertWarehouseOrSignWithdraw("delivery_confirm");
        assertWarehouseOrSignWithdraw("settlement_confirm");
        assertWarehouseOrSignWithdraw("delivery_operation");
        assertWarehouseOrSignWithdraw("delivery_review");
    }

    private void assertWarehouseOrSignWithdraw(String currentNodeCode) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String processCode = "ww_" + currentNodeCode.substring(0, 4) + "_" + suffix.substring(0, 20);
        createWarehouseDefinition(processCode, suffix);
        MockHttpSession salesSession = login("sales01");
        bindSession(salesSession);
        boolean pledgeBranch = "delivery_confirm".equals(currentNodeCode)
                || "settlement_confirm".equals(currentNodeCode);
        ProcessInstanceDTO instance = startWarehouse(processCode, suffix,
                pledgeBranch ? "国债质押" : "仓单解质押", pledgeBranch);

        String previousUser;
        String previousNode;
        assertNode(approve("manager_sales", processCode, "withdraw-manager-" + suffix),
                pledgeBranch ? "finance_operation" : "delivery_operation");
        if (pledgeBranch) {
            assertNode(approveClaimed("finance01", processCode, "withdraw-finance-" + suffix),
                    "delivery_confirm");
        }
        if ("delivery_confirm".equals(currentNodeCode)) {
            previousUser = "finance01";
            previousNode = "finance_operation";
        } else if ("settlement_confirm".equals(currentNodeCode)) {
            assertNode(approve("delivery01", processCode, "withdraw-confirm-" + suffix),
                    "operations_leader_approve");
            assertNode(approve("manager_operations", processCode, "withdraw-leader-" + suffix),
                    "settlement_confirm");
            previousUser = "manager_operations";
            previousNode = "operations_leader_approve";
        } else if ("delivery_operation".equals(currentNodeCode)) {
            previousUser = "manager_sales";
            previousNode = "supervisor_approve";
        } else {
            assertNode(approve("delivery01", processCode, "withdraw-operation-" + suffix),
                    "delivery_review");
            previousUser = "delivery01";
            previousNode = "delivery_operation";
        }

        MockHttpSession previousSession = login(previousUser);
        com.flowmind.platform.api.dto.ProcessInstanceDetailDTO beforeWithdraw =
                runtimeService.getInstance(instance.getInstanceId());
        assertThat(beforeWithdraw.getActiveTasks()).isNotEmpty();
        String activeGroupId = beforeWithdraw.getActiveTasks().get(0).getTaskGroupId();
        assertThat(activeGroupId).isNotBlank();
        assertThat(beforeWithdraw.getActiveTasks())
                .allMatch(task -> activeGroupId.equals(task.getTaskGroupId()));
        ProcessDefinitionDetailDTO beforeDefinition =
                definitionService.getDefinition(beforeWithdraw.getDefinitionId());
        assertThat(beforeWithdraw.getActiveTasks()).allMatch(task -> task.getTaskVersion() != null
                && task.getTaskStatus() != null && task.getBranchKey() == null
                && currentNodeCode.equals(task.getNodeCode()));
        assertThat(beforeDefinition.getNodes()).filteredOn(node -> currentNodeCode.equals(node.getNodeCode()))
                .extracting(ProcessNodeDTO::getMultiInstanceMode).containsExactly(MultiInstanceModeEnum.OR_SIGN);
        String previousUserId = "finance01".equals(previousUser) ? "u_finance_01"
                        : ("manager_sales".equals(previousUser) ? "u_dept_manager_01"
                        : ("manager_operations".equals(previousUser) ? "u_operations_manager_01"
                        : "u_delivery_01"));
        assertThat(beforeWithdraw.getHistoryTasks()).anyMatch(history -> previousUserId.equals(history.getAssigneeUserId())
                && previousNode.equals(history.getNodeCode()));
        assertThat(new WorkflowWithdrawContextResolver().resolve(beforeWithdraw,
                beforeDefinition, previousUserId)).isNotNull();
        String completedBody = mockMvc.perform(get("/api/workflow/tasks/completed")
                        .session(previousSession).param("processCode", processCode))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode context = null;
        for (JsonNode record : objectMapper.readTree(completedBody).path("records")) {
            if (!record.path("withdrawContext").isMissingNode()
                    && !record.path("withdrawContext").isNull()) {
                context = record.path("withdrawContext");
                break;
            }
        }
        assertThat(context).isNotNull();
        assertThat(context.path("targetNodeCode").asText()).isEqualTo(previousNode);
        String taskId = context.path("taskId").asText();
        long taskVersion = context.path("expectedTaskVersion").asLong();

        String withdrawBody = mockMvc.perform(post("/api/workflow/tasks/{taskId}/withdraw", taskId)
                        .session(previousSession).header("Idempotency-Key", "withdraw-or-sign-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + taskVersion + ",\"comment\":\"撤回或签节点\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTasks[0].nodeCode").value(previousNode))
                .andReturn().getResponse().getContentAsString();
        JsonNode archived = objectMapper.readTree(withdrawBody).path("archivedTasks");
        int withdrawCount = 0;
        int cancelCount = 0;
        for (JsonNode history : archived) {
            if ("WITHDRAW".equals(history.path("actionType").asText())) withdrawCount++;
            if ("CANCEL".equals(history.path("actionType").asText())) cancelCount++;
        }
        assertThat(withdrawCount).isEqualTo(1);
        assertThat(cancelCount).isGreaterThanOrEqualTo(1);
        bindSession(previousSession);
        assertThat(runtimeService.getInstance(instance.getInstanceId()).getActiveTasks())
                .isNotEmpty().allMatch(task -> previousNode.equals(task.getNodeCode()));
    }

    private TaskView approve(String username, String processCode, String idempotencyKey) throws Exception {
        MockHttpSession session = login(username);
        TaskView task = todo(session, processCode);
        return approve(session, task, idempotencyKey);
    }

    private TaskView approveClaimed(String username, String processCode, String idempotencyKey) throws Exception {
        MockHttpSession session = login(username);
        TaskView task = todo(session, processCode);
        mockMvc.perform(post("/api/workflow/tasks/{taskId}/claim", task.taskId)
                        .session(session).header("Idempotency-Key", idempotencyKey + "-claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + task.version + ",\"comment\":\"认领\"}"))
                .andExpect(status().isOk());
        return approve(session, todo(session, processCode), idempotencyKey);
    }

    private TaskView approve(MockHttpSession session, TaskView task, String idempotencyKey) throws Exception {
        String body = mockMvc.perform(post("/api/workflow/tasks/{taskId}/approve", task.taskId)
                        .session(session).header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":" + task.version + ",\"comment\":\"同意\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body).path("createdTasks");
        return created.isArray() && created.size() > 0
                ? new TaskView(created.get(0).path("taskId").asText(),
                created.get(0).path("taskVersion").asLong(), created.get(0).path("nodeCode").asText())
                : new TaskView("", 0L, "");
    }

    private void assertNode(TaskView task, String expectedNodeCode) {
        assertThat(task.nodeCode).isEqualTo(expectedNodeCode);
    }

    private TaskView todo(MockHttpSession session, String processCode) throws Exception {
        String body = mockMvc.perform(get("/api/workflow/tasks/todo")
                        .session(session).param("processCode", processCode).param("source", "ALL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode task = objectMapper.readTree(body).path("records").get(0);
        return new TaskView(task.path("taskId").asText(), task.path("taskVersion").asLong(),
                task.path("nodeCode").asText());
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private void bindSession(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ProcessDefinitionDTO createDefinition(String processCode, String suffix) {
        CreateProcessDefinitionRequest create = new CreateProcessDefinitionRequest();
        create.setOperationId("create-" + suffix); create.setOperatorUserId("u_sales_01");
        create.setProcessCode(processCode); create.setProcessName("入金申请端到端"); create.setSystemCode("business-base");
        ProcessDefinitionDTO definition = definitionService.createDefinition(create);
        SaveProcessGraphRequest graph = new SaveProcessGraphRequest();
        graph.setOperationId("graph-" + suffix); graph.setOperatorUserId("u_sales_01");
        graph.setNodes(Arrays.asList(
                node("start", "开始", NodeTypeEnum.START, null, null, 10),
                node("apply", "申请", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER, null, 20),
                node("manager", "经理审批", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER, "u_dept_manager_01", 30),
                node("finance", "财务确认", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.USER, "u_finance_01", 40),
                node("end", "结束", NodeTypeEnum.END, null, null, 50)));
        graph.setEdges(Arrays.asList(edge("e1", "start", "apply", 10), edge("e2", "apply", "manager", 20),
                edge("e3", "manager", "finance", 30), edge("e4", "finance", "end", 40)));
        graph.setFormFields(Arrays.asList(field("applicationNo", "申请编号", "string", 10),
                field("amount", "金额", "number", 20), field("currency", "币种", "string", 30)));
        graph.setAttachmentConfigs(Collections.emptyList());
        definitionService.saveGraph(definition.getId(), graph);
        definitionService.publish(lifecycle(definition.getId(), "publish-" + suffix));
        definitionService.activate(lifecycle(definition.getId(), "activate-" + suffix));
        return definition;
    }

    private ProcessDefinitionDTO createAgentRoleDefinition(String processCode, String suffix) {
        CreateProcessDefinitionRequest create = new CreateProcessDefinitionRequest();
        create.setOperationId("create-agent-role-" + suffix); create.setOperatorUserId("u_sales_01");
        create.setProcessCode(processCode); create.setProcessName("Agent 角色码入金申请"); create.setSystemCode("business-base");
        ProcessDefinitionDTO definition = definitionService.createDefinition(create);
        SaveProcessGraphRequest graph = new SaveProcessGraphRequest();
        graph.setOperationId("graph-agent-role-" + suffix); graph.setOperatorUserId("u_sales_01");
        graph.setNodes(Arrays.asList(
                node("start", "开始", NodeTypeEnum.START, null, null, 10),
                node("apply", "申请", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER, null, 20),
                ruleNode("dept_approve", "部门经理审批", ApproverRuleTypeEnum.ROLE_IN_DEPARTMENT,
                        "{\"roleCode\":\"DEPARTMENT_MANAGER\",\"departmentFrom\":\"starter\"}", 30),
                ruleNode("finance_confirm", "财务确认", ApproverRuleTypeEnum.ROLE,
                        "{\"roleCode\":\"FINANCE\"}", 40),
                node("end", "结束", NodeTypeEnum.END, null, null, 50)));
        graph.setEdges(Arrays.asList(edge("e1", "start", "apply", 10), edge("e2", "apply", "dept_approve", 20),
                edge("e3", "dept_approve", "finance_confirm", 30), edge("e4", "finance_confirm", "end", 40)));
        graph.setFormFields(Arrays.asList(field("applicationNo", "申请编号", "string", 10),
                field("amount", "金额", "number", 20), field("currency", "币种", "string", 30)));
        graph.setAttachmentConfigs(Collections.emptyList());
        definitionService.saveGraph(definition.getId(), graph);
        definitionService.publish(lifecycle(definition.getId(), "publish-agent-role-" + suffix));
        definitionService.activate(lifecycle(definition.getId(), "activate-agent-role-" + suffix));
        return definition;
    }

    private ProcessDefinitionDTO createWarehouseDefinition(String processCode, String suffix) {
        CreateProcessDefinitionRequest create = new CreateProcessDefinitionRequest();
        create.setOperationId("create-warehouse-" + suffix); create.setOperatorUserId("u_admin_01");
        create.setProcessCode(processCode); create.setProcessName("仓单、国债（解）质押申请");
        create.setSystemCode("business-base");
        ProcessDefinitionDTO definition = definitionService.createDefinition(create);
        SaveProcessGraphRequest graph = new SaveProcessGraphRequest();
        graph.setOperationId("graph-warehouse-" + suffix); graph.setOperatorUserId("u_admin_01");
        graph.setNodes(Arrays.asList(
                node("start", "开始", NodeTypeEnum.START, null, null, 10),
                node("apply", "经办发起", NodeTypeEnum.USER_TASK, ApproverRuleTypeEnum.STARTER, null, 20),
                ruleNode("supervisor_approve", "上级审批", ApproverRuleTypeEnum.ROLE_IN_DEPARTMENT,
                        "{\"roleCode\":\"department_manager\",\"departmentFrom\":\"starter\"}", 30),
                node("amount_gateway", "金额分支", NodeTypeEnum.EXCLUSIVE_GATEWAY, null, null, 40),
                ruleNode("finance_operation", "财务操作", ApproverRuleTypeEnum.ROLE,
                        "{\"roleCode\":\"finance\"}", 50),
                node("business_type_gateway", "业务类型分支", NodeTypeEnum.EXCLUSIVE_GATEWAY, null, null, 60),
                orSign(ruleNode("delivery_confirm", "交割确认", ApproverRuleTypeEnum.DEPARTMENT,
                        "{\"departmentId\":\"dept_delivery\"}", 70)),
                ruleNode("operations_leader_approve", "运营中心分管领导审批", ApproverRuleTypeEnum.USER,
                        "{\"userIds\":[\"u_operations_manager_01\"]}", 80),
                orSign(ruleNode("settlement_confirm", "结算确认", ApproverRuleTypeEnum.DEPARTMENT,
                        "{\"departmentId\":\"dept_settlement\"}", 90)),
                orSign(ruleNode("delivery_operation", "交割操作", ApproverRuleTypeEnum.USER,
                        "{\"userIds\":[\"u_delivery_01\",\"u_delivery_02\",\"u_delivery_03\"]}", 100)),
                orSign(ruleNode("delivery_review", "交割复核", ApproverRuleTypeEnum.USER,
                        "{\"userIds\":[\"u_delivery_04\",\"u_delivery_05\"]}", 110)),
                noticeNode("notify_starter", "知会经办", 120),
                node("end", "结束", NodeTypeEnum.END, null, null, 130)));
        graph.setEdges(Arrays.asList(
                edge("e01", "start", "apply", 10), edge("e02", "apply", "supervisor_approve", 20),
                edge("e03", "supervisor_approve", "amount_gateway", 30),
                conditionalEdge("e04", "amount_gateway", "finance_operation", "largeAmount == true", false, 40),
                conditionalEdge("e05", "amount_gateway", "business_type_gateway", null, true, 50),
                edge("e06", "finance_operation", "business_type_gateway", 60),
                conditionalEdge("e07", "business_type_gateway", "delivery_confirm", "businessType == \"仓单质押\"", false, 70),
                conditionalEdge("e08", "business_type_gateway", "delivery_confirm", "businessType == \"国债质押\"", false, 80),
                conditionalEdge("e09", "business_type_gateway", "delivery_operation", null, true, 90),
                edge("e10", "delivery_confirm", "operations_leader_approve", 100),
                edge("e11", "operations_leader_approve", "settlement_confirm", 110),
                edge("e12", "settlement_confirm", "delivery_operation", 120),
                edge("e13", "delivery_operation", "delivery_review", 130),
                edge("e14", "delivery_review", "notify_starter", 140),
                edge("e15", "notify_starter", "end", 150)));
        graph.setFormFields(Arrays.asList(field("businessType", "业务类型", "select", 10),
                field("largeAmount", "大额标记", "boolean", 20)));
        graph.setAttachmentConfigs(Collections.emptyList());
        definitionService.saveGraph(definition.getId(), graph);
        assertThat(definitionService.validateForPublish(definition.getId()).isValid()).isTrue();
        definitionService.publish(lifecycle(definition.getId(), "publish-warehouse-" + suffix));
        definitionService.activate(lifecycle(definition.getId(), "activate-warehouse-" + suffix));
        return definition;
    }

    private ProcessInstanceDTO startWarehouse(String processCode, String suffix, String businessType,
                                               boolean largeAmount) {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId("start-warehouse-" + suffix); request.setProcessCode(processCode);
        request.setStarterUserId("u_sales_01"); request.setStarterDeptId("dept_sales");
        request.setInstanceTitle("质押申请 " + suffix.substring(0, 6));
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("businessType", businessType); variables.put("largeAmount", Boolean.valueOf(largeAmount));
        request.setVariables(variables);
        return runtimeService.startAndSubmit(request);
    }

    private ProcessInstanceDTO start(String processCode, String suffix) {
        StartProcessRequest request = new StartProcessRequest();
        request.setOperationId("start-" + suffix); request.setProcessCode(processCode);
        request.setStarterUserId("u_sales_01"); request.setStarterDeptId("dept_sales");
        request.setInstanceTitle("入金申请 " + suffix.substring(0, 6));
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("applicationNo", suffix.substring(0, 8)); variables.put("amount", 1000);
        variables.put("currency", "CNY"); variables.put("internalApprover", "must-not-leak");
        request.setVariables(variables);
        return runtimeService.startAndSubmit(request);
    }

    private ProcessNodeDTO node(String code, String name, NodeTypeEnum type,
                                ApproverRuleTypeEnum rule, String userId, int order) {
        ProcessNodeDTO node = new ProcessNodeDTO(); node.setNodeCode(code); node.setNodeName(name);
        node.setNodeType(type); node.setSortOrder(order);
        if (NodeTypeEnum.USER_TASK.equals(type)) {
            node.setApproverRuleType(rule); node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
            if (userId != null) node.setApproverRuleConfig("{\"userIds\":[\"" + userId + "\"]}");
        }
        return node;
    }

    private ProcessNodeDTO ruleNode(String code, String name, ApproverRuleTypeEnum rule, String config, int order) {
        ProcessNodeDTO node = node(code, name, NodeTypeEnum.USER_TASK, rule, null, order);
        node.setApproverRuleConfig(config);
        return node;
    }

    private ProcessNodeDTO orSign(ProcessNodeDTO node) {
        node.setMultiInstanceMode(MultiInstanceModeEnum.OR_SIGN); return node;
    }

    private ProcessNodeDTO noticeNode(String code, String name, int order) {
        ProcessNodeDTO node = node(code, name, NodeTypeEnum.NOTICE, ApproverRuleTypeEnum.STARTER, null, order);
        node.setApproverRuleType(ApproverRuleTypeEnum.STARTER);
        node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        node.setApproverRuleConfig("{}");
        node.setNoticeConfig("{\"title\":\"流程知会\",\"content\":\"申请已办理完成\"}");
        return node;
    }

    private ProcessEdgeDTO edge(String code, String source, String target, int order) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO(); edge.setEdgeCode(code); edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target); edge.setDefaultEdge(Boolean.FALSE); edge.setSortOrder(order); return edge;
    }

    private ProcessEdgeDTO conditionalEdge(String code, String source, String target, String condition,
                                            boolean defaultEdge, int order) {
        ProcessEdgeDTO edge = edge(code, source, target, order);
        edge.setConditionExpression(condition); edge.setDefaultEdge(Boolean.valueOf(defaultEdge)); return edge;
    }

    private ProcessFormFieldDTO field(String code, String name, String type, int order) {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO(); field.setFieldCode(code); field.setFieldName(name);
        field.setFieldType(type);
        field.setControlType("number".equals(type) ? "number"
                : ("select".equals(type) ? "select" : ("boolean".equals(type) ? "checkbox" : "input")));
        field.setRequired(Boolean.TRUE); field.setSortOrder(order); return field;
    }

    private DefinitionOperationRequest lifecycle(String definitionId, String operationId) {
        DefinitionOperationRequest request = new DefinitionOperationRequest(); request.setDefinitionId(definitionId);
        request.setOperationId(operationId); request.setOperatorUserId("u_sales_01"); return request;
    }

    private static final class TaskView {
        private final String taskId; private final long version; private final String nodeCode;
        private TaskView(String taskId, long version, String nodeCode) {
            this.taskId = taskId; this.version = version; this.nodeCode = nodeCode;
        }
    }

    @TestConfiguration
    static class AttachmentAccessTestConfiguration {
        @Bean
        @Primary
        AttachmentAccessProvider attachmentAccessProvider() {
            return request -> true;
        }
    }
}
