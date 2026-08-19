import type {
  BusinessRequirement,
  FormFieldRequirement,
  GenerationTargetContract,
} from "@flowmind/agent-contracts";
import type { GenerationSpec } from "../generation/generation-spec.js";

/** Deterministic test/demo substitute. Production generation remains LLM-driven. */
export function createFakeGenerationFiles(
  requirement: BusinessRequirement,
  spec: GenerationSpec,
  contract: GenerationTargetContract,
  existingRouteRegistry: string,
): Record<string, string> {
  const fields = [...requirement.formFields].sort((left, right) => left.sortOrder - right.sortOrder);
  if ((contract as { generationMode?: string }).generationMode === "FRONTEND_FORM_ONLY") {
    return {
      [spec.paths.businessForm]: businessFormSource(requirement, fields),
      [spec.paths.businessFormTest]: businessFormTestSource(spec),
      [spec.paths.applyView]: applyShellSource(spec),
      [spec.paths.applyViewTest]: applyShellTestSource(spec),
      [spec.paths.routeRegistry]: mergeGeneratedRoute(existingRouteRegistry, spec),
    };
  }
  const backend = contract.backend!;
  const accessorImport = backend.trustedUserContext.accessorType;
  const accessorType = simpleName(accessorImport);
  const accessorMethod = backend.trustedUserContext.accessorMethod;
  const userIdGetter = getter(backend.trustedUserContext.userIdProperty);
  const departmentIdGetter = getter(backend.trustedUserContext.departmentIdProperty);
  const formType = `${spec.classPrefix}Payload`;
  const submitFunction = `submit${spec.classPrefix}`;

  return {
    [spec.paths.controller]: controllerSource(spec),
    [spec.paths.service]: serviceSource(spec, fields, accessorImport, accessorMethod, userIdGetter, departmentIdGetter),
    [spec.paths.requestDto]: requestSource(spec, fields),
    [spec.paths.responseDto]: responseSource(spec),
    [spec.paths.controllerTest]: controllerTestSource(spec),
    [spec.paths.serviceTest]: serviceTestSource(spec, fields, accessorImport, accessorMethod),
    [spec.paths.view]: viewSource(spec, fields, formType, submitFunction),
    [spec.paths.viewTest]: viewTestSource(spec, fields),
    [spec.paths.api]: apiSource(spec, fields, formType, submitFunction),
    [spec.paths.apiTest]: apiTestSource(spec, fields, submitFunction),
    [spec.paths.routeRegistry]: mergeGeneratedRoute(existingRouteRegistry, spec),
  };
}

function controllerSource(spec: GenerationSpec): string {
  return `package ${spec.javaPackage};

import ${spec.javaPackage}.dto.${spec.classPrefix}SubmitRequest;
import ${spec.javaPackage}.dto.${spec.classPrefix}SubmitResponse;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/generated/${spec.kebabCode}")
public class ${spec.classPrefix}Controller {
    private final ${spec.classPrefix}Service service;

    public ${spec.classPrefix}Controller(${spec.classPrefix}Service service) { this.service = service; }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<${spec.classPrefix}SubmitResponse> submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("payload") @Valid ${spec.classPrefix}SubmitRequest request,
            @RequestParam MultiValueMap<String, MultipartFile> files) {
        return ResponseEntity.ok(service.submit(request, files, idempotencyKey));
    }
}
`;
}

