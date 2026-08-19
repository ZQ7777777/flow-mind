<script setup lang="ts">
import { defineAsyncComponent } from "vue";
import WorkflowStartShell from "../../../components/workflow/WorkflowStartShell.vue";

const BusinessForm = defineAsyncComponent(() => import("./BusinessForm.vue"));
</script>

<template>
  <main class="warehouse-pledge-page">
    <WorkflowStartShell process-code="warehouse_pledge" :business-form="BusinessForm" />
  </main>
</template>

<style scoped>
.warehouse-pledge-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 20px;
  background: #f5f7fb;
}
.warehouse-pledge-page :deep(.workflow-start-shell) {
  max-width: 1480px;
  margin: 0 auto;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  padding: 18px;
  background: #fff;
}
@media (max-width: 640px) { .warehouse-pledge-page { padding: 10px; } }
</style>
