import { createServer } from "node:http";
import { pathToFileURL } from "node:url";

let definitionStatus = "DRAFT";
let activationStatus = "INACTIVE";
let graph = { nodes: [], edges: [], formFields: [], attachmentConfigs: [] };
const businessEntryConfigs = new Map();
const template = {
  attachmentTemplateId: "tpl_bank_receipt_v1",
  attachmentCode: "bankReceipt",
  attachmentName: "银行回单",
  templateVersion: 1,
  allowedExtensions: ["jpg", "pdf", "png"],
  maxSizeBytes: 10485760,
  templateStatus: "ENABLED",
};

export function startMockPlatform(port = 18080) {
  businessEntryConfigs.clear();
  const server = createServer(async (request, response) => {
  const url = new URL(request.url || "/", "http://127.0.0.1:18080");
  const body = await readBody(request);
  let payload = {};

  if (url.pathname === "/api/auth/login" && request.method === "POST") {
    if (body.username !== "admin01" || body.password !== "123456") {
      response.writeHead(401, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ message: "用户名或密码错误" }));
      return;
    }
    response.writeHead(200, {
      "Content-Type": "application/json",
      "Set-Cookie": "JSESSIONID=e2e-admin-session; Path=/; HttpOnly; SameSite=Lax",
    });
    response.end(JSON.stringify(adminUser()));
    return;
  } else if (url.pathname === "/api/auth/me" && request.method === "GET") {
    if (!String(request.headers.cookie || "").includes("JSESSIONID=e2e-admin-session")) {
      response.writeHead(401, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ message: "请先登录" }));
      return;
    }
    payload = adminUser();
  } else if (url.pathname === "/api/auth/logout" && request.method === "POST") {
    response.writeHead(204, {
      "Set-Cookie": "JSESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax",
    });
    response.end();
    return;
  } else if (url.pathname === "/api/platform/definitions" && request.method === "GET") {
    payload = { items: [], total: 0 };
  } else if (url.pathname === "/api/platform/definitions" && request.method === "POST") {
    definitionStatus = "DRAFT";
    activationStatus = "INACTIVE";
    payload = { id: "definition_entry_v1", version: 1, ...body };
  } else if (url.pathname === "/api/platform/attachment-templates" && request.method === "GET") {
    payload = [];
  } else if (url.pathname === "/api/platform/attachment-templates" && request.method === "POST") {
    payload = { ...template, ...body };
  } else if (url.pathname === "/api/platform/definitions/definition_entry_v1/graph" && request.method === "PUT") {
    graph = body;
    payload = { id: "definition_entry_v1", version: 1 };
  } else if (url.pathname.endsWith("/publish-validation")) {
    payload = { valid: true, issues: [] };
  } else if (url.pathname === "/api/platform/definitions/publish") {
    definitionStatus = "PUBLISHED";
    payload = detail();
  } else if (url.pathname === "/api/platform/definitions/activate") {
    activationStatus = "ACTIVE";
    payload = detail();
  } else if (url.pathname === "/api/platform/definitions/definition_entry_v1") {
    payload = detail();
  } else if (request.method === "PUT" && url.pathname.startsWith("/api/admin/business-entry-configs/by-definition/")) {
    const definitionId = decodeURIComponent(url.pathname.slice("/api/admin/business-entry-configs/by-definition/".length));
    payload = {
      id: `entry_${definitionId}`,
      definitionId,
      ...body,
    };
    businessEntryConfigs.set(definitionId, payload);
  } else {
    response.writeHead(404, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ message: `unknown mock path: ${request.method} ${url.pathname}` }));
    return;
  }
  response.writeHead(200, { "Content-Type": "application/json" });
  response.end(JSON.stringify(payload));
  });
  server.listen(port, "127.0.0.1");
  return server;
}

function detail() {
  return {
    id: "definition_entry_v1",
    processCode: "entry_application",
    processName: "入金申请",
    version: 1,
    definitionStatus,
    activationStatus,
    nodes: graph.nodes,
    edges: graph.edges,
    formFields: graph.formFields,
    attachmentTemplates: graph.attachmentConfigs.map((config) => ({ ...template, ...config })),
  };
}

function adminUser() {
  return {
    userId: "u_admin_01",
    username: "admin01",
    realName: "系统管理员一",
    departmentId: "dept_company",
    departmentName: "总公司",
    userType: "ADMIN",
    administrator: true,
  };
}

async function readBody(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = startMockPlatform();
  for (const signal of ["SIGINT", "SIGTERM"]) {
    process.on(signal, () => server.close(() => process.exit(0)));
  }
}