function serviceSource(
  spec: GenerationSpec,
  fields: FormFieldRequirement[],
  accessorImport: string,
  accessorMethod: string,
  userIdGetter: string,
  departmentIdGetter: string,
): string {
  const accessorType = simpleName(accessorImport);
  const variables = fields.map((field) => `        variables.put("${field.fieldCode}", input.${getter(field.fieldCode)}());`).join("\n");
  const applicationNo = fields.find((field) => field.required && field.fieldCode === "applicationNo");
  const instanceTitle = applicationNo
    ? `"${escapeJava(spec.businessName)} - " + input.${getter(applicationNo.fieldCode)}()`
    : `"${escapeJava(spec.businessName)}"`;
  const attachmentBlocks = spec.applyAttachments.map((attachment) => `
        java.util.List<MultipartFile> ${attachment.attachmentCode}Files = files.get("${attachment.attachmentCode}");
        int ${attachment.attachmentCode}Count = ${attachment.attachmentCode}Files == null ? 0 : ${attachment.attachmentCode}Files.size();
        if (${attachment.attachmentCode}Count < ${attachment.minCount} || ${attachment.attachmentCode}Count > ${attachment.maxCount}) {
            throw new IllegalArgumentException("${escapeJava(attachment.attachmentName)}文件数量不符合要求");
        }
        if (${attachment.attachmentCode}Files != null) {
            for (MultipartFile file : ${attachment.attachmentCode}Files) {
                if (file.getSize() > ${attachment.maxSizeBytes}L) throw new IllegalArgumentException("${escapeJava(attachment.attachmentName)}文件过大");
                ${extensionValidation(attachment.attachmentName, attachment.allowedExtensions)}
                AttachmentUploadItem item = new AttachmentUploadItem();
                item.setAttachmentCode("${attachment.attachmentCode}");
                item.setOwnerType(AttachmentOwnerTypeEnum.INSTANCE);
                item.setFileName(file.getOriginalFilename());
                item.setContentType(file.getContentType());
                item.setSizeBytes(file.getSize());
                try { item.setContent(file.getBytes()); } catch (java.io.IOException error) { throw new IllegalArgumentException("附件读取失败", error); }
                attachments.add(item);
            }
        }`).join("\n");
  return `package ${spec.javaPackage};

import ${spec.javaPackage}.dto.${spec.classPrefix}SubmitRequest;
import ${spec.javaPackage}.dto.${spec.classPrefix}SubmitResponse;
import ${accessorImport};
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ${spec.classPrefix}Service {
    private final ProcessRuntimeService runtimeService;
    private final ${accessorType} users;

    public ${spec.classPrefix}Service(ProcessRuntimeService runtimeService, ${accessorType} users) {
        this.runtimeService = runtimeService;
        this.users = users;
    }

    public ${spec.classPrefix}SubmitResponse submit(${spec.classPrefix}SubmitRequest input,
            MultiValueMap<String, MultipartFile> files, String idempotencyKey) {
        ${accessorType}.BusinessUser user = users.${accessorMethod}();
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
${variables || "        // This confirmed process has no initiation form fields."}
        List<AttachmentUploadItem> attachments = new ArrayList<AttachmentUploadItem>();${attachmentBlocks}
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessCode("${escapeJava(spec.processCode)}");
        request.setInstanceTitle(${instanceTitle});
        request.setStarterUserId(user.${userIdGetter}());
        request.setStarterDeptId(user.${departmentIdGetter}());
        request.setOperationId(operationId(user.${userIdGetter}(), idempotencyKey));
        request.setVariables(variables);
        request.setAttachments(attachments);
        ProcessInstanceDTO result = runtimeService.startAndSubmit(request);
        return ${spec.classPrefix}SubmitResponse.from(result);
    }

    private String operationId(String userId, String key) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest(("${escapeJava(spec.processCode)}|" + userId + "|" + key).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : value) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
`;
}

function requestSource(spec: GenerationSpec, fields: FormFieldRequirement[]): string {
  const declarations = fields.map((field) => `${validationAnnotations(field)}    private ${javaType(field)} ${field.fieldCode};`).join("\n");
  const accessors = fields.map((field) => `    public ${javaType(field)} ${getter(field.fieldCode)}() { return ${field.fieldCode}; }
    public void ${setter(field.fieldCode)}(${javaType(field)} value) { ${field.fieldCode} = value; }`).join("\n");
  return `package ${spec.javaPackage}.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ${spec.classPrefix}SubmitRequest {
${declarations || "    // This confirmed process has no initiation form fields."}
${accessors}
}
`;
}

function responseSource(spec: GenerationSpec): string {
  return `package ${spec.javaPackage}.dto;

import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ${spec.classPrefix}SubmitResponse {
    private String instanceId;
    private String status;
    private List<CreatedTask> createdTasks;

    public static ${spec.classPrefix}SubmitResponse from(ProcessInstanceDTO value) {
        ${spec.classPrefix}SubmitResponse response = new ${spec.classPrefix}SubmitResponse();
        response.instanceId = value.getInstanceId();
        response.status = String.valueOf(value.getInstanceStatus());
        response.createdTasks = mapCreatedTasks(value.getCreatedTasks());
        return response;
    }

    private static List<CreatedTask> mapCreatedTasks(List<TaskDTO> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<CreatedTask> result = new ArrayList<CreatedTask>(source.size());
        for (TaskDTO task : source) {
            result.add(new CreatedTask(task.getTaskId(), task.getNodeCode(), task.getNodeName()));
        }
        return result;
    }

    public String getInstanceId() { return instanceId; }
    public String getStatus() { return status; }
    public List<CreatedTask> getCreatedTasks() { return createdTasks; }

    public static class CreatedTask {
        private final String taskId;
        private final String nodeCode;
        private final String taskName;
        public CreatedTask(String taskId, String nodeCode, String taskName) {
            this.taskId = taskId;
            this.nodeCode = nodeCode;
            this.taskName = taskName;
        }
        public String getTaskId() { return taskId; }
        public String getNodeCode() { return nodeCode; }
        public String getTaskName() { return taskName; }
    }
}
`;
}

