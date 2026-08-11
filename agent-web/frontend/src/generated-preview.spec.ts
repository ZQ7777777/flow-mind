import { describe, expect, it } from "vitest";
import { buildGeneratedPreviewDocument } from "./generated-preview";

function executePreviewDocument(srcdoc: string): Document {
  const document = new DOMParser().parseFromString(srcdoc, "text/html");
  const script = document.querySelector("script");
  if (!script?.textContent) throw new Error("preview interaction script is missing");
  Function("document", "Element", script.textContent)(document, Element);
  return document;
}

describe("generated static preview", () => {
  it("extracts the Vue template and styles while mapping common Element Plus controls", () => {
    const result = buildGeneratedPreviewDocument(`<script setup lang="ts">
const form = { name: "" };
</script>
<template>
  <section class="generated-entry-page">
    <el-form><el-form-item label="申请人"><el-input v-model="form.name" placeholder="请输入" /></el-form-item>
    <el-form-item label="金额"><el-input-number v-model="form.amount" /></el-form-item>
    <el-form-item label="币种"><el-select><el-option label="人民币" value="CNY" /></el-select></el-form-item>
    <el-date-picker v-model="form.date" /><el-upload multiple>上传附件</el-upload>
    <el-button type="primary" @click="submit">提交</el-button></el-form>
  </section>
</template>
<style scoped>.generated-entry-page { color: rgb(1, 2, 3); }</style>`);

    expect(result.srcdoc).toContain("generated-entry-page");
    expect(result.srcdoc).toContain("申请人");
    expect(result.srcdoc).toContain('<input placeholder="请输入" type="text">');
    expect(result.srcdoc).toContain('type="number"');
    expect(result.srcdoc).toContain("<select>");
    expect(result.srcdoc).toContain("人民币");
    expect(result.srcdoc).toContain('type="date"');
    expect(result.srcdoc).toContain('type="file"');
    expect(result.srcdoc).toContain("preview-primary-button");
    expect(result.srcdoc).toContain("color: rgb(1, 2, 3)");
    expect(result.srcdoc).not.toContain("v-model");
    expect(result.srcdoc).not.toContain("@click");
  });

  it("hides conditional feedback, replaces interpolation, and degrades unknown components", () => {
    const result = buildGeneratedPreviewDocument(`<template><main>
      <p v-if="error">{{ error }}</p><business-summary><strong>摘要</strong></business-summary>
    </main></template>`);

    expect(result.srcdoc).toContain('<p hidden="">—</p>');
    expect(result.srcdoc).toContain('class="preview-unknown"');
    expect(result.srcdoc).toContain("<strong>摘要</strong>");
    expect(result.srcdoc).not.toContain("{{ error }}");
  });

  it("marks native and Element Plus actions, simulates submit, and resets only the preview form", () => {
    const result = buildGeneratedPreviewDocument(`<template><form>
      <input name="applicant" value="初始值" />
      <button type="submit">提交申请</button>
      <el-button @click="resetForm">重置</el-button>
    </form></template>`);
    expect(result.srcdoc).toContain('data-preview-action="submit"');
    expect(result.srcdoc).toContain('data-preview-action="reset"');

    const document = executePreviewDocument(result.srcdoc);
    const input = document.querySelector<HTMLInputElement>('input[name="applicant"]')!;
    const submit = document.querySelector<HTMLElement>('[data-preview-action="submit"]')!;
    const reset = document.querySelector<HTMLElement>('[data-preview-action="reset"]')!;
    const warning = document.getElementById("preview-interaction-warning")!;
    input.value = "预览中填写的值";
    submit.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    expect(warning.hidden).toBe(false);
    expect(warning.textContent).toContain("内容未真实提交");
    expect(input.value).toBe("预览中填写的值");

    reset.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    expect(input.value).toBe("初始值");
    expect(warning.hidden).toBe(true);
  });

  it("recognizes handler names and an implicit native form submit button", () => {
    const result = buildGeneratedPreviewDocument(`<template><form>
      <el-button @click="handleSubmit">继续</el-button>
      <button @click="clearForm">返回初始状态</button>
      <button>下一步</button>
    </form></template>`);
    const document = new DOMParser().parseFromString(result.srcdoc, "text/html");
    expect(document.querySelector('button[data-preview-action="submit"]')?.textContent).toBe("继续");
    expect(document.querySelector('button[data-preview-action="reset"]')?.textContent).toBe("返回初始状态");
    expect(document.querySelectorAll('button[data-preview-action="submit"]')).toHaveLength(2);
  });

  it("removes executable content, navigation targets, and external resources", () => {
    const result = buildGeneratedPreviewDocument(`<template><form action="https://evil.example/submit" @submit="steal">
      <script>globalThis.compromised = true</script>
      <iframe src="https://evil.example/frame"></iframe>
      <img src="https://evil.example/pixel.png" onerror="steal()">
      <a href="javascript:steal()">危险链接</a>
      <button formaction="https://evil.example/button">提交</button>
    </form></template>
    <style>@import "https://evil.example/style.css"; .x { background: url(https://evil.example/a.png); }</style>`);

    expect(result.srcdoc).toContain("default-src 'none'");
    expect(result.srcdoc).toContain("script-src 'nonce-flowmind-static-preview'");
    expect(result.srcdoc).toContain("connect-src 'none'");
    expect(result.srcdoc).not.toContain("evil.example");
    expect(result.srcdoc).not.toContain("globalThis.compromised");
    expect(result.srcdoc).not.toContain("javascript:");
    expect(result.srcdoc).not.toContain("onerror");
    expect(result.srcdoc).not.toContain("formaction");
    const document = new DOMParser().parseFromString(result.srcdoc, "text/html");
    const submit = document.querySelector('button[data-preview-action="submit"]');
    expect(submit?.getAttribute("type")).toBe("button");
    expect(submit?.textContent).toBe("提交");
    const scripts = Array.from(document.querySelectorAll("script"));
    expect(scripts).toHaveLength(1);
    expect(scripts[0].getAttribute("nonce")).toBe("flowmind-static-preview");
    expect(scripts[0].textContent).not.toMatch(/fetch|XMLHttpRequest|postMessage|localStorage|saveGeneratedFile/);
  });

  it("reports missing or empty templates", () => {
    expect(() => buildGeneratedPreviewDocument(" ")).toThrow("内容为空");
    expect(() => buildGeneratedPreviewDocument("<script setup>const value = 1</script>")).toThrow("缺少 template");
  });
});
