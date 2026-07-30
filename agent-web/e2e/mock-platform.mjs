import { createServer } from "node:http";
import { pathToFileURL } from "node:url";

let definitionStatus = "DRAFT";
let activationStatus = "INACTIVE";
let graph = { nodes: [], edges: [], formFields: [], attachmentConfigs: [] };
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
  const server = createServer(async (request, response) => {
  const url = new URL(request.url || "/", "http://127.0.0.1:18080");
  const body = await readBody(request);
  let payload = {};

  if (url.pathname === "/api/platform/definitions" && request.method === "GET") {
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