function controllerTestSource(spec: GenerationSpec): string {
  return `package ${spec.javaPackage};
import static org.mockito.Mockito.*;
import ${spec.javaPackage}.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
class ${spec.classPrefix}ControllerTest {
    @Test void forwardsIdempotencyKeyToService() {
        ${spec.classPrefix}Service service = mock(${spec.classPrefix}Service.class);
        ${spec.classPrefix}SubmitRequest request = new ${spec.classPrefix}SubmitRequest();
        new ${spec.classPrefix}Controller(service).submit("same-key", request, new LinkedMultiValueMap<>());
        verify(service).submit(eq(request), any(), eq("same-key"));
    }
}
`;
}

function serviceTestSource(spec: GenerationSpec, fields: FormFieldRequirement[], accessorImport: string, accessorMethod: string): string {
  const accessorType = simpleName(accessorImport);
  const firstField = fields[0];
  const applicationNo = fields.find((field) => field.required && field.fieldCode === "applicationNo");
  const setterLines = [
    firstField ? `request.${setter(firstField.fieldCode)}(${javaTestValue(firstField)});` : "",
    applicationNo && applicationNo !== firstField
      ? `request.${setter(applicationNo.fieldCode)}(${javaTestValue(applicationNo)});`
      : "",
  ].filter(Boolean).join("\n        ");
  const expectedInstanceTitle = applicationNo
    ? `${escapeJava(spec.businessName)} - ${javaTestValue(applicationNo).replace(/^"|"$/g, "")}`
    : escapeJava(spec.businessName);
  const attachmentSetup = spec.applyAttachments.map((attachment) =>
    `        files.add("${escapeJava(attachment.attachmentCode)}", new MockMultipartFile("${escapeJava(attachment.attachmentCode)}", "proof.pdf", "application/pdf", new byte[] { 1 }));`,
  ).join("\n");
  return `package ${spec.javaPackage};
import static org.mockito.Mockito.*;
import ${spec.javaPackage}.dto.*;
import ${accessorImport};
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;
class ${spec.classPrefix}ServiceTest {
    @Test void mapsConfirmedRequirementAndCallsStartAndSubmitOnce() {
        ProcessRuntimeService runtime = mock(ProcessRuntimeService.class);
        ${accessorType} users = mock(${accessorType}.class);
        when(users.${accessorMethod}()).thenReturn(new ${accessorType}.BusinessUser("u1", "d1"));
        when(runtime.startAndSubmit(any())).thenReturn(new ProcessInstanceDTO());
        ${spec.classPrefix}SubmitRequest request = new ${spec.classPrefix}SubmitRequest();
        ${setterLines}
        LinkedMultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<>();
${attachmentSetup}
        new ${spec.classPrefix}Service(runtime, users).submit(request, files, "key");
        verify(runtime, times(1)).startAndSubmit(argThat(value ->
                "${escapeJava(spec.processCode)}".equals(value.getProcessCode())
                        && "${expectedInstanceTitle}".equals(value.getInstanceTitle())));
    }
}
`;
}

