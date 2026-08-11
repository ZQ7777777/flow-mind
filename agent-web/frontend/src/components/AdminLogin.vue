<script setup lang="ts">
import { ref } from "vue";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const username = ref("");
const password = ref("");

async function submit(): Promise<void> {
  if (!username.value.trim() || !password.value) return;
  await auth.login(username.value, password.value);
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-brand">
        <div class="login-brand-mark" aria-hidden="true">FM</div>
        <div>
          <span>FLOW MIND</span>
          <strong>流程生成工作台</strong>
        </div>
      </div>

      <div class="login-heading">
        <p>管理员入口</p>
        <h1 id="login-title">登录 Agent Web</h1>
        <span>使用 Business Base 管理员账号登录</span>
      </div>

      <form @submit.prevent="submit">
        <label for="agent-username">用户名</label>
        <input
          id="agent-username"
          v-model="username"
          name="username"
          type="text"
          autocomplete="username"
          placeholder="请输入用户名"
          autofocus
        />

        <label for="agent-password">密码</label>
        <input
          id="agent-password"
          v-model="password"
          name="password"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
        />

        <p v-if="auth.forbidden" class="login-error" role="alert">仅管理员可以访问 Agent 工作台</p>
        <p v-else-if="auth.error" class="login-error" role="alert">{{ auth.error }}</p>

        <button type="submit" :disabled="auth.loading || !username.trim() || !password">
          {{ auth.loading ? "正在验证..." : "登录" }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: #f3f5f8;
  color: #17213a;
}

.login-panel {
  width: min(420px, 100%);
  padding: 32px 36px 36px;
  border: 1px solid #dfe3eb;
  border-top: 3px solid #275de7;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 14px 40px rgba(20, 34, 64, .1);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 22px;
  border-bottom: 1px solid #e7eaf0;
}

.login-brand-mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 4px;
  background: #17213a;
  color: #fff;
  font-weight: 800;
}

.login-brand span,
.login-brand strong { display: block; }
.login-brand span { color: #7a8498; font-size: 10px; font-weight: 700; }
.login-brand strong { margin-top: 3px; font-size: 16px; }
.login-heading { padding: 28px 0 22px; }
.login-heading p { margin: 0 0 7px; color: #275de7; font-size: 12px; font-weight: 700; }
.login-heading h1 { margin: 0; font-size: 24px; }
.login-heading span { display: block; margin-top: 9px; color: #6c7488; font-size: 13px; }
form { display: grid; }
label { margin: 0 0 7px; font-size: 13px; font-weight: 700; }
input {
  height: 42px;
  margin-bottom: 18px;
  padding: 0 12px;
  border: 1px solid #ccd2dc;
  border-radius: 4px;
  outline: none;
  color: #17213a;
  background: #fff;
}
input:focus { border-color: #275de7; box-shadow: 0 0 0 2px rgba(39, 93, 231, .12); }
.login-error { margin: -4px 0 16px; color: #c0362c; font-size: 13px; }
button {
  height: 42px;
  border: 0;
  border-radius: 4px;
  background: #275de7;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}
button:hover:not(:disabled) { background: #1f4fc9; }
button:disabled { cursor: not-allowed; opacity: .55; }

@media (max-width: 520px) {
  .login-page { padding: 18px; }
  .login-panel { padding: 26px 24px 30px; }
}
</style>
