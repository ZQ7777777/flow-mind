<script setup lang="ts">
import { ref } from "vue";
import type { UploadUserFile } from "element-plus";
import { submitEntryApplication } from "../../../api/generated/entry-application";
import type { EntryApplicationSubmitResult } from "../../../api/generated/entry-application";

interface FormState {
  applicantName: string;
  amount: number | undefined;
  /** 发起节点附件：付款凭证，按附件编码 bankReceipt 暴露给表单。 */
  bankReceipt: UploadUserFile[];
}

const BANK_RECEIPT_MAX_COUNT = 5;
const BANK_RECEIPT_MAX_SIZE_BYTES = 10 * 1024 * 1024;
const BANK_RECEIPT_ALLOWED_EXTENSIONS = ["pdf", "jpg", "png"] as const;

const form = ref<FormState>({ applicantName: "", amount: undefined, bankReceipt: [] });
const submitting = ref(false);
const errorText = ref("");
const successText = ref("");

/**
 * 幂等键在页面加载时生成一次；同一页面内的重试提交保持同一键，
 * 服务端据此派生稳定的 operationId，保证重试幂等。
 */
const idempotencyKey = ref(createIdempotencyKey());

function createIdempotencyKey(): string {
  const random =
    typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
      ? crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  return `entry-application:${random}`;
}

function selectedBankReceiptFiles(): File[] {
  return form.value.bankReceipt
    .filter((item) => item.raw != null)
    .map((item) => item.raw as File);
}

function validate(): string | null {
  if (!form.value.applicantName.trim()) {
    return "请输入申请人姓名";
  }
  const amount = form.value.amount;
  if (amount === null || amount === undefined || Number.isNaN(amount) || amount <= 0) {
    return "入金金额必须大于0";
  }
  const bankReceiptFiles = selectedBankReceiptFiles();
  if (bankReceiptFiles.length === 0) {
    return "请至少上传1个付款凭证";
  }
  if (bankReceiptFiles.length > BANK_RECEIPT_MAX_COUNT) {
    return "付款凭证最多上传5个文件";
  }
  for (const raw of bankReceiptFiles) {
    const dot = raw.name.lastIndexOf(".");
    const extension =
      dot >= 0 && dot < raw.name.length - 1 ? raw.name.slice(dot + 1).toLowerCase() : "";
    if (
      !BANK_RECEIPT_ALLOWED_EXTENSIONS.includes(
        extension as (typeof BANK_RECEIPT_ALLOWED_EXTENSIONS)[number]
      )
    ) {
      return "付款凭证仅支持 pdf、jpg、png 格式";
    }
    if (raw.size > BANK_RECEIPT_MAX_SIZE_BYTES) {
      return "付款凭证单个文件不能超过10MB";
    }
  }
  return null;
}

async function handleSubmit(): Promise<void> {
  if (submitting.value) {
    return;
  }
  const validationError = validate();
  if (validationError !== null) {
    errorText.value = validationError;
    successText.value = "";
    return;
  }
  errorText.value = "";
  successText.value = "";
  submitting.value = true;
  try {
    const result: EntryApplicationSubmitResult = await submitEntryApplication(
      { applicantName: form.value.applicantName.trim(), amount: form.value.amount as number },
      { idempotencyKey: idempotencyKey.value, bankReceiptFiles: selectedBankReceiptFiles() }
    );
    successText.value = `提交成功，实例ID：${result.instanceId}`;
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : "提交失败";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="entry-application-apply">
    <h2>入金申请</h2>
    <el-form :model="form" label-width="110px" @submit.prevent="handleSubmit">
      <el-form-item label="申请人姓名">
        <el-input v-model="form.applicantName" placeholder="请输入申请人姓名" />
      </el-form-item>
      <el-form-item label="入金金额">
        <el-input-number
          v-model="form.amount"
          :min="0.01"
          :precision="2"
          :controls="false"
          placeholder="请输入入金金额"
        />
      </el-form-item>
      <el-form-item label="付款凭证">
        <el-upload
          v-model:file-list="form.bankReceipt"
          action="#"
          :auto-upload="false"
          :limit="BANK_RECEIPT_MAX_COUNT"
          :accept="'.pdf,.jpg,.png'"
        >
          <el-button>选择文件</el-button>
        </el-upload>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? "提交中…" : "提交申请" }}
        </el-button>
      </el-form-item>
    </el-form>
    <p v-if="errorText" data-test="error-text" class="error-text">{{ errorText }}</p>
    <p v-if="successText" data-test="success-text" class="success-text">{{ successText }}</p>
  </div>
</template>