function viewSource(spec: GenerationSpec, fields: FormFieldRequirement[], formType: string, submitFunction: string): string {
  const formValues = fields.map((field) => `${field.fieldCode}: ${tsDefault(field)}`).concat(spec.applyAttachments.map((item) => `${item.attachmentCode}: [] as File[]`)).join(", ");
  const requiredChecks = fields.filter((field) => field.required).map((field) => field.multiple ? `!form.${field.fieldCode}.length` : `!form.${field.fieldCode}`).concat(spec.applyAttachments.filter((item) => item.required).map((item) => `!form.${item.attachmentCode}.length`));
  const controls = fields.map((field) => vueControl(field)).concat(spec.applyAttachments.map((item) => `<input aria-label="${escapeHtml(item.attachmentName)}" type="file" ${item.required ? "required " : ""}multiple @change="form.${item.attachmentCode} = Array.from(($event.target as HTMLInputElement).files || [])" />`)).join("\n    ");
  const dynamicFields = fields.filter((field) => field.referenceDataSource);
  const referenceFunctions = [...new Set(dynamicFields.map((field) => referenceFunction(field.referenceDataSource!.resource)))];
  const referenceImports = referenceFunctions.length ? `, ${referenceFunctions.join(", ")}` : "";
  const optionDeclarations = dynamicFields.filter((field) => field.controlType === "select")
    .map((field) => `const ${field.fieldCode}Options = ref<any[]>([]);`).join("\n");
  const referenceSetup = dynamicReferenceSetup(dynamicFields);
  return `<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { ${submitFunction}${referenceImports}, type ${formType} } from "../../../api/generated/${spec.kebabCode}";
const form = reactive<${formType}>({ ${formValues} });
const success = ref("");
const referenceLoading = ref(false);
const referenceError = ref("");
${optionDeclarations}
${referenceSetup}
async function submit() {
  if (${requiredChecks.length ? requiredChecks.join(" || ") : "false"}) throw new Error("请完整填写必填项");
  const result = await ${submitFunction}(form, crypto.randomUUID());
  success.value = "提交成功，下一处理节点：" + (result.createdTasks?.[0]?.taskName || "待处理");
}
</script>
<template><form @submit.prevent="submit"><h1>${escapeHtml(spec.businessName)}</h1><p v-if="referenceError" role="alert">{{ referenceError }}</p>
    ${controls}
    <button type="submit">提交申请</button><p v-if="success">{{ success }}</p></form></template>
`;
}

function viewTestSource(spec: GenerationSpec, fields: FormFieldRequirement[]): string {
  const firstLabel = spec.applyAttachments[0]?.attachmentName;
  const referenceMocks = [...new Set(fields.filter((field) => field.referenceDataSource)
    .map((field) => `${referenceFunction(field.referenceDataSource!.resource)}: vi.fn().mockResolvedValue([])`))];
  return `import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import ${spec.classPrefix}Apply from "../${spec.kebabCode}/${spec.classPrefix}Apply.vue";
vi.mock("../../../api/generated/${spec.kebabCode}", () => ({ submit${spec.classPrefix}: vi.fn().mockResolvedValue({ createdTasks: [{ nodeName: "下一节点" }] })${referenceMocks.length ? `, ${referenceMocks.join(", ")}` : ""} }));
describe("${spec.businessName} apply", () => { it("renders confirmed initiation controls", () => {
  const wrapper = mount(${spec.classPrefix}Apply);
  expect(wrapper.text()).toContain("${escapeTs(spec.businessName)}");${firstLabel ? `
  expect(wrapper.find('[aria-label="${escapeTs(firstLabel)}"]').exists()).toBe(true);` : ""}
}); });
`;
}

function apiSource(spec: GenerationSpec, fields: FormFieldRequirement[], formType: string, submitFunction: string): string {
  const properties = fields.map((field) => `${field.fieldCode}: ${tsType(field)};`).concat(spec.applyAttachments.map((item) => `${item.attachmentCode}: File[];`)).join(" ");
  const payload = fields.map((field) => `${field.fieldCode}: input.${field.fieldCode}`).join(", ");
  const attachments = spec.applyAttachments.map((item) => `input.${item.attachmentCode}.forEach((file) => body.append("${item.attachmentCode}", file));`).join("\n  ");
  return `${referenceApiSource(fields)}export interface ${formType} { ${properties} }
export async function ${submitFunction}(input: ${formType}, idempotencyKey: string): Promise<any> {
  const body = new FormData();
  body.append("payload", new Blob([JSON.stringify({ ${payload} })], { type: "application/json" }));
  ${attachments}
  const response = await fetch("${spec.apiPath}", { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body });
  if (!response.ok) throw new Error("提交失败");
  return response.json();
}
`;
}

function apiTestSource(spec: GenerationSpec, fields: FormFieldRequirement[], submitFunction: string): string {
  const values = fields.map((field) => `${field.fieldCode}: ${tsTestValue(field)}`).concat(spec.applyAttachments.map((item) => `${item.attachmentCode}: []`)).join(", ");
  return `import { describe, expect, it, vi } from "vitest";
import { ${submitFunction} } from "./${spec.kebabCode}";
describe("${spec.kebabCode} api", () => { it("uses the derived endpoint and idempotency header", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ instanceId: "i1" }), { status: 200 }));
  await ${submitFunction}({ ${values} }, "stable-key");
  expect(fetchMock.mock.calls[0][0]).toBe("${spec.apiPath}");
  expect(fetchMock.mock.calls[0][1]?.headers).toEqual({ "Idempotency-Key": "stable-key" });
}); });
`;
}

