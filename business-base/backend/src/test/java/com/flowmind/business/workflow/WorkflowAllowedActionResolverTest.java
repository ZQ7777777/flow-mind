package com.flowmind.business.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkflowAllowedActionResolverTest {

    @Test
    void multiCandidateTaskRequiresClaimAndDisablesOtherActions() {
        WorkflowAllowedActionResolver resolver = new WorkflowAllowedActionResolver(
                new ObjectMapper(), mock(PlatformFacade.class));
        TaskDTO task = task(Arrays.asList("finance01", "finance02"));

        List<String> actions = resolver.resolve(task, definition(), Collections.emptyList(),
                Collections.emptyList(), "finance01");

        assertThat(actions).contains("CLAIM", "APPROVE", "TRANSFER", "DELEGATE", "ADD_SIGN");
        assertThat(resolver.resolveDisabled(task, actions, "finance01"))
                .contains("APPROVE", "TRANSFER", "DELEGATE", "ADD_SIGN")
                .doesNotContain("CLAIM");
    }

    @Test
    void singleCandidateTaskDoesNotShowClaim() {
        WorkflowAllowedActionResolver resolver = new WorkflowAllowedActionResolver(
                new ObjectMapper(), mock(PlatformFacade.class));

        List<String> actions = resolver.resolve(task(Collections.singletonList("finance01")), definition(),
                Collections.emptyList(), Collections.emptyList(), "finance01");

        assertThat(actions).contains("APPROVE").doesNotContain("CLAIM");
        assertThat(resolver.resolveDisabled(task(Collections.singletonList("finance01")), actions, "finance01"))
                .isEmpty();
    }

    private TaskDTO task(List<String> candidates) {
        TaskDTO task = new TaskDTO();
        task.setTaskId("task-1");
        task.setNodeCode("review");
        task.setTaskStatus(TaskStatusEnum.ACTIVE);
        task.setCandidateUserIds(candidates);
        return task;
    }

    private ProcessDefinitionDetailDTO definition() {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode("review");
        node.setApproverRuleType(ApproverRuleTypeEnum.USER);
        definition.setNodes(Collections.singletonList(node));
        return definition;
    }
}