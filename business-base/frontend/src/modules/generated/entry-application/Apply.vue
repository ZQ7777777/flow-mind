<script setup lang="ts">
import { markRaw } from "vue";
import WorkflowStartShell from "../../../components/workflow/WorkflowStartShell.vue";
import BusinessForm from "./BusinessForm.vue";

/**
 * 入金申请（entry_application）发起页适配器。
 *
 * 只向共享发起外壳透传固定的 processCode 与生成的业务表单组件；
 * 上下文加载（GET /api/workflow/processes/{processCode}/start-context）、
 * 提交（POST /api/workflow/processes/{processCode}/start-submit）、
 * 附件上传、成功/失败状态与后续导航均由共享外壳持有，本组件不重复实现。
 */
const PROCESS_CODE = "entry_application";

const businessForm = markRaw(BusinessForm);

</script>

<template>
  <WorkflowStartShell :process-code="PROCESS_CODE" :business-form="businessForm" />
</template>