function mergeGeneratedRoute(existing: string, spec: GenerationSpec): string {
  let result = existing;
  if (!result.includes(`name: "${spec.routeName}"`) && !result.includes(`name: '${spec.routeName}'`)) {
    const componentFile = spec.paths.applyView.endsWith("/Apply.vue") ? "Apply.vue" : `${spec.classPrefix}Apply.vue`;
    const route = `{ path: "${spec.routePath}", name: "${spec.routeName}", meta: { title: "${escapeTs(spec.businessName)}", standalone: true }, component: () => import("../modules/generated/${spec.kebabCode}/${componentFile}") }`;
  const empty = /export const generatedRoutes:\s*RouteRecordRaw\[\]\s*=\s*\[\s*\];/;
    if (empty.test(result)) {
      result = result.replace(empty, `export const generatedRoutes: RouteRecordRaw[] = [${route}];`);
    } else {
      const closing = result.lastIndexOf("];");
  if (closing < 0) throw new Error("generated route registry does not export an array");
      const before = result.slice(0, closing).trimEnd();
  const separator = before.endsWith("[") ? "" : ",";
      result = `${before}${separator}\n  ${route},\n${result.slice(closing)}`;
    }
  }
  const registryEntry = `${spec.processCode}: () => import("../modules/generated/${spec.kebabCode}/BusinessForm.vue")`;
  if (result.includes(`${spec.processCode}: () =>`)) return result;
  const registryPattern = /export const generatedBusinessFormRegistry\s*=\s*\{([\s\S]*?)\};/;
  if (registryPattern.test(result)) {
    return result.replace(registryPattern, (_match, body: string) => {
      const trimmed = body.trim();
      const separator = trimmed && !trimmed.endsWith(",") ? "," : "";
      return `export const generatedBusinessFormRegistry = {${body}${separator}\n  ${registryEntry},\n};`;
    });
  }
  return `${result.trimEnd()}\nexport const generatedBusinessFormRegistry = {\n  ${registryEntry},\n};\n`;
}

function businessFormSource(requirement: BusinessRequirement, fields: FormFieldRequirement[]): string {
  const fieldCodes = fields.map((field) => JSON.stringify(field.fieldCode)).join(", ");
  return `<script setup>
import { computed } from "vue";
const props = defineProps({
  modelValue: { type: Object, required: true },
  fields: { type: Array, required: true },
  fieldPermissions: { type: Array, required: true },
  mode: { type: String, default: "edit" },
  disabled: { type: Boolean, default: false },
});
const emit = defineEmits(["update:modelValue"]);
const confirmedFieldCodes = [${fieldCodes}];
const visibleFields = computed(() => props.fields.filter((field) => confirmedFieldCodes.includes(field.fieldCode)));
function update(fieldCode, value) { emit("update:modelValue", { ...props.modelValue, [fieldCode]: value }); }
async function validate() { return true; }
defineExpose({ validate });
</script>
<template>
  <section aria-label="${escapeHtml(requirement.businessName)}表单">
    <label v-for="field in visibleFields" :key="field.fieldCode" :data-field-code="field.fieldCode">
      <span>{{ field.fieldName }}</span>
      <input :value="String(modelValue[field.fieldCode] ?? '')" :disabled="disabled || mode === 'readonly'" @input="update(field.fieldCode, $event.target.value)" />
    </label>
  </section>
</template>
`;
}

function businessFormTestSource(spec: GenerationSpec): string {
  return `import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import BusinessForm from "../BusinessForm.vue";
describe("${spec.kebabCode} BusinessForm", () => { it("renders confirmed fields only", () => {
  const wrapper = mount(BusinessForm, { props: { modelValue: {}, fields: [{ fieldCode: "applicantName", fieldName: "申请人" }], fieldPermissions: [] } });
  expect(wrapper.text()).toContain("申请人");
}); });
`;
}

function applyShellSource(spec: GenerationSpec): string {
  return `<script setup lang="ts">
import { defineAsyncComponent } from "vue";
import WorkflowStartShell from "../../../components/workflow/WorkflowStartShell.vue";
const BusinessForm = defineAsyncComponent(() => import("./BusinessForm.vue"));
</script>
<template>
  <WorkflowStartShell process-code="${spec.processCode}" :business-form="BusinessForm" />
</template>
`;
}

function applyShellTestSource(spec: GenerationSpec): string {
  return `import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import Apply from "../Apply.vue";
describe("${spec.kebabCode} Apply", () => { it("delegates submission to WorkflowStartShell", () => {
  const wrapper = mount(Apply, { global: { stubs: { WorkflowStartShell: true } } });
  expect(wrapper.findComponent({ name: "WorkflowStartShell" }).exists()).toBe(true);
}); });
`;
}

