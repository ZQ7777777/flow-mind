<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const username = ref("");
const password = ref("");

function requestedRoute(): string {
  const redirect = route.query.redirect;
  if (typeof redirect !== "string" || !redirect.startsWith("/") || redirect.startsWith("//")) {
    return "/workflow/todo";
  }
  return redirect === "/login" || redirect.startsWith("/login?")
    ? "/workflow/todo"
    : redirect;
}

async function submit(): Promise<void> {
  if (await auth.login(username.value, password.value)) {
    await router.replace(requestedRoute());
  }
}
</script>

<template>
  <div class="login-page">
    <header class="login-header">
      <div>
        <p>Flow Mind</p>
        <h1>业务流程系统</h1>
      </div>
    </header>

    <main class="login-main">
      <section class="login-panel" aria-labelledby="login-title">
        <div class="panel-heading">
          <h2 id="login-title">用户登录</h2>
          <p>使用业务系统账号登录</p>
        </div>

        <form @submit.prevent="submit">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="username"
            name="username"
            type="text"
            autocomplete="username"
            required
          />

          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
          />

          <p v-if="auth.error" class="login-error" role="alert">{{ auth.error }}</p>

          <button type="submit" :disabled="auth.loading">
            {{ auth.loading ? "登录中..." : "登录" }}
          </button>
        </form>
      </section>
    </main>
  </div>
</template>

<style scoped>
.login-page,
.login-page * {
  box-sizing: border-box;
}

.login-page {
  min-height: 100vh;
  background: #eef2f7;
  color: #17202a;
  font-family: "Segoe UI", "Microsoft YaHei", Arial, sans-serif;
}

.login-header {
  min-height: 74px;
  padding: 14px 24px;
  border-bottom: 1px solid #d8dee8;
  background: #fff;
}

.login-header p {
  margin: 0 0 3px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.login-header h1 {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
}

.login-main {
  display: grid;
  min-height: calc(100vh - 74px);
  place-items: start center;
  padding: clamp(48px, 10vh, 96px) 20px 32px;
}

.login-panel {
  width: min(100%, 420px);
  border: 1px solid #d8dee8;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(15 23 42 / 8%);
}

.panel-heading {
  padding: 22px 24px 18px;
  border-bottom: 1px solid #e5e9f0;
}

.panel-heading h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.panel-heading p {
  margin: 0;
  color: #5d6978;
  font-size: 14px;
}

form {
  display: grid;
  gap: 9px;
  padding: 22px 24px 24px;
}

label {
  color: #374151;
  font-size: 14px;
  font-weight: 700;
}

input {
  width: 100%;
  min-height: 40px;
  margin-bottom: 7px;
  border: 1px solid #b9c2cf;
  border-radius: 5px;
  padding: 8px 10px;
  background: #fff;
  color: #17202a;
  font: inherit;
}

input:focus {
  border-color: #0f766e;
  outline: 3px solid rgb(15 118 110 / 15%);
}

.login-error {
  margin: 0 0 4px;
  border-left: 3px solid #b42318;
  padding: 7px 10px;
  background: #fff1f0;
  color: #8a1c13;
  font-size: 13px;
}

button {
  min-height: 40px;
  margin-top: 4px;
  border: 1px solid #0b5f59;
  border-radius: 5px;
  padding: 8px 16px;
  background: #0f766e;
  color: #fff;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

button:hover:not(:disabled) {
  background: #0b5f59;
}

button:disabled {
  cursor: wait;
  opacity: 0.65;
}
</style>
