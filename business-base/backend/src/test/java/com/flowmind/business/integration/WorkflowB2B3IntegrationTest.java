package com.flowmind.business.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.business.BusinessBaseApplication;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private TaskView todo(MockHttpSession session, String processCode) throws Exception {
        String body = mockMvc.perform(get("/api/workflow/tasks/todo")
                        .session(session).param("processCode", processCode).param("source", "ALL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode task = objectMapper.readTree(body).path("records").get(0);
        return new TaskView(task.path("taskId").asText(), task.path("taskVersion").asLong());
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

    private ProcessEdgeDTO edge(String code, String source, String target, int order) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO(); edge.setEdgeCode(code); edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target); edge.setDefaultEdge(Boolean.FALSE); edge.setSortOrder(order); return edge;
    }

    private ProcessFormFieldDTO field(String code, String name, String type, int order) {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO(); field.setFieldCode(code); field.setFieldName(name);
        field.setFieldType(type); field.setControlType("number".equals(type) ? "number" : "input");
        field.setRequired(Boolean.TRUE); field.setSortOrder(order); return field;
    }

    private DefinitionOperationRequest lifecycle(String definitionId, String operationId) {
        DefinitionOperationRequest request = new DefinitionOperationRequest(); request.setDefinitionId(definitionId);
        request.setOperationId(operationId); request.setOperatorUserId("u_sales_01"); return request;
    }

    private static final class TaskView {
        private final String taskId; private final long version;
        private TaskView(String taskId, long version) { this.taskId = taskId; this.version = version; }
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