function javaType(field: FormFieldRequirement): string {
  if (field.multiple) return "java.util.List<String>";
  if (field.fieldType === "number") return "BigDecimal";
  if (field.fieldType === "date") return "LocalDate";
  if (field.fieldType === "boolean") return "Boolean";
  return "String";
}

function validationAnnotations(field: FormFieldRequirement): string {
  const annotations: string[] = [];
  if (field.required) annotations.push(field.multiple ? "@NotEmpty" : field.fieldType === "string" || field.fieldType === "select" ? "@NotBlank" : "@NotNull");
  if (field.fieldType === "number" && finiteNumber(field.validation.minimum)) annotations.push(`@DecimalMin("${field.validation.minimum}")`);
  if (field.fieldType === "number" && finiteNumber(field.validation.maximum)) annotations.push(`@DecimalMax("${field.validation.maximum}")`);
  const minLength = nonNegativeInteger(field.validation.minLength);
  const maxLength = nonNegativeInteger(field.validation.maxLength);
  if (minLength !== undefined || maxLength !== undefined) {
    annotations.push(`@Size(${minLength === undefined ? "" : `min = ${minLength}`}${minLength !== undefined && maxLength !== undefined ? ", " : ""}${maxLength === undefined ? "" : `max = ${maxLength}`})`);
  }
  if (typeof field.validation.pattern === "string" && field.validation.pattern) annotations.push(`@Pattern(regexp = "${escapeJava(field.validation.pattern)}")`);
  return annotations.map((annotation) => `    ${annotation}\n`).join("");
}

function tsType(field: FormFieldRequirement): string {
  if (field.multiple) return "string[]";
  if (field.fieldType === "number") return "number";
  if (field.fieldType === "boolean") return "boolean";
  return "string";
}

function tsDefault(field: FormFieldRequirement): string {
  if (field.multiple) return "[]";
  if (field.defaultValue !== undefined) {
    if (field.fieldType === "number") return String(Number(field.defaultValue));
    if (field.fieldType === "boolean") return String(field.defaultValue.toLowerCase() === "true");
    return JSON.stringify(field.defaultValue);
  }
  if (field.fieldType === "number") return "0";
  if (field.fieldType === "boolean") return "false";
  return '""';
}

function tsTestValue(field: FormFieldRequirement): string {
  if (field.multiple) return '["value"]';
  if (field.fieldType === "number") return "1";
  if (field.fieldType === "boolean") return "true";
  return '"value"';
}

function javaTestValue(field: FormFieldRequirement): string {
  if (field.multiple) return 'java.util.Collections.singletonList("value")';
  if (field.fieldType === "number") return 'new java.math.BigDecimal("1")';
  if (field.fieldType === "boolean") return "Boolean.TRUE";
  if (field.fieldType === "date") return "java.time.LocalDate.now()";
  return '"value"';
}

function extensionValidation(name: string, extensions: string[]): string {
  if (!extensions.length) return "// No extension restriction was confirmed.";
  const checks = extensions.map((extension) => `lowerName.endsWith(".${escapeJava(extension.toLowerCase().replace(/^\./, ""))}")`).join(" || ");
  return `String lowerName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
                if (!(${checks})) throw new IllegalArgumentException("${escapeJava(name)}文件类型不符合要求");`;
}

