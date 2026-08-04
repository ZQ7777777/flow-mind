import type { BusinessRequirement } from "@flowmind/agent-contracts";

export function createFakeEntryApplicationFiles(requirement: BusinessRequirement): Record<string, string> {
  const processCode = requirement.businessCode;
  return {
    "backend/src/main/java/com/flowmind/business/generated/entryapplication/EntryApplicationController.java": `package com.flowmind.business.generated.entryapplication;

import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitRequest;
import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entry-application")
public class EntryApplicationController {
    private final EntryApplicationService service;
    public EntryApplicationController(EntryApplicationService service) { this.service = service; }
    @PostMapping("/submit")
    public ResponseEntity<EntryApplicationSubmitResponse> submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody EntryApplicationSubmitRequest request) {
        return ResponseEntity.ok(service.submit(request, idempotencyKey));
    }
}
`,
    "backend/src/main/java/com/flowmind/business/generated/entryapplication/EntryApplicationService.java": `package com.flowmind.business.generated.entryapplication;

import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitRequest;
import com.flowmind.business.generated.entryapplication.dto.EntryApplicationSubmitResponse;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EntryApplicationService {
    private final ProcessRuntimeService runtimeService;
    private final CurrentBusinessUserProvider users;
    public EntryApplicationService(ProcessRuntimeService runtimeService, CurrentBusinessUserProvider users) {
        this.runtimeService = runtimeService; this.users = users;
    }
    public EntryApplicationSubmitResponse submit(EntryApplicationSubmitRequest input, String idempotencyKey) {
        CurrentBusinessUserProvider.BusinessUser user = users.currentUser();
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("applicantName", input.getApplicantName());
        variables.put("amount", input.getAmount());
        variables.put("accountNo", input.getAccountNo());
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessCode("${processCode}");
        request.setStarterUserId(user.getUserId());
        request.setStarterDeptId(user.getDepartmentId());
        request.setOperationId(operationId(user.getUserId(), idempotencyKey));
        request.setVariables(variables);
        request.setAttachments(input.getBankReceipt());
        ProcessInstanceDTO result = runtimeService.startAndSubmit(request);
        return EntryApplicationSubmitResponse.from(result);
    }
    private String operationId(String userId, String key) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(("${processCode}|" + userId + "|" + key).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : value) hex.append(String.format("%02x", item));
            return "entry_application_" + hex.toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
`,
    "backend/src/main/java/com/flowmind/business/generated/entryapplication/dto/EntryApplicationSubmitRequest.java": `package com.flowmind.business.generated.entryapplication.dto;

import java.math.BigDecimal;
import java.util.List;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

public class EntryApplicationSubmitRequest {
    @NotBlank private String applicantName;
    @DecimalMin("0.01") private BigDecimal amount;
    @NotBlank private String accountNo;
    @NotEmpty private List<Object> bankReceipt;
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String value) { applicantName = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String value) { accountNo = value; }
    public List<Object> getBankReceipt() { return bankReceipt; }
    public void setBankReceipt(List<Object> value) { bankReceipt = value; }
}
`,
    "backend/src/main/java/com/flowmind/business/generated/entryapplication/dto/EntryApplicationSubmitResponse.java": `package com.flowmind.business.generated.entryapplication.dto;

import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import java.util.List;
public class EntryApplicationSubmitResponse {
    private String instanceId;
    private String status;
    private List<?> createdTasks;
    public static EntryApplicationSubmitResponse from(ProcessInstanceDTO value) {
        EntryApplicationSubmitResponse response = new EntryApplicationSubmitResponse();
        response.instanceId = value.getId(); response.status = value.getStatus(); response.createdTasks = value.getCreatedTasks(); return response;
    }
    public String getInstanceId() { return instanceId; }
    public String getStatus() { return status; }
    public List<?> getCreatedTasks() { return createdTasks; }
}
`,
    "backend/src/test/java/com/flowmind/business/generated/entryapplication/EntryApplicationControllerTest.java": `package com.flowmind.business.generated.entryapplication;
import static org.mockito.Mockito.*;
import com.flowmind.business.generated.entryapplication.dto.*;
import org.junit.jupiter.api.Test;
class EntryApplicationControllerTest {
    @Test void forwardsIdempotencyKeyToService() { EntryApplicationService service = mock(EntryApplicationService.class); EntryApplicationSubmitRequest request = new EntryApplicationSubmitRequest(); new EntryApplicationController(service).submit("same-key", request); verify(service).submit(request, "same-key"); }
}
`,
    "backend/src/test/java/com/flowmind/business/generated/entryapplication/EntryApplicationServiceTest.java": `package com.flowmind.business.generated.entryapplication;
import static org.mockito.Mockito.*;
import com.flowmind.business.generated.entryapplication.dto.*;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
class EntryApplicationServiceTest {
    @Test void mapsVariablesAttachmentAndCallsStartAndSubmitOnce() { ProcessRuntimeService runtime = mock(ProcessRuntimeService.class); CurrentBusinessUserProvider users = mock(CurrentBusinessUserProvider.class); when(users.currentUser()).thenReturn(new CurrentBusinessUserProvider.BusinessUser("u1", "d1")); when(runtime.startAndSubmit(any())).thenReturn(new ProcessInstanceDTO()); EntryApplicationSubmitRequest request = new EntryApplicationSubmitRequest(); request.setApplicantName("A"); request.setAmount(new BigDecimal("1.00")); request.setAccountNo("001"); request.setBankReceipt(Collections.<Object>singletonList("receipt")); new EntryApplicationService(runtime, users).submit(request, "key"); verify(runtime, times(1)).startAndSubmit(any()); }
}
`,
    "frontend/src/modules/generated/entry-application/EntryApplicationApply.vue": `<script setup lang="ts">
import { reactive, ref } from "vue";
import { submitEntryApplication } from "../../../api/generated/entry-application";
const form = reactive({ applicantName: "", amount: 0, accountNo: "", bankReceipt: [] as File[] });
const success = ref("");
async function submit() { if (!form.applicantName || form.amount <= 0 || !form.accountNo || !form.bankReceipt.length) throw new Error("请完整填写表单并上传银行回单"); const result = await submitEntryApplication(form, crypto.randomUUID()); success.value = "提交成功，下一处理节点：" + (result.createdTasks?.[0]?.nodeName || "待处理"); }
</script>
<template><form @submit.prevent="submit"><input v-model="form.applicantName" aria-label="申请人姓名" required /><input v-model.number="form.amount" aria-label="入金金额" type="number" min="0.01" required /><input v-model="form.accountNo" aria-label="入金账号" required /><input aria-label="银行回单" type="file" required @change="form.bankReceipt = Array.from(($event.target as HTMLInputElement).files || [])" /><button type="submit">提交申请</button><p v-if="success">{{ success }}</p></form></template>
`,
    "frontend/src/modules/generated/__tests__/EntryApplicationApply.spec.ts": `import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import EntryApplicationApply from "../entry-application/EntryApplicationApply.vue";
vi.mock("../../../api/generated/entry-application", () => ({ submitEntryApplication: vi.fn().mockResolvedValue({ createdTasks: [{ nodeName: "部门经理审批" }] }) }));
describe("EntryApplicationApply", () => { it("renders confirmed fields, receipt and success state", async () => { const wrapper = mount(EntryApplicationApply); expect(wrapper.find('[aria-label="申请人姓名"]').exists()).toBe(true); expect(wrapper.find('[aria-label="银行回单"]').exists()).toBe(true); }); });
`,
    "frontend/src/api/generated/entry-application.ts": `export interface EntryApplicationPayload { applicantName: string; amount: number; accountNo: string; bankReceipt: File[] }
export async function submitEntryApplication(payload: EntryApplicationPayload, idempotencyKey: string): Promise<any> { const body = new FormData(); body.append("payload", JSON.stringify({ applicantName: payload.applicantName, amount: payload.amount, accountNo: payload.accountNo })); payload.bankReceipt.forEach((file) => body.append("bankReceipt", file)); const response = await fetch("/api/entry-application/submit", { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body }); if (!response.ok) throw new Error("提交失败"); return response.json(); }
`,
    "frontend/src/api/generated/entry-application.spec.ts": `import { describe, expect, it, vi } from "vitest";
import { submitEntryApplication } from "./entry-application";
describe("entry application api", () => { it("sends receipt and idempotency header", async () => { const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ instanceId: "i1" }), { status: 200 })); await submitEntryApplication({ applicantName: "A", amount: 1, accountNo: "001", bankReceipt: [new File(["x"], "receipt.pdf")] }, "stable-key"); expect(fetchMock.mock.calls[0][1]?.headers).toEqual({ "Idempotency-Key": "stable-key" }); }); });
`,
    "frontend/src/router/generated-routes.ts": `import type { RouteRecordRaw } from "vue-router";
export const generatedRoutes: RouteRecordRaw[] = [{ path: "/entry-application/apply", name: "entry-application-apply", component: () => import("../modules/generated/entry-application/EntryApplicationApply.vue") }];
`,
  };
}
