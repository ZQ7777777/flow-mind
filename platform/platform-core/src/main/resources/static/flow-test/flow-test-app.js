(function () {
    "use strict";

    if (!window.Vue) {
        document.body.innerHTML = '<div style="padding:24px;font-family:Arial,sans-serif">Vue 加载失败，请检查网络或将 Vue 运行时改为本地静态资源。</div>';
        return;
    }

    var createApp = window.Vue.createApp;

    var FLOW_CONSOLE_SECTIONS = [
        { key: "home", title: "控制台首页", caption: "数据统计与快捷入口" },
        { key: "definitions", title: "流程定义", caption: "定义列表、新建定义、模型设计" },
        { key: "instances", title: "流程实例", caption: "启动、详情、终止、删除" },
        { key: "tasks", title: "任务中心", caption: "待办、已办、活动与历史任务" },
        { key: "tracking", title: "流程追踪", caption: "轨迹、意见、回调事件" },
        { key: "debug", title: "接口调试", caption: "请求参数与响应 JSON" },
        { key: "logs", title: "操作日志", caption: "错误码和执行日志" }
    ];

    var BANK_RECEIPT_TEMPLATE = {
        attachmentTemplateId: "template-bank-receipt",
        attachmentCode: "bankReceipt",
        attachmentName: "银行回单",
        nodeCode: "APPLY",
        required: true,
        minCount: 1,
        maxCount: 1,
        applicableNodeCodes: ["APPLY"],
        allowedTypes: ["pdf", "jpg", "png"],
        maxSizeMb: 10
    };

    var DEFAULT_ATTACHMENT_META = {
        attachmentCode: "bankReceipt",
        fileName: "bank-receipt.pdf",
        contentType: "application/pdf",
        fileSize: 1024,
        storageKey: "flow-test/bank-receipt.pdf"
    };

    function clone(value) {
        return JSON.parse(JSON.stringify(value || {}));
    }

    function hasText(value) {
        return value !== undefined && value !== null && String(value).trim() !== "";
    }

    function splitCsv(value) {
        return String(value || "").split(",").map(function (item) {
            return item.trim();
        }).filter(Boolean);
    }

    function jsonText(value) {
        return JSON.stringify(value || {}, null, 2);
    }

    function nowTime() {
        return new Date().toLocaleTimeString();
    }

    function node(code, name, type, x, y, approverRuleType, approverRuleConfig, sortOrder) {
        return {
            nodeCode: code,
            nodeName: name,
            nodeType: type,
            approverRuleType: approverRuleType || null,
            approverRuleConfig: approverRuleConfig || null,
            multiInstanceMode: type === "USER_TASK" ? "SINGLE" : null,
            positionX: x,
            positionY: y,
            sortOrder: sortOrder
        };
    }

    function edge(code, source, target, sortOrder) {
        return {
            edgeCode: code,
            sourceNodeCode: source,
            targetNodeCode: target,
            conditionExpression: null,
            defaultEdge: false,
            sortOrder: sortOrder
        };
    }

    function formField(code, name, type, required, sortOrder) {
        return {
            fieldCode: code,
            fieldName: name,
            fieldType: type,
            controlType: type === "NUMBER" ? "number" : "input",
            required: required,
            validationRule: null,
            defaultValue: null,
            sortOrder: sortOrder
        };
    }

    function buildDepositTemplateDraft(base) {
        var source = base || {};
        return {
            processCode: source.processCode || "deposit_apply_demo",
            processName: source.processName || "入金申请测试流程",
            systemCode: source.systemCode || "flow-test-page",
            instanceTitleTemplate: source.instanceTitleTemplate || "${starterName}提交的测试申请",
            remark: source.remark || "M0-M3 流程操作测试模板",
            nodes: [
                node("START", "开始节点", "START", 80, 120, null, null, 1),
                node("APPLY", "申请节点", "USER_TASK", 260, 120, "STARTER", "{}", 2),
                node("MANAGER_APPROVE", "部门经理审批", "USER_TASK", 460, 120, "USER", JSON.stringify({ userIds: ["user_manager"] }), 3),
                node("FINANCE_CONFIRM", "财务确认", "USER_TASK", 680, 120, "USER", JSON.stringify({ userIds: ["user_finance"] }), 4),
                node("END", "结束节点", "END", 880, 120, null, null, 5)
            ],
            edges: [
                edge("E_START_APPLY", "START", "APPLY", 1),
                edge("E_APPLY_MANAGER", "APPLY", "MANAGER_APPROVE", 2),
                edge("E_MANAGER_FINANCE", "MANAGER_APPROVE", "FINANCE_CONFIRM", 3),
                edge("E_FINANCE_END", "FINANCE_CONFIRM", "END", 4)
            ],
            formFields: [
                formField("applicantName", "申请人姓名", "STRING", true, 1),
                formField("amount", "入金金额", "NUMBER", true, 2),
                formField("accountNo", "入金账号", "STRING", true, 3)
            ],
            attachmentConfigs: [clone(BANK_RECEIPT_TEMPLATE)]
        };
    }

    function validateBankReceiptAttachment(meta) {
        var issues = [];
        var item = meta || {};
        var fileName = String(item.fileName || "");
        var contentType = String(item.contentType || "");
        var size = Number(item.fileSize || 0);
        var extension = fileName.indexOf(".") >= 0 ? fileName.split(".").pop().toLowerCase() : "";
        var contentTypeOk = contentType === "application/pdf" || contentType === "image/jpeg" || contentType === "image/png";
        var extensionOk = BANK_RECEIPT_TEMPLATE.allowedTypes.indexOf(extension) >= 0;
        if (!hasText(fileName)) {
            issues.push("银行回单为必填附件");
        }
        if (!contentTypeOk && !extensionOk) {
            issues.push("银行回单格式必须为 pdf/jpg/png");
        }
        if (size <= 0) {
            issues.push("银行回单大小必须大于 0");
        }
        if (size > BANK_RECEIPT_TEMPLATE.maxSizeMb * 1024 * 1024) {
            issues.push("银行回单大小不能超过 10MB");
        }
        return issues;
    }

    function deleteInstanceChecks(context, rows) {
        var activeCount = (rows && rows.activeTasks ? rows.activeTasks.length : 0);
        var historyCount = (rows && rows.historyTasks ? rows.historyTasks.length : 0);
        var commentCount = (rows && rows.comments ? rows.comments.length : 0);
        return [
            "instanceId: " + (context.instanceId || "-"),
            "将观察活动任务清理数量: " + activeCount,
            "将观察历史任务清理数量: " + historyCount,
            "将观察审批意见清理数量: " + commentCount,
            "审计日志和回调日志默认保留"
        ];
    }

    function normalizeList(payload) {
        if (!payload) {
            return [];
        }
        if (Array.isArray(payload)) {
            return payload;
        }
        if (Array.isArray(payload.items)) {
            return payload.items;
        }
        if (Array.isArray(payload.records)) {
            return payload.records;
        }
        if (Array.isArray(payload.list)) {
            return payload.list;
        }
        if (Array.isArray(payload.content)) {
            return payload.content;
        }
        if (Array.isArray(payload.data)) {
            return payload.data;
        }
        if (payload.data) {
            return normalizeList(payload.data);
        }
        return [payload];
    }

    createApp({
        components: {
            DataTable: {
                props: ["title", "rows", "columns"],
                emits: ["row-select"],
                template: [
                    '<section class="surface">',
                    '<div class="section-heading inline"><h2>{{ title }}</h2><p>{{ rows ? rows.length : 0 }} 条</p></div>',
                    '<div class="table-host">',
                    '<div v-if="!rows || !rows.length" class="message-box">暂无数据</div>',
                    '<table v-else>',
                    '<thead><tr><th v-for="column in columns" :key="column.key">{{ column.label }}</th></tr></thead>',
                    '<tbody><tr v-for="(row, index) in rows" :key="index" class="clickable" @click="$emit(\'row-select\', row)">',
                    '<td v-for="column in columns" :key="column.key">{{ format(row[column.key]) }}</td>',
                    '</tr></tbody>',
                    '</table>',
                    '</div>',
                    '</section>'
                ].join(""),
                methods: {
                    format: function (value) {
                        if (value === undefined || value === null || value === "") {
                            return "-";
                        }
                        if (typeof value === "object") {
                            return JSON.stringify(value);
                        }
                        return String(value);
                    }
                }
            },
            JsonPanel: {
                props: ["title", "value"],
                template: '<section class="surface"><h2>{{ title }}</h2><pre>{{ format(value) }}</pre></section>',
                methods: {
                    format: function (value) {
                        return JSON.stringify(value || {}, null, 2);
                    }
                }
            }
        },
        data: function () {
            return {
                sections: FLOW_CONSOLE_SECTIONS,
                activeSection: "home",
                environmentName: "本地",
                baseUrl: "/api/platform",
                currentUserId: "user_sales",
                systemCode: "flow-test-page",
                operationMode: "auto",
                manualOperationId: "",
                lastOperationId: "",
                connectionStatus: "未检测",
                showDefinitionDialog: false,
                showRawDefinition: false,
                selectedNodeIndex: 0,
                selectedEdgeIndex: 0,
                definitionConfigTab: "fields",
                connectionMode: false,
                pendingEdgeSourceNodeCode: "",
                nodeDrag: {
                    active: false,
                    nodeCode: "",
                    offsetX: 0,
                    offsetY: 0
                },
                selectedApi: null,
                taskTab: "todo",
                definitionDraft: buildDepositTemplateDraft(),
                nodeForm: {},
                edgeForm: {},
                formFieldsJson: "[]",
                attachmentConfigsJson: "[]",
                draftIssues: [],
                draftValidationText: "尚未校验",
                definitionFilters: {
                    processCode: "",
                    processName: "",
                    definitionStatus: "",
                    activationStatus: "",
                    version: "",
                    creatorUserId: ""
                },
                instanceFilters: {
                    instanceStatus: ""
                },
                taskFilters: {
                    taskId: "",
                    instanceId: "",
                    processCode: "",
                    nodeCode: "",
                    instanceTitle: "",
                    todoSource: ""
                },
                context: {
                    definitionId: "",
                    instanceId: "",
                    instanceStatus: ""
                },
                instanceForm: {
                    instanceTitle: "入金申请 TEST-BIZ-001",
                    starterUserId: "user_sales",
                    starterDeptId: "dept_sales",
                    applicantName: "测试业务员",
                    amount: 100000,
                    accountNo: "TEST-ACCOUNT-001"
                },
                taskForm: {
                    taskId: "",
                    taskVersion: null,
                    comment: "同意"
                },
                selectedTaskVariables: {},
                definitionRows: [],
                instanceRows: [],
                activeTasks: [],
                todoTasks: [],
                completedTasks: [],
                startedInstances: [],
                historyTasks: [],
                comments: [],
                callbackLogs: [],
                operationLogs: [],
                operationLogRows: [],
                toasts: [],
                lastRequest: null,
                lastResponse: {},
                errorMessage: "",
                confirmDialog: {
                    show: false,
                    title: "",
                    message: "",
                    reason: "",
                    checks: [],
                    action: null
                },
                debugRequest: {
                    method: "GET",
                    path: "/definitions",
                    bodyText: "{}"
                },
                definitionColumns: [
                    { key: "definitionId", label: "定义 ID" },
                    { key: "processCode", label: "流程编码" },
                    { key: "processName", label: "流程名称" },
                    { key: "version", label: "版本" },
                    { key: "definitionStatus", label: "定义状态" },
                    { key: "activationStatus", label: "激活状态" },
                    { key: "updatedAt", label: "更新时间" }
                ],
                instanceColumns: [
                    { key: "instanceId", label: "实例 ID" },
                    { key: "processCode", label: "流程编码" },
                    { key: "instanceTitle", label: "标题" },
                    { key: "starterUserId", label: "发起人" },
                    { key: "instanceStatus", label: "状态" },
                    { key: "startedAt", label: "启动时间" }
                ],
                taskColumns: [
                    { key: "taskId", label: "任务 ID" },
                    { key: "instanceId", label: "实例 ID" },
                    { key: "processCode", label: "流程编码" },
                    { key: "nodeCode", label: "节点编码" },
                    { key: "nodeName", label: "节点名称" },
                    { key: "assigneeUserId", label: "审批人" },
                    { key: "todoSource", label: "待办来源" },
                    { key: "taskVersion", label: "任务版本" },
                    { key: "taskStatus", label: "状态" }
                ],
                historyColumns: [
                    { key: "taskId", label: "任务 ID" },
                    { key: "instanceId", label: "实例 ID" },
                    { key: "nodeCode", label: "节点编码" },
                    { key: "nodeName", label: "节点名称" },
                    { key: "operatorUserId", label: "办理人" },
                    { key: "actionType", label: "动作类型" },
                    { key: "comment", label: "审批意见" },
                    { key: "completedAt", label: "完成时间" },
                    { key: "operationId", label: "operationId" }
                ],
                commentColumns: [
                    { key: "taskId", label: "任务 ID" },
                    { key: "nodeCode", label: "节点编码" },
                    { key: "nodeName", label: "节点名称" },
                    { key: "operatorUserId", label: "办理人" },
                    { key: "actionType", label: "动作类型" },
                    { key: "comment", label: "审批意见" },
                    { key: "operationId", label: "operationId" }
                ],
                operationColumns: [
                    { key: "time", label: "时间" },
                    { key: "label", label: "操作类型" },
                    { key: "method", label: "方法" },
                    { key: "path", label: "地址" },
                    { key: "operationId", label: "operationId" },
                    { key: "status", label: "状态" },
                    { key: "durationMs", label: "耗时" }
                ],
                callbackColumns: [
                    { key: "eventType", label: "事件类型" },
                    { key: "operationId", label: "operationId" },
                    { key: "eventId", label: "eventId" },
                    { key: "instanceId", label: "实例 ID" },
                    { key: "status", label: "状态" },
                    { key: "createdAt", label: "创建时间" }
                ],
                apiCatalog: [
                    { name: "查询定义", method: "GET", path: "/definitions", body: null },
                    { name: "创建定义", method: "POST", path: "/definitions", body: { processCode: "deposit_apply_demo", processName: "入金申请测试流程", systemCode: "flow-test-page" } },
                    { name: "保存流程图", method: "PUT", path: "/definitions/{definitionId}/graph", body: {} },
                    { name: "发布前校验", method: "GET", path: "/definitions/{definitionId}/publish-validation", body: null },
                    { name: "发布定义", method: "POST", path: "/definitions/publish", body: { definitionId: "{definitionId}" } },
                    { name: "激活定义", method: "POST", path: "/definitions/activate", body: { definitionId: "{definitionId}" } },
                    { name: "停用定义", method: "POST", path: "/definitions/deactivate", body: { definitionId: "{definitionId}" } },
                    { name: "归档定义", method: "POST", path: "/definitions/archive", body: { definitionId: "{definitionId}" } },
                    { name: "删除定义", method: "DELETE", path: "/definitions", body: { definitionId: "{definitionId}" } },
                    { name: "启动实例", method: "POST", path: "/runtime/instances/start", body: {} },
                    { name: "启动并提交", method: "POST", path: "/runtime/instances/start-submit", body: {} },
                    { name: "审批通过", method: "POST", path: "/runtime/tasks/approve", body: { taskId: "{taskId}" } },
                    { name: "更新变量", method: "PUT", path: "/runtime/instances/variables", body: { instanceId: "{instanceId}", variables: {} } },
                    { name: "终止实例", method: "POST", path: "/runtime/instances/terminate", body: { instanceId: "{instanceId}" } },
                    { name: "删除实例", method: "DELETE", path: "/runtime/instances", body: { instanceId: "{instanceId}" } },
                    { name: "实例详情", method: "GET", path: "/runtime/instances/{instanceId}", body: null },
                    { name: "待办查询", method: "GET", path: "/tasks/todo", body: null },
                    { name: "已办查询", method: "GET", path: "/tasks/completed", body: null },
                    { name: "我发起查询", method: "GET", path: "/instances/started", body: null },
                    { name: "回调日志", method: "GET", path: "/callbacks/logs", body: null }
                ]
            };
        },
        computed: {
            connectionStatusClass: function () {
                if (this.connectionStatus === "连接正常") {
                    return "ok";
                }
                if (this.connectionStatus === "连接失败") {
                    return "fail";
                }
                return "";
            },
            orderedNodes: function () {
                return clone(this.definitionDraft.nodes).sort(function (a, b) {
                    return Number(a.sortOrder || 0) - Number(b.sortOrder || 0);
                });
            },
            canvasWidth: function () {
                var maxX = 0;
                this.definitionDraft.nodes.forEach(function (item) {
                    maxX = Math.max(maxX, Number(item.positionX || 0));
                });
                return Math.max(1000, maxX + 260);
            },
            canvasHeight: function () {
                var maxY = 0;
                this.definitionDraft.nodes.forEach(function (item) {
                    maxY = Math.max(maxY, Number(item.positionY || 0));
                });
                return Math.max(420, maxY + 180);
            },
            canvasEdges: function () {
                var self = this;
                return this.definitionDraft.edges.map(function (item) {
                    var source = self.canvasNodeByCode(item.sourceNodeCode);
                    var target = self.canvasNodeByCode(item.targetNodeCode);
                    if (!source || !target) {
                        return null;
                    }
                    return {
                        edgeCode: item.edgeCode,
                        x1: Number(source.positionX || 0) + 150,
                        y1: Number(source.positionY || 0) + 41,
                        x2: Number(target.positionX || 0),
                        y2: Number(target.positionY || 0) + 41
                    };
                }).filter(function (item) {
                    return item != null;
                });
            },
            definitionCreateSteps: function () {
                var baseDone = hasText(this.definitionDraft.processCode)
                        && hasText(this.definitionDraft.processName)
                        && hasText(this.definitionDraft.systemCode)
                        && hasText(this.currentUserId);
                var nodeDone = this.definitionDraft.nodes.length >= 3;
                var edgeDone = this.definitionDraft.edges.length >= Math.max(0, this.definitionDraft.nodes.length - 1);
                var configDone = (this.definitionDraft.formFields || []).length > 0
                        && (this.definitionDraft.attachmentConfigs || []).length > 0;
                var validationDone = this.draftValidationText === "本地配置校验通过" && !this.draftIssues.length;
                var rows = [
                    { index: 1, label: "基础信息", detail: this.definitionDraft.processCode || "未填写流程编码", done: baseDone },
                    { index: 2, label: "可视化模型", detail: this.definitionDraft.nodes.length + " 个节点", done: nodeDone },
                    { index: 3, label: "连线配置", detail: this.definitionDraft.edges.length + " 条连线", done: edgeDone },
                    { index: 4, label: "扩展配置", detail: (this.definitionDraft.formFields || []).length + " 个字段，" + (this.definitionDraft.attachmentConfigs || []).length + " 个附件模板", done: configDone },
                    { index: 5, label: "校验保存", detail: validationDone ? "可创建并保存" : "等待本地校验", done: validationDone }
                ];
                var activeMarked = false;
                rows.forEach(function (item) {
                    item.active = !activeMarked && !item.done;
                    activeMarked = activeMarked || item.active;
                });
                if (!activeMarked && rows.length) {
                    rows[rows.length - 1].active = true;
                }
                return rows;
            },
            selectedTaskId: function () {
                return this.taskForm.taskId || (this.activeTasks[0] && this.activeTasks[0].taskId) || "";
            },
            selectedTaskVersionText: function () {
                var version = this.taskForm.taskVersion;
                if (version === null || version === undefined || version === "") {
                    version = this.activeTasks[0] && this.activeTasks[0].taskVersion;
                }
                return version === null || version === undefined || version === "" ? "-" : String(version);
            },
            selectedTaskFieldRows: function () {
                var self = this;
                var fields = this.definitionDraft.formFields && this.definitionDraft.formFields.length
                        ? this.definitionDraft.formFields
                        : DEPOSIT_TEMPLATE.formFields;
                var variables = this.selectedTaskVariables || {};
                return fields.map(function (field) {
                    var code = field.fieldCode || field.code || field.name || "";
                    return {
                        code: code,
                        label: field.fieldName || field.label || code,
                        value: self.formatFieldValue(variables[code])
                    };
                });
            },
            dashboardStats: function () {
                return [
                    { label: "流程定义总数", value: this.definitionRows.length },
                    { label: "已发布定义数", value: this.countRows(this.definitionRows, "definitionStatus", "PUBLISHED") },
                    { label: "已激活定义数", value: this.countRows(this.definitionRows, "activationStatus", "ACTIVE") },
                    { label: "运行中实例数", value: this.countRows(this.instanceRows, "instanceStatus", "RUNNING") },
                    { label: "当前待办任务数", value: this.todoTasks.length },
                    { label: "今日异常操作数", value: this.operationLogs.filter(function (item) { return !item.success; }).length }
                ];
            },
            coverageItems: function () {
                return [
                    { title: "M0 契约", detail: "operationId、expectedTaskVersion、错误码、回调 eventId" },
                    { title: "M1 定义", detail: "CRUD、复制、节点、连线、表单字段、附件模板" },
                    { title: "M2 运行", detail: "启动、申请节点、审批通过、历史归档、流程轨迹" },
                    { title: "M3 查询", detail: "待办、已办、我发起、活动任务、终止和删除实例" }
                ];
            },
            timelineRows: function () {
                var rows = [];
                var self = this;
                this.historyTasks.forEach(function (item, index) {
                    rows.push({
                        id: "history-" + index,
                        time: item.completedAt || item.createdAt || "-",
                        title: (item.nodeName || item.nodeCode || "历史任务") + " " + (item.actionType || ""),
                        detail: "办理人 " + (item.operatorUserId || item.assigneeUserId || "-") + "；operationId " + (item.operationId || "-")
                    });
                });
                this.activeTasks.forEach(function (item, index) {
                    rows.push({
                        id: "active-" + index,
                        time: item.createdAt || "-",
                        title: (item.nodeName || item.nodeCode || "活动任务") + " 待处理",
                        detail: "审批人 " + (item.assigneeUserId || "-") + "；任务版本 " + (item.taskVersion == null ? "-" : item.taskVersion)
                    });
                });
                if (!rows.length && self.context.instanceId) {
                    rows.push({ id: "instance", time: "-", title: "实例已选择", detail: self.context.instanceId });
                }
                return rows;
            },
            lastRequestPreview: function () {
                if (!this.lastRequest) {
                    return {};
                }
                return {
                    method: this.lastRequest.method,
                    path: this.lastRequest.path,
                    body: this.lastRequest.body || null
                };
            }
        },
        mounted: function () {
            this.loadDraftForms();
        },
        methods: {
            openDefinitionDialog: function () {
                this.showDefinitionDialog = true;
                this.loadDraftForms();
            },
            closeDefinitionDialog: function () {
                this.showDefinitionDialog = false;
            },
            loadDepositTemplate: function () {
                this.definitionDraft = buildDepositTemplateDraft(this.definitionDraft);
                this.selectedNodeIndex = 0;
                this.selectedEdgeIndex = 0;
                this.loadDraftForms();
                this.showToast("已载入入金申请模板");
            },
            clearDefinitionDraft: function () {
                this.definitionDraft = {
                    processCode: this.definitionDraft.processCode || "custom_process",
                    processName: this.definitionDraft.processName || "自定义流程",
                    systemCode: this.systemCode,
                    instanceTitleTemplate: "${starterName}提交的测试申请",
                    remark: "",
                    nodes: [],
                    edges: [],
                    formFields: [],
                    attachmentConfigs: []
                };
                this.loadDraftForms();
            },
            loadDraftForms: function () {
                this.formFieldsJson = jsonText(this.definitionDraft.formFields || []);
                this.attachmentConfigsJson = jsonText(this.definitionDraft.attachmentConfigs || []);
                this.selectNode(Math.min(this.selectedNodeIndex, Math.max(this.definitionDraft.nodes.length - 1, 0)));
                this.selectEdge(Math.min(this.selectedEdgeIndex, Math.max(this.definitionDraft.edges.length - 1, 0)));
            },
            selectNode: function (index) {
                this.selectedNodeIndex = index;
                var item = this.definitionDraft.nodes[index] || {};
                this.nodeForm = {
                    nodeCode: item.nodeCode || "",
                    nodeName: item.nodeName || "",
                    nodeType: item.nodeType || "USER_TASK",
                    approverRuleType: item.approverRuleType || "",
                    approverUserIds: this.userIdsFromRuleConfig(item.approverRuleConfig),
                    approverRuleConfig: item.approverRuleConfig || "",
                    positionX: item.positionX == null ? 100 : item.positionX,
                    positionY: item.positionY == null ? 100 : item.positionY,
                    sortOrder: item.sortOrder == null ? index + 1 : item.sortOrder
                };
            },
            selectNodeByCode: function (nodeCode) {
                for (var i = 0; i < this.definitionDraft.nodes.length; i++) {
                    if (this.definitionDraft.nodes[i].nodeCode === nodeCode) {
                        this.selectNode(i);
                        return;
                    }
                }
            },
            selectEdge: function (index) {
                this.selectedEdgeIndex = index;
                var item = this.definitionDraft.edges[index] || {};
                this.edgeForm = {
                    edgeCode: item.edgeCode || "",
                    sourceNodeCode: item.sourceNodeCode || "",
                    targetNodeCode: item.targetNodeCode || "",
                    conditionExpression: item.conditionExpression || "",
                    defaultEdge: Boolean(item.defaultEdge),
                    sortOrder: item.sortOrder == null ? index + 1 : item.sortOrder
                };
            },
            selectEdgeByCode: function (edgeCode) {
                for (var i = 0; i < this.definitionDraft.edges.length; i++) {
                    if (this.definitionDraft.edges[i].edgeCode === edgeCode) {
                        this.selectEdge(i);
                        return;
                    }
                }
            },
            canvasNodeByCode: function (nodeCode) {
                for (var i = 0; i < this.definitionDraft.nodes.length; i++) {
                    if (this.definitionDraft.nodes[i].nodeCode === nodeCode) {
                        return this.definitionDraft.nodes[i];
                    }
                }
                return null;
            },
            draftNodeClass: function (item) {
                var selected = this.definitionDraft.nodes[this.selectedNodeIndex];
                return {
                    selected: selected && selected.nodeCode === item.nodeCode,
                    start: item.nodeType === "START",
                    task: item.nodeType === "USER_TASK",
                    end: item.nodeType === "END"
                };
            },
            draftNodeStyle: function (item) {
                return {
                    position: "absolute",
                    left: Number(item.positionX == null ? 80 : item.positionX) + "px",
                    top: Number(item.positionY == null ? 120 : item.positionY) + "px"
                };
            },
            beginNodeDrag: function (event, nodeCode) {
                if (this.connectionMode) {
                    return;
                }
                var node = this.canvasNodeByCode(nodeCode);
                if (!node) {
                    return;
                }
                this.selectNodeByCode(nodeCode);
                var canvas = event.currentTarget.parentElement;
                var rect = canvas.getBoundingClientRect();
                this.nodeDrag = {
                    active: true,
                    nodeCode: nodeCode,
                    offsetX: event.clientX - rect.left + canvas.scrollLeft - Number(node.positionX || 0),
                    offsetY: event.clientY - rect.top + canvas.scrollTop - Number(node.positionY || 0)
                };
            },
            dragDraftNode: function (event) {
                if (!this.nodeDrag.active) {
                    return;
                }
                var node = this.canvasNodeByCode(this.nodeDrag.nodeCode);
                if (!node) {
                    return;
                }
                var rect = event.currentTarget.getBoundingClientRect();
                node.positionX = Math.max(20, Math.round(event.clientX - rect.left
                        + event.currentTarget.scrollLeft - this.nodeDrag.offsetX));
                node.positionY = Math.max(20, Math.round(event.clientY - rect.top
                        + event.currentTarget.scrollTop - this.nodeDrag.offsetY));
                if (this.definitionDraft.nodes[this.selectedNodeIndex]
                        && this.definitionDraft.nodes[this.selectedNodeIndex].nodeCode === node.nodeCode) {
                    this.nodeForm.positionX = node.positionX;
                    this.nodeForm.positionY = node.positionY;
                }
            },
            finishNodeDrag: function () {
                if (!this.nodeDrag.active) {
                    return;
                }
                this.nodeDrag = {
                    active: false,
                    nodeCode: "",
                    offsetX: 0,
                    offsetY: 0
                };
                this.saveDraftGraphAfterCanvasChange("节点坐标已更新");
            },
            beginEdgeConnect: function () {
                this.connectionMode = !this.connectionMode;
                this.pendingEdgeSourceNodeCode = "";
                this.showToast(this.connectionMode ? "请选择连线起点节点" : "已退出连线模式");
            },
            handleCanvasNodeClick: function (nodeCode) {
                if (!this.connectionMode) {
                    this.selectNodeByCode(nodeCode);
                    return;
                }
                if (!hasText(this.pendingEdgeSourceNodeCode)) {
                    this.pendingEdgeSourceNodeCode = nodeCode;
                    this.selectNodeByCode(nodeCode);
                    this.showToast("请选择连线终点节点");
                    return;
                }
                if (this.pendingEdgeSourceNodeCode === nodeCode) {
                    this.showToast("连线起点和终点不能相同", "warn");
                    return;
                }
                this.createDraftEdgeBetween(this.pendingEdgeSourceNodeCode, nodeCode);
                this.pendingEdgeSourceNodeCode = "";
                this.connectionMode = false;
                this.saveDraftGraphAfterCanvasChange("连线已创建");
            },
            createDraftEdgeBetween: function (sourceNodeCode, targetNodeCode) {
                var baseCode = "E_" + sourceNodeCode + "_" + targetNodeCode;
                var edgeCode = baseCode;
                var suffix = 2;
                while (this.definitionDraft.edges.some(function (item) { return item.edgeCode === edgeCode; })) {
                    edgeCode = baseCode + "_" + suffix;
                    suffix += 1;
                }
                this.definitionDraft.edges.push(edge(edgeCode, sourceNodeCode, targetNodeCode,
                        this.definitionDraft.edges.length + 1));
                this.selectEdge(this.definitionDraft.edges.length - 1);
            },
            saveDraftGraphAfterCanvasChange: function (message) {
                if (!this.context.definitionId) {
                    this.showToast(message + "，创建定义时提交");
                    return Promise.resolve(false);
                }
                return this.saveGraph();
            },
            layoutDraftGraph: function () {
                var byCode = {};
                this.definitionDraft.nodes.forEach(function (item) {
                    byCode[item.nodeCode] = item;
                });
                this.orderedNodes.forEach(function (item, index) {
                    if (byCode[item.nodeCode]) {
                        byCode[item.nodeCode].positionX = 80 + index * 190;
                        byCode[item.nodeCode].positionY = 130;
                        byCode[item.nodeCode].sortOrder = index + 1;
                    }
                });
                this.selectNode(Math.min(this.selectedNodeIndex, Math.max(this.definitionDraft.nodes.length - 1, 0)));
                this.saveDraftGraphAfterCanvasChange("自动布局已应用");
            },
            addDraftNode: function () {
                var index = this.definitionDraft.nodes.length + 1;
                this.definitionDraft.nodes.push(node("TASK_" + index, "审批节点" + index, "USER_TASK", 180 + index * 120, 120, "USER", JSON.stringify({ userIds: ["user_manager"] }), index));
                this.selectNode(this.definitionDraft.nodes.length - 1);
            },
            duplicateDraftNode: function () {
                var source = this.definitionDraft.nodes[this.selectedNodeIndex];
                if (!source) {
                    return;
                }
                var copy = clone(source);
                copy.nodeCode = copy.nodeCode + "_COPY";
                copy.nodeName = copy.nodeName + "复制";
                copy.sortOrder = this.definitionDraft.nodes.length + 1;
                this.definitionDraft.nodes.push(copy);
                this.selectNode(this.definitionDraft.nodes.length - 1);
            },
            deleteDraftNode: function () {
                var removed = this.definitionDraft.nodes.splice(this.selectedNodeIndex, 1)[0];
                if (removed) {
                    this.definitionDraft.edges = this.definitionDraft.edges.filter(function (item) {
                        return item.sourceNodeCode !== removed.nodeCode && item.targetNodeCode !== removed.nodeCode;
                    });
                }
                this.renumberNodes();
                this.selectNode(Math.max(0, this.selectedNodeIndex - 1));
            },
            saveDraftNode: function () {
                var type = this.nodeForm.nodeType;
                var ruleType = type === "USER_TASK" ? this.nodeForm.approverRuleType : null;
                var config = this.nodeForm.approverRuleConfig;
                if (ruleType === "USER" && hasText(this.nodeForm.approverUserIds)) {
                    config = JSON.stringify({ userIds: splitCsv(this.nodeForm.approverUserIds) });
                }
                if (ruleType === "STARTER") {
                    config = config || "{}";
                }
                var saved = {
                    nodeCode: this.nodeForm.nodeCode,
                    nodeName: this.nodeForm.nodeName,
                    nodeType: type,
                    approverRuleType: ruleType,
                    approverRuleConfig: config || null,
                    multiInstanceMode: type === "USER_TASK" ? "SINGLE" : null,
                    positionX: Number(this.nodeForm.positionX || 0),
                    positionY: Number(this.nodeForm.positionY || 0),
                    sortOrder: Number(this.nodeForm.sortOrder || this.selectedNodeIndex + 1)
                };
                if (this.definitionDraft.nodes.length) {
                    this.definitionDraft.nodes.splice(this.selectedNodeIndex, 1, saved);
                } else {
                    this.definitionDraft.nodes.push(saved);
                    this.selectedNodeIndex = 0;
                }
                this.showToast("节点已保存");
            },
            addDraftEdge: function () {
                var index = this.definitionDraft.edges.length + 1;
                var nodes = this.definitionDraft.nodes;
                this.definitionDraft.edges.push(edge("E_" + index, nodes[0] ? nodes[0].nodeCode : "", nodes[1] ? nodes[1].nodeCode : "", index));
                this.selectEdge(this.definitionDraft.edges.length - 1);
            },
            deleteDraftEdge: function () {
                this.definitionDraft.edges.splice(this.selectedEdgeIndex, 1);
                this.selectEdge(Math.max(0, this.selectedEdgeIndex - 1));
            },
            saveDraftEdge: function () {
                var saved = {
                    edgeCode: this.edgeForm.edgeCode,
                    sourceNodeCode: this.edgeForm.sourceNodeCode,
                    targetNodeCode: this.edgeForm.targetNodeCode,
                    conditionExpression: this.edgeForm.conditionExpression || null,
                    defaultEdge: Boolean(this.edgeForm.defaultEdge),
                    sortOrder: Number(this.edgeForm.sortOrder || this.selectedEdgeIndex + 1)
                };
                if (this.definitionDraft.edges.length) {
                    this.definitionDraft.edges.splice(this.selectedEdgeIndex, 1, saved);
                } else {
                    this.definitionDraft.edges.push(saved);
                    this.selectedEdgeIndex = 0;
                }
                this.showToast("连线已保存");
            },
            renumberNodes: function () {
                this.definitionDraft.nodes.forEach(function (item, index) {
                    item.sortOrder = index + 1;
                });
            },
            validateDefinitionDraftAction: function () {
                try {
                    this.syncDraftJson();
                    this.draftIssues = this.validateDefinitionDraft();
                } catch (error) {
                    this.draftIssues = [error.message];
                }
                this.draftValidationText = this.draftIssues.length ? "本地配置校验未通过" : "本地配置校验通过";
                this.showToast(this.draftValidationText, this.draftIssues.length ? "warn" : "success");
            },
            validateDefinitionDraft: function () {
                var issues = [];
                var codes = {};
                if (!hasText(this.definitionDraft.processCode)) {
                    issues.push("流程编码不能为空");
                }
                if (!hasText(this.definitionDraft.processName)) {
                    issues.push("流程名称不能为空");
                }
                if (!hasText(this.definitionDraft.systemCode)) {
                    issues.push("系统编码不能为空");
                }
                if (!this.definitionDraft.nodes.length) {
                    issues.push("至少需要一个节点");
                }
                var startCount = 0;
                var endCount = 0;
                this.definitionDraft.nodes.forEach(function (item) {
                    if (!hasText(item.nodeCode)) {
                        issues.push("节点编码不能为空");
                    }
                    if (codes[item.nodeCode]) {
                        issues.push("节点编码重复：" + item.nodeCode);
                    }
                    codes[item.nodeCode] = true;
                    if (!hasText(item.nodeName)) {
                        issues.push("节点名称不能为空：" + (item.nodeCode || "-"));
                    }
                    if (item.nodeType === "START") {
                        startCount += 1;
                    }
                    if (item.nodeType === "END") {
                        endCount += 1;
                    }
                    if (item.nodeType === "USER_TASK" && !hasText(item.approverRuleType)) {
                        issues.push("用户任务必须配置审批规则：" + item.nodeCode);
                    }
                });
                if (startCount !== 1) {
                    issues.push("必须存在且仅存在一个开始节点");
                }
                if (endCount < 1) {
                    issues.push("至少存在一个结束节点");
                }
                var edgeCodes = {};
                this.definitionDraft.edges.forEach(function (item) {
                    if (!hasText(item.edgeCode)) {
                        issues.push("连线编码不能为空");
                    }
                    if (edgeCodes[item.edgeCode]) {
                        issues.push("连线编码重复：" + item.edgeCode);
                    }
                    edgeCodes[item.edgeCode] = true;
                    if (!codes[item.sourceNodeCode]) {
                        issues.push("连线来源节点不存在：" + item.edgeCode);
                    }
                    if (!codes[item.targetNodeCode]) {
                        issues.push("连线目标节点不存在：" + item.edgeCode);
                    }
                    if (item.sourceNodeCode === item.targetNodeCode) {
                        issues.push("连线来源和目标不能相同：" + item.edgeCode);
                    }
                });
                return issues;
            },
            syncDraftJson: function () {
                this.definitionDraft.formFields = this.parseJsonArray(this.formFieldsJson, "表单字段 JSON");
                this.definitionDraft.attachmentConfigs = this.parseJsonArray(this.attachmentConfigsJson, "附件模板 JSON");
            },
            buildGraphRequestBody: function () {
                this.syncDraftJson();
                return {
                    nodes: clone(this.definitionDraft.nodes),
                    edges: clone(this.definitionDraft.edges),
                    formFields: clone(this.definitionDraft.formFields),
                    attachmentConfigs: clone(this.definitionDraft.attachmentConfigs),
                    operatorUserId: this.currentUserId
                };
            },
            confirmCreateAndSave: function () {
                var self = this;
                try {
                    self.syncDraftJson();
                } catch (error) {
                    self.draftIssues = [error.message];
                    self.draftValidationText = "本地配置校验未通过";
                    self.showToast(error.message, "warn");
                    return Promise.resolve(false);
                }
                self.draftIssues = self.validateDefinitionDraft();
                if (self.draftIssues.length) {
                    self.showToast("本地配置校验未通过", "warn");
                    return Promise.resolve(false);
                }
                return self.sendOperation("创建定义", "POST", "/definitions", {
                    processCode: self.definitionDraft.processCode,
                    processName: self.definitionDraft.processName,
                    systemCode: self.definitionDraft.systemCode,
                    remark: self.definitionDraft.remark,
                    operatorUserId: self.currentUserId
                }).then(function () {
                    return self.saveGraph();
                }).then(function () {
                    self.showDefinitionDialog = false;
                    self.showToast("保存成功");
                });
            },
            saveGraph: function () {
                if (!this.context.definitionId) {
                    this.showToast("请先创建定义", "warn");
                    return Promise.resolve(false);
                }
                return this.sendOperation("保存流程图", "PUT", "/definitions/" + encodeURIComponent(this.context.definitionId) + "/graph", this.buildGraphRequestBody())
                        .then(this.bindThis(function () {
                            this.showToast("保存成功");
                        }));
            },
            validateDefinition: function () {
                if (!this.context.definitionId) {
                    this.showToast("请先选择或创建定义", "warn");
                    return Promise.resolve(false);
                }
                return this.saveGraph().then(this.bindThis(function () {
                    return this.sendRequest("发布前校验", "GET",
                            "/definitions/" + encodeURIComponent(this.context.definitionId) + "/publish-validation", null);
                }));
            },
            definitionOperation: function (label, action) {
                if (!this.context.definitionId) {
                    this.showToast("请先选择或创建定义", "warn");
                    return Promise.resolve(false);
                }
                return this.sendOperation(label, "POST", "/definitions/" + action, {
                    definitionId: this.context.definitionId,
                    operatorUserId: this.currentUserId
                });
            },
            copyDefinition: function () {
                if (!this.context.definitionId) {
                    this.showToast("请先选择定义", "warn");
                    return Promise.resolve(false);
                }
                return this.sendOperation("复制定义", "POST", "/definitions/" + encodeURIComponent(this.context.definitionId) + "/copy", {
                    operatorUserId: this.currentUserId,
                    processCode: this.definitionDraft.processCode,
                    processName: this.definitionDraft.processName + "复制"
                });
            },
            deleteDefinition: function () {
                var self = this;
                if (!self.context.definitionId) {
                    self.showToast("请先选择定义", "warn");
                    return Promise.resolve(false);
                }
                self.openConfirm("删除定义", "将删除流程定义及其关联节点、连线、表单字段、附件模板和运行数据。", function (reason) {
                    return self.sendOperation("删除定义", "DELETE", "/definitions", {
                        definitionId: self.context.definitionId,
                        operatorUserId: self.currentUserId,
                        reason: reason
                    });
                });
                return Promise.resolve(true);
            },
            startInstance: function () {
                return this.sendOperation("启动实例", "POST", "/runtime/instances/start", this.buildStartInstanceBody());
            },
            startAndSubmitInstance: function () {
                var issues = validateBankReceiptAttachment(this.buildAttachmentMeta());
                if (issues.length) {
                    this.showToast("银行回单附件模板校验未通过", "warn");
                    this.errorMessage = issues.join("；");
                    return Promise.resolve(false);
                }
                return this.sendOperation("启动并提交", "POST", "/runtime/instances/start-submit", this.buildStartAndSubmitBody());
            },
            buildStartInstanceBody: function () {
                return {
                    processCode: this.definitionDraft.processCode,
                    instanceTitle: this.instanceForm.instanceTitle,
                    starterUserId: this.instanceForm.starterUserId,
                    starterDeptId: this.instanceForm.starterDeptId,
                    variables: this.buildVariables()
                };
            },
            buildStartAndSubmitBody: function () {
                var body = this.buildStartInstanceBody();
                body.attachments = [this.buildAttachmentMeta()];
                return body;
            },
            approveCurrentTask: function () {
                return this.sendTaskOperation("审批通过当前任务", "approve", {});
            },
            approveWithStaleVersion: function () {
                var task = this.selectedTask();
                task.taskVersion = Math.max(0, Number(task.taskVersion || 0) - 1);
                return this.sendTaskOperation("使用过期任务版本审批", "approve", {}, task);
            },
            simulateConcurrentApprove: function () {
                var task = this.selectedTask();
                var requestA = this.buildTaskRequest("并发审批 A", "approve", {}, task);
                var requestB = this.buildTaskRequest("并发审批 B", "approve", {}, task);
                requestA.body.operationId = this.createOperationId("concurrent-a");
                requestB.body.operationId = this.createOperationId("concurrent-b");
                return Promise.all([
                    this.sendPreparedRequest("并发审批 A", requestA).catch(function (error) { return error; }),
                    this.sendPreparedRequest("并发审批 B", requestB).catch(function (error) { return error; })
                ]);
            },
            updateVariables: function () {
                if (!this.context.instanceId) {
                    this.showToast("请先启动或选择实例", "warn");
                    return Promise.resolve(false);
                }
                return this.sendOperation("更新测试变量", "PUT", "/runtime/instances/variables", {
                    instanceId: this.context.instanceId,
                    variables: this.buildVariables()
                });
            },
            terminateInstance: function () {
                var self = this;
                if (!self.context.instanceId) {
                    self.showToast("请先启动或选择实例", "warn");
                    return Promise.resolve(false);
                }
                self.openConfirm("终止实例", "将终止当前实例并观察活动任务清理结果。", function (reason) {
                    return self.sendOperation("终止实例", "POST", "/runtime/instances/terminate", {
                        instanceId: self.context.instanceId,
                        operatorUserId: self.currentUserId,
                        reason: reason
                    });
                });
                return Promise.resolve(true);
            },
            deleteInstance: function () {
                var self = this;
                if (!self.context.instanceId) {
                    self.showToast("请先启动或选择实例", "warn");
                    return Promise.resolve(false);
                }
                self.openConfirm("删除实例", "将删除当前流程实例，并观察活动任务、历史任务和审批意见清理结果。", function (reason) {
                    return self.sendOperation("删除实例", "DELETE", "/runtime/instances", {
                        instanceId: self.context.instanceId,
                        operatorUserId: self.currentUserId,
                        reason: reason
                    });
                }, deleteInstanceChecks(self.context, {
                    activeTasks: self.activeTasks,
                    historyTasks: self.historyTasks,
                    comments: self.comments
                }));
                return Promise.resolve(true);
            },
            queryDefinitions: function () {
                return this.sendRequest("查询定义", "GET", "/definitions" + this.toQuery(this.withPage(this.definitionFilters)), null)
                        .then(this.bindThis(function (payload) {
                            this.definitionRows = normalizeList(payload);
                        }));
            },
            queryInstances: function () {
                var params = this.withPage({
                    starterUserId: this.instanceForm.starterUserId || this.currentUserId,
                    processCode: this.definitionDraft.processCode,
                    instanceStatus: this.instanceFilters.instanceStatus,
                    instanceTitle: this.instanceForm.instanceTitle
                });
                return this.sendRequest("查询实例", "GET", "/instances/started" + this.toQuery(params), null)
                        .then(this.bindThis(function (payload) {
                            this.instanceRows = normalizeList(payload);
                        }));
            },
            queryTodoTasks: function () {
                this.taskTab = "todo";
                var params = this.withPage({
                    userId: this.currentUserId,
                    processCode: this.taskFilters.processCode || this.definitionDraft.processCode,
                    nodeCode: this.taskFilters.nodeCode,
                    instanceTitle: this.taskFilters.instanceTitle,
                    todoSource: this.taskFilters.todoSource
                });
                return this.sendRequest("查待办", "GET", "/tasks/todo" + this.toQuery(params), null)
                        .then(this.bindThis(function (payload) {
                            this.todoTasks = normalizeList(payload);
                        }));
            },
            queryCompletedTasks: function () {
                this.taskTab = "done";
                var params = this.withPage({
                    userId: this.currentUserId,
                    processCode: this.taskFilters.processCode || this.definitionDraft.processCode,
                    nodeCode: this.taskFilters.nodeCode,
                    instanceTitle: this.taskFilters.instanceTitle
                });
                return this.sendRequest("查已办", "GET", "/tasks/completed" + this.toQuery(params), null)
                        .then(this.bindThis(function (payload) {
                            this.completedTasks = normalizeList(payload);
                        }));
            },
            queryStartedInstances: function () {
                this.taskTab = "started";
                var params = this.withPage({
                    starterUserId: this.currentUserId,
                    processCode: this.definitionDraft.processCode,
                    instanceStatus: this.instanceFilters.instanceStatus,
                    instanceTitle: this.instanceForm.instanceTitle
                });
                return this.sendRequest("查我发起", "GET", "/instances/started" + this.toQuery(params), null)
                        .then(this.bindThis(function (payload) {
                            this.startedInstances = normalizeList(payload);
                        }));
            },
            queryActiveTasks: function () {
                this.taskTab = "active";
                if (!this.context.instanceId) {
                    this.showToast("请先启动或选择实例", "warn");
                    return Promise.resolve(false);
                }
                return this.sendRequest("查活动任务", "GET", "/instances/" + encodeURIComponent(this.context.instanceId) + "/active-tasks", null)
                        .then(this.bindThis(function (payload) {
                            this.activeTasks = normalizeList(payload);
                            this.updateCurrentTaskFromList(this.activeTasks);
                        }));
            },
            queryHistoryTasks: function () {
                this.taskTab = "history";
                if (!this.context.instanceId) {
                    this.showToast("请先启动或选择实例", "warn");
                    return Promise.resolve(false);
                }
                return this.sendRequest("查历史任务", "GET", "/instances/" + encodeURIComponent(this.context.instanceId) + "/history-tasks", null)
                        .then(this.bindThis(function (payload) {
                            this.historyTasks = normalizeList(payload);
                        }));
            },
            queryComments: function () {
                if (!this.context.instanceId) {
                    this.showToast("请先启动或选择实例", "warn");
                    return Promise.resolve(false);
                }
                return this.sendRequest("查审批意见", "GET", "/instances/" + encodeURIComponent(this.context.instanceId) + "/comments", null)
                        .then(this.bindThis(function (payload) {
                            this.comments = normalizeList(payload);
                        }));
            },
            queryCallbackLogs: function () {
                return this.sendRequest("查回调日志", "GET", "/callbacks/logs" + this.toQuery(this.withPage({ instanceId: this.context.instanceId })), null)
                        .then(this.bindThis(function (payload) {
                            this.callbackLogs = normalizeList(payload);
                        }));
            },
            queryOperationLogs: function () {
                return this.markEndpointUnavailable("查操作日志", "操作日志查询接口当前代码暂未提供，本页保留浏览器侧操作流水。");
            },
            getDefinition: function () {
                if (!this.context.definitionId) {
                    this.showToast("请先选择定义", "warn");
                    return Promise.resolve(false);
                }
                return this.sendRequest("查定义详情", "GET", "/definitions/" + encodeURIComponent(this.context.definitionId), null)
                        .then(this.bindThis(function (payload) {
                            this.applyDefinitionResult(payload);
                        }));
            },
            getInstance: function () {
                if (!this.context.instanceId) {
                    this.showToast("请先启动或选择实例", "warn");
                    return Promise.resolve(false);
                }
                return this.sendRequest("查实例详情", "GET", "/runtime/instances/" + encodeURIComponent(this.context.instanceId), null)
                        .then(this.bindThis(function (payload) {
                            this.applyInstanceResult(payload);
                        }));
            },
            pingBackend: function () {
                var self = this;
                self.connectionStatus = "检测中";
                return self.sendRequest("检测连接", "GET", "/definitions?pageNo=1&pageSize=1", null)
                        .then(function () {
                            self.connectionStatus = "连接正常";
                        }).catch(function () {
                            self.connectionStatus = "连接失败";
                        });
            },
            refreshDefinitionCache: function () {
                return this.sendOperation("刷新流程定义缓存", "POST", "/admin/definition-cache/refresh", {
                    operatorUserId: this.currentUserId
                });
            },
            clearTestData: function () {
                return this.sendOperation("清理测试数据", "POST", "/admin/test-data/clear", {
                    operatorUserId: this.currentUserId
                });
            },
            repeatLastRequest: function () {
                if (!this.lastRequest) {
                    this.showToast("暂无可重复请求", "warn");
                    return Promise.resolve(false);
                }
                return this.sendPreparedRequest("重复上次请求", clone(this.lastRequest));
            },
            conflictOperationId: function () {
                if (!this.lastRequest) {
                    this.showToast("暂无可复用 operationId 的请求", "warn");
                    return Promise.resolve(false);
                }
                var request = clone(this.lastRequest);
                request.body = request.body || {};
                request.body.comment = "同号不同请求 " + new Date().toISOString();
                return this.sendPreparedRequest("同号不同请求", request);
            },
            sendTaskOperation: function (label, actionName, extraBody, overrideTask) {
                return this.sendPreparedRequest(label, this.buildTaskRequest(label, actionName, extraBody, overrideTask));
            },
            buildTaskRequest: function (label, actionName, extraBody, overrideTask) {
                var selected = overrideTask || this.selectedTask();
                var body = {
                    taskId: selected.taskId,
                    expectedTaskVersion: Number(selected.taskVersion),
                    operatorUserId: this.currentUserId,
                    comment: this.taskForm.comment || label
                };
                Object.keys(extraBody || {}).forEach(function (key) {
                    body[key] = extraBody[key];
                });
                return {
                    label: label,
                    method: "POST",
                    path: "/runtime/tasks/" + actionName,
                    body: this.addOperationId(body)
                };
            },
            markEndpointUnavailable: function (label, message) {
                var request = { label: label, method: "-", path: "当前代码暂未提供", body: null };
                this.lastRequest = clone(request);
                this.lastResponse = { code: "ENDPOINT_NOT_READY", message: message };
                this.errorMessage = message;
                this.recordOperation(label, request, "NOT_READY", this.lastResponse, false, 0);
                this.showToast(message, "warn");
                return Promise.resolve(false);
            },
            sendOperation: function (label, method, path, body) {
                return this.sendRequest(label, method, path, this.addOperationId(body || {}));
            },
            sendRequest: function (label, method, path, body) {
                return this.sendPreparedRequest(label, { label: label, method: method, path: path, body: body });
            },
            sendPreparedRequest: function (label, request) {
                var self = this;
                var started = Date.now();
                var headers = { Accept: "application/json" };
                headers["X-Flow-User-Id"] = self.currentUserId;
                var options = { method: request.method, headers: headers };
                if (request.body) {
                    headers["Content-Type"] = "application/json";
                    if (request.body.operationId) {
                        headers["Idempotency-Key"] = request.body.operationId;
                    }
                    options.body = JSON.stringify(request.body);
                }
                self.lastRequest = clone(request);
                self.errorMessage = "";
                return fetch(self.trimTrailingSlash(self.baseUrl) + self.resolvePath(request.path), options)
                        .then(function (response) {
                            return response.text().then(function (text) {
                                var payload = text ? self.parseJsonOrRaw(text) : {};
                                if (!response.ok) {
                                    throw { status: response.status, payload: payload };
                                }
                                self.lastResponse = payload;
                                self.recordOperation(label, request, response.status, payload, true, Date.now() - started);
                                self.applyResult(payload);
                                return payload;
                            });
                        })
                        .catch(function (error) {
                            self.lastResponse = error.payload || { message: error.message || "请求失败" };
                            self.errorMessage = self.errorText(label, request, error);
                            self.showToast("请求失败", "error");
                            self.recordOperation(label, request, error.status || "ERR", self.lastResponse, false, Date.now() - started);
                            return Promise.reject(error);
                        });
            },
            applyResult: function (payload) {
                if (!payload || typeof payload !== "object") {
                    return;
                }
                if (payload.definitionId || payload.id || payload.processCode) {
                    this.applyDefinitionResult(payload);
                }
                if (payload.instance || payload.instanceId) {
                    this.applyInstanceResult(payload);
                }
                if (payload.createdTasks) {
                    this.activeTasks = normalizeList(payload.createdTasks);
                    this.updateCurrentTaskFromList(this.activeTasks);
                }
                if (payload.archivedTasks) {
                    this.historyTasks = normalizeList(payload.archivedTasks).concat(this.historyTasks || []);
                }
                if (payload.callbackLogs) {
                    this.callbackLogs = normalizeList(payload.callbackLogs);
                }
            },
            applyDefinitionResult: function (payload) {
                var source = payload.data && !payload.definitionId ? payload.data : payload;
                this.context.definitionId = this.readFirst(source, ["definitionId", "id"], this.context.definitionId);
                this.definitionDraft.processCode = this.readFirst(source, ["processCode"], this.definitionDraft.processCode);
                this.definitionDraft.processName = this.readFirst(source, ["processName"], this.definitionDraft.processName);
                this.definitionDraft.systemCode = this.readFirst(source, ["systemCode"], this.definitionDraft.systemCode);
            },
            applyInstanceResult: function (payload) {
                var instance = payload.instance || payload.data || payload;
                this.context.instanceId = this.readFirst(instance, ["instanceId", "id"], this.context.instanceId);
                this.context.instanceStatus = this.readFirst(instance, ["instanceStatus", "status"], this.context.instanceStatus);
                if (instance.createdTasks) {
                    this.activeTasks = normalizeList(instance.createdTasks);
                    this.updateCurrentTaskFromList(this.activeTasks);
                }
                if (instance.activeTasks) {
                    this.activeTasks = normalizeList(instance.activeTasks);
                    this.updateCurrentTaskFromList(this.activeTasks);
                }
                if (instance.historyTasks) {
                    this.historyTasks = normalizeList(instance.historyTasks);
                }
                if (instance.comments) {
                    this.comments = normalizeList(instance.comments);
                }
            },
            selectDefinitionRow: function (row) {
                this.applyDefinitionResult(row);
                this.showToast("已选择定义");
            },
            selectInstanceRow: function (row) {
                this.applyInstanceResult(row);
                this.showToast("已选择实例");
            },
            selectTaskRow: function (row) {
                this.taskForm.taskId = row.taskId || row.activeTaskId || "";
                this.taskForm.taskVersion = row.taskVersion == null ? null : row.taskVersion;
                if (row.instanceId) {
                    this.context.instanceId = row.instanceId;
                }
                this.showToast("已选择任务");
                return this.loadSelectedTaskVariables(row);
            },
            loadSelectedTaskVariables: function (row) {
                var self = this;
                var selectedTaskId = self.taskForm.taskId;
                var selectedTaskVersion = self.taskForm.taskVersion;
                var snapshot = row.variablesSnapshot || row.variables;
                if (snapshot && typeof snapshot === "object") {
                    self.applySelectedTaskVariables(snapshot);
                    return Promise.resolve(true);
                }
                self.applySelectedTaskVariables({});
                if (!row.instanceId) {
                    return Promise.resolve(false);
                }
                return self.sendRequest("读取任务表单字段", "GET", "/runtime/instances/" + encodeURIComponent(row.instanceId), null)
                        .then(function (payload) {
                            var instance = payload.instance || payload.data || payload;
                            self.applySelectedTaskVariables(instance.variables || {});
                            self.taskForm.taskId = selectedTaskId;
                            self.taskForm.taskVersion = selectedTaskVersion;
                            return true;
                        })
                        .catch(function () {
                            self.taskForm.taskId = selectedTaskId;
                            self.taskForm.taskVersion = selectedTaskVersion;
                            return false;
                        });
            },
            applySelectedTaskVariables: function (variables) {
                this.selectedTaskVariables = clone(variables || {});
            },
            selectedTask: function () {
                var task = {
                    taskId: this.taskForm.taskId || (this.activeTasks[0] && this.activeTasks[0].taskId),
                    taskVersion: this.taskForm.taskVersion
                };
                if ((task.taskVersion === null || task.taskVersion === undefined || task.taskVersion === "") && this.activeTasks[0]) {
                    task.taskVersion = this.activeTasks[0].taskVersion;
                }
                if (!task.taskId) {
                    throw new Error("请先启动或查询活动任务");
                }
                if (task.taskVersion === undefined || task.taskVersion === null || task.taskVersion === "") {
                    throw new Error("缺少任务版本");
                }
                return task;
            },
            updateCurrentTaskFromList: function (rows) {
                if (rows && rows.length) {
                    this.taskForm.taskId = rows[0].taskId || "";
                    this.taskForm.taskVersion = rows[0].taskVersion == null ? null : rows[0].taskVersion;
                }
            },
            buildVariables: function () {
                return {
                    applicantName: this.instanceForm.applicantName,
                    amount: Number(this.instanceForm.amount || 0),
                    accountNo: this.instanceForm.accountNo
                };
            },
            buildAttachmentMeta: function () {
                return clone(DEFAULT_ATTACHMENT_META);
            },
            openConfirm: function (title, message, action, checks) {
                this.confirmDialog = {
                    show: true,
                    title: title,
                    message: message,
                    reason: "",
                    checks: checks || [],
                    action: action
                };
            },
            closeConfirm: function () {
                this.confirmDialog.show = false;
            },
            runConfirmAction: function () {
                var action = this.confirmDialog.action;
                var reason = this.confirmDialog.reason || "测试操作";
                this.closeConfirm();
                if (typeof action === "function") {
                    return action(reason);
                }
                return Promise.resolve(false);
            },
            selectApi: function (api) {
                this.selectedApi = api;
                this.debugRequest.method = api.method;
                this.debugRequest.path = api.path;
                this.debugRequest.bodyText = jsonText(api.body || {});
            },
            sendDebugRequest: function () {
                var body = null;
                if (this.debugRequest.method !== "GET") {
                    body = this.addOperationId(this.resolveBodyPlaceholders(this.parseJsonObject(this.debugRequest.bodyText, "请求体 JSON")));
                }
                return this.sendRequest("接口调试", this.debugRequest.method, this.debugRequest.path, body);
            },
            resetDefinitionFilters: function () {
                this.definitionFilters = {
                    processCode: "",
                    processName: "",
                    definitionStatus: "",
                    activationStatus: "",
                    version: "",
                    creatorUserId: ""
                };
            },
            flowNodeClass: function (item) {
                if (this.activeTasks.some(function (task) { return task.nodeCode === item.nodeCode; })) {
                    return "active";
                }
                if (this.historyTasks.some(function (task) { return task.nodeCode === item.nodeCode; })) {
                    return "done";
                }
                return "";
            },
            flowNodeStatus: function (item) {
                var active = this.activeTasks.find(function (task) { return task.nodeCode === item.nodeCode; });
                if (active) {
                    return "活动任务 v" + (active.taskVersion == null ? "-" : active.taskVersion);
                }
                if (this.historyTasks.some(function (task) { return task.nodeCode === item.nodeCode; })) {
                    return "已完成";
                }
                if (item.nodeType === "END" && this.context.instanceStatus === "COMPLETED") {
                    return "已办结";
                }
                return item.nodeType;
            },
            countRows: function (rows, key, value) {
                return (rows || []).filter(function (item) {
                    return item[key] === value;
                }).length;
            },
            withPage: function (params) {
                var copy = clone(params || {});
                copy.pageNo = copy.pageNo || 1;
                copy.pageSize = copy.pageSize || 20;
                return copy;
            },
            toQuery: function (params) {
                var pairs = Object.keys(params || {}).filter(function (key) {
                    return params[key] !== undefined && params[key] !== null && params[key] !== "";
                }).map(function (key) {
                    return encodeURIComponent(key) + "=" + encodeURIComponent(params[key]);
                });
                return pairs.length ? "?" + pairs.join("&") : "";
            },
            addOperationId: function (body) {
                var copy = clone(body || {});
                copy.operationId = this.nextOperationId();
                return copy;
            },
            nextOperationId: function () {
                if (this.operationMode === "reuse" && this.lastOperationId) {
                    return this.lastOperationId;
                }
                if (this.operationMode === "manual" && hasText(this.manualOperationId)) {
                    this.lastOperationId = this.manualOperationId;
                    return this.manualOperationId;
                }
                this.lastOperationId = this.createOperationId("op");
                this.manualOperationId = this.lastOperationId;
                return this.lastOperationId;
            },
            createOperationId: function (prefix) {
                return prefix + "-" + Date.now() + "-" + Math.floor(Math.random() * 100000);
            },
            recordOperation: function (label, request, status, payload, success, durationMs) {
                this.operationLogs.unshift({
                    id: Date.now() + Math.random(),
                    time: nowTime(),
                    label: label,
                    method: request.method,
                    path: request.path,
                    operationId: request.body && request.body.operationId,
                    status: status,
                    durationMs: durationMs,
                    replayed: Boolean(payload && payload.replayed),
                    success: success
                });
                this.operationLogs = this.operationLogs.slice(0, 60);
            },
            showToast: function (message, type) {
                var self = this;
                var item = { id: Date.now() + Math.random(), message: message || "操作完成", type: type || "success" };
                self.toasts.push(item);
                window.setTimeout(function () {
                    self.toasts = self.toasts.filter(function (candidate) {
                        return candidate.id !== item.id;
                    });
                }, 2600);
            },
            parseJsonArray: function (text, label) {
                var value = this.parseJsonObject(text, label);
                if (!Array.isArray(value)) {
                    throw new Error(label + " 必须是数组");
                }
                return value;
            },
            formatJson: function (value) {
                return JSON.stringify(value || {}, null, 2);
            },
            formatFieldValue: function (value) {
                if (value === undefined || value === null || value === "") {
                    return "-";
                }
                if (typeof value === "object") {
                    return JSON.stringify(value);
                }
                return String(value);
            },
            parseJsonObject: function (text, label) {
                try {
                    return JSON.parse(text || "{}");
                } catch (error) {
                    throw new Error(label + " 格式错误");
                }
            },
            parseJsonOrRaw: function (text) {
                try {
                    return JSON.parse(text);
                } catch (error) {
                    return { raw: text };
                }
            },
            readFirst: function (object, keys, fallback) {
                if (!object) {
                    return fallback;
                }
                for (var i = 0; i < keys.length; i += 1) {
                    if (object[keys[i]] !== undefined && object[keys[i]] !== null) {
                        return object[keys[i]];
                    }
                }
                return fallback;
            },
            resolveBodyPlaceholders: function (value) {
                var self = this;
                if (Array.isArray(value)) {
                    return value.map(function (item) {
                        return self.resolveBodyPlaceholders(item);
                    });
                }
                if (value && typeof value === "object") {
                    var copy = {};
                    Object.keys(value).forEach(function (key) {
                        copy[key] = self.resolveBodyPlaceholders(value[key]);
                    });
                    return copy;
                }
                if (value === "{definitionId}") {
                    return self.context.definitionId;
                }
                if (value === "{instanceId}") {
                    return self.context.instanceId;
                }
                if (value === "{taskId}") {
                    return self.selectedTaskId;
                }
                return value;
            },
            userIdsFromRuleConfig: function (config) {
                if (!hasText(config)) {
                    return "";
                }
                try {
                    var json = JSON.parse(config);
                    return Array.isArray(json.userIds) ? json.userIds.join(",") : "";
                } catch (error) {
                    return "";
                }
            },
            resolvePath: function (path) {
                return String(path || "")
                        .replace("{definitionId}", encodeURIComponent(this.context.definitionId || ""))
                        .replace("{instanceId}", encodeURIComponent(this.context.instanceId || ""))
                        .replace("{taskId}", encodeURIComponent(this.selectedTaskId || ""));
            },
            trimTrailingSlash: function (value) {
                return String(value || "").replace(/\/+$/, "");
            },
            errorText: function (label, request, error) {
                var payload = error.payload || {};
                return label + " 失败；路径：" + request.path + "；状态：" + (error.status || "ERR") +
                        "；错误码：" + (payload.code || payload.errorCode || "-") +
                        "；信息：" + (payload.message || payload.errorMessage || error.message || "请求失败");
            },
            bindThis: function (fn) {
                var self = this;
                return function () {
                    return fn.apply(self, arguments);
                };
            }
        }
    }).mount("#app");
}());