function vueControl(field: FormFieldRequirement): string {
  if (field.controlType === "checkbox") return `<input v-model="form.${field.fieldCode}" aria-label="${escapeHtml(field.fieldName)}" type="checkbox" />`;
  if (field.controlType === "select") {
    const source = field.referenceDataSource;
    const options = source
      ? `<option v-for="option in ${field.fieldCode}Options" :key="option.${referenceValueProperty(source.resource)}" :value="option.${referenceValueProperty(source.resource)}">{{ ${referenceLabelExpression(source.resource)} }}</option>`
      : (field.options || []).map((item) => `<option value="${escapeHtml(item.value)}">${escapeHtml(item.label)}</option>`).join("");
    return `<select v-model="form.${field.fieldCode}" aria-label="${escapeHtml(field.fieldName)}" ${field.required ? "required" : ""} ${field.multiple ? "multiple" : ""} :disabled="referenceLoading">${options}</select>`;
  }
  if (field.controlType === "textarea") return `<textarea v-model="form.${field.fieldCode}" aria-label="${escapeHtml(field.fieldName)}" ${field.required ? "required" : ""}></textarea>`;
  const type = field.controlType === "number" ? "number" : field.controlType === "datePicker" ? "date" : "text";
  const model = field.controlType === "number" ? "v-model.number" : "v-model";
  const validation = [
    finiteNumber(field.validation.minimum) ? `min="${field.validation.minimum}"` : "",
    finiteNumber(field.validation.maximum) ? `max="${field.validation.maximum}"` : "",
    nonNegativeInteger(field.validation.minLength) !== undefined ? `minlength="${field.validation.minLength}"` : "",
    nonNegativeInteger(field.validation.maxLength) !== undefined ? `maxlength="${field.validation.maxLength}"` : "",
    typeof field.validation.pattern === "string" && field.validation.pattern ? `pattern="${escapeHtml(field.validation.pattern)}"` : "",
  ].filter(Boolean).join(" ");
  return `<input ${model}="form.${field.fieldCode}" aria-label="${escapeHtml(field.fieldName)}" type="${type}" ${field.required ? "required" : ""} ${field.readOnly ? "readonly" : ""} ${validation} />`;
}

function referenceFunction(resource: NonNullable<FormFieldRequirement["referenceDataSource"]>["resource"]): string {
  return {
    FUTURES_ACCOUNTS: "loadFuturesAccounts",
    EXCHANGES: "loadExchanges",
    TRADING_CODES: "loadTradingCodes",
    FUTURES_PRODUCTS: "loadFuturesProducts",
  }[resource];
}

function referenceValueProperty(resource: NonNullable<FormFieldRequirement["referenceDataSource"]>["resource"]): string {
  return {
    FUTURES_ACCOUNTS: "accountNo",
    EXCHANGES: "exchangeCode",
    TRADING_CODES: "tradingCode",
    FUTURES_PRODUCTS: "productCode",
  }[resource];
}

function referenceLabelExpression(resource: NonNullable<FormFieldRequirement["referenceDataSource"]>["resource"]): string {
  return {
    FUTURES_ACCOUNTS: 'option.accountNo + " - " + option.customerName',
    EXCHANGES: "option.exchangeName",
    TRADING_CODES: 'option.tradingCode + "（" + option.tradingStatus + "）"',
    FUTURES_PRODUCTS: 'option.productCode + " - " + option.productName',
  }[resource];
}

function dynamicReferenceSetup(fields: FormFieldRequirement[]): string {
  const lines: string[] = [];
  for (const field of fields) {
    const source = field.referenceDataSource!;
    if (source.resource === "FUTURES_ACCOUNTS" || source.resource === "EXCHANGES") {
      lines.push(`onMounted(async () => { referenceLoading.value = true; try { ${field.fieldCode}Options.value = await ${referenceFunction(source.resource)}(); } catch { referenceError.value = "参考数据加载失败"; } finally { referenceLoading.value = false; } });`);
      continue;
    }
    const bindings = source.parameterBindings || {};
    const dependencies = Object.values(bindings);
    const dependencyExpression = dependencies.length === 1
      ? `() => form.${dependencies[0]}`
      : `() => [${dependencies.map((code) => `form.${code}`).join(", ")}]`;
    const values = dependencies.length === 1 ? ["value"] : dependencies.map((_item, index) => `values[${index}]`);
    const parameterValue = new Map(Object.keys(bindings).map((parameter, index) => [parameter, values[index]]));
    const clearValue = field.multiple ? "[]" : '""';
    if (source.resource === "TRADING_CODES") {
      lines.push(`watch(${dependencyExpression}, async (${dependencies.length === 1 ? "value" : "values"}) => { form.${field.fieldCode} = ${clearValue}; const accountNo = ${parameterValue.get("accountNo")}; const exchangeCode = ${parameterValue.get("exchangeCode")}; if (!accountNo || !exchangeCode) return; referenceLoading.value = true; try { const items = await loadTradingCodes(String(accountNo), String(exchangeCode)); form.${field.fieldCode} = items[0]?.tradingCode || ""; } catch { referenceError.value = "交易编码加载失败"; } finally { referenceLoading.value = false; } }, { immediate: true });`);
    } else if (source.resource === "FUTURES_PRODUCTS") {
      const exchangeValue = parameterValue.get("exchangeCode");
      const clearAutofill = Object.values(source.autofillBindings || {}).map((target) => `form.${target} = ${fieldDefaultForCode(fields, target)};`).join(" ");
      lines.push(`watch(${dependencyExpression}, async (${dependencies.length === 1 ? "value" : "values"}) => { form.${field.fieldCode} = ${clearValue}; ${clearAutofill} ${field.fieldCode}Options.value = []; const exchangeCode = ${exchangeValue}; if (!exchangeCode) return; referenceLoading.value = true; try { ${field.fieldCode}Options.value = await loadFuturesProducts(String(exchangeCode)); } catch { referenceError.value = "期货品种加载失败"; } finally { referenceLoading.value = false; } }, { immediate: true });`);
      if (source.autofillBindings && Object.keys(source.autofillBindings).length) {
        const selectedCodes = field.multiple ? `form.${field.fieldCode}` : `[form.${field.fieldCode}]`;
        const assignments = Object.entries(source.autofillBindings).map(([property, target]) => `form.${target} = selected ? selected.${property} : ${fieldDefaultForCode(fields, target)};`).join(" ");
        lines.push(`watch(() => form.${field.fieldCode}, () => { const codes = ${selectedCodes}; const selected = ${field.fieldCode}Options.value.find((item) => item.productCode === codes[codes.length - 1]); ${assignments} }, { deep: true });`);
      }
    }
  }
  return lines.join("\n");
}

function fieldDefaultForCode(fields: FormFieldRequirement[], fieldCode: string): string {
  const target = fields.find((field) => field.fieldCode === fieldCode);
  return target ? tsDefault(target) : '""';
}

function referenceApiSource(fields: FormFieldRequirement[]): string {
  const resources = new Set(fields.flatMap((field) => field.referenceDataSource ? [field.referenceDataSource.resource] : []));
  if (!resources.size) return "";
  const blocks = [
    `async function requestReferenceData<T>(url: string): Promise<T> { const response = await fetch(url); if (!response.ok) throw new Error("参考数据加载失败"); return response.json() as Promise<T>; }`,
  ];
  if (resources.has("FUTURES_ACCOUNTS")) blocks.push(`export interface FuturesAccountOption { accountNo: string; customerName: string; accountStatus: "NORMAL" | "DORMANT"; }\nexport function loadFuturesAccounts(keyword = ""): Promise<FuturesAccountOption[]> { const query = keyword ? "?keyword=" + encodeURIComponent(keyword) : ""; return requestReferenceData("/api/reference-data/futures-accounts" + query); }`);
  if (resources.has("EXCHANGES")) blocks.push(`export interface ExchangeOption { exchangeCode: string; exchangeName: string; sourcePrefix: string; }\nexport function loadExchanges(): Promise<ExchangeOption[]> { return requestReferenceData("/api/reference-data/exchanges"); }`);
  if (resources.has("TRADING_CODES")) blocks.push(`export interface TradingCodeOption { accountNo: string; exchangeCode: string; tradingCode: string; tradingStatus: "NORMAL" | "DORMANT"; }\nexport function loadTradingCodes(accountNo: string, exchangeCode: string): Promise<TradingCodeOption[]> { return requestReferenceData("/api/reference-data/futures-accounts/" + encodeURIComponent(accountNo) + "/trading-codes?exchangeCode=" + encodeURIComponent(exchangeCode)); }`);
  if (resources.has("FUTURES_PRODUCTS")) blocks.push(`export interface FuturesProductOption { exchangeCode: string; exchangeName: string; productCode: string; productName: string; productType: "FUTURES"; contractMultiplier: number; pledgeUnitQuantity: number; previousSettlementPrice: number; dataSource: "HTML_EXTRACTED" | "DEMO_GENERATED"; }\nexport function loadFuturesProducts(exchangeCode: string, keyword = ""): Promise<FuturesProductOption[]> { const query = new URLSearchParams({ exchangeCode, productType: "FUTURES" }); if (keyword) query.set("keyword", keyword); return requestReferenceData("/api/reference-data/futures-products?" + query.toString()); }`);
  return `${blocks.join("\n\n")}\n\n`;
}

function getter(code: string): string { return `get${code.slice(0, 1).toUpperCase()}${code.slice(1)}`; }
function setter(code: string): string { return `set${code.slice(0, 1).toUpperCase()}${code.slice(1)}`; }
function simpleName(type: string): string { return type.split(".").pop() || type; }
function finiteNumber(value: unknown): value is number { return typeof value === "number" && Number.isFinite(value); }
function nonNegativeInteger(value: unknown): number | undefined { return Number.isInteger(value) && Number(value) >= 0 ? Number(value) : undefined; }
function escapeJava(value: string): string { return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"'); }
function escapeTs(value: string): string { return escapeJava(value); }
function escapeHtml(value: string): string { return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }
