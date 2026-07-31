(function () {
    "use strict";

    var API_PATHS = {
        definitions: "/api/platform/definitions",
        definitionDetail: function (definitionId) {
            return "/api/platform/definitions/" + encodeURIComponent(definitionId);
        },
        saveGraph: function (definitionId) {
            return "/api/platform/definitions/" + encodeURIComponent(definitionId) + "/graph";
        },
        validateDefinition: function (definitionId) {
            return "/api/platform/definitions/" + encodeURIComponent(definitionId) + "/publish-validation";
        },
        copyDefinition: function (definitionId) {
            return "/api/platform/definitions/" + encodeURIComponent(definitionId) + "/copy";
        },
        publishDefinition: "/api/platform/definitions/publish",
        activateDefinition: "/api/platform/definitions/activate",
        deactivateDefinition: "/api/platform/definitions/deactivate",
        archiveDefinition: "/api/platform/definitions/archive",
        deleteDefinition: "/api/platform/definitions",
        startAndSubmit: "/api/platform/runtime/instances/start-submit",
        adminInstances: "/api/platform/admin/instances",
        updateVariables: "/api/platform/runtime/instances/variables",
        terminateInstance: "/api/platform/runtime/instances/terminate",
        deleteInstance: "/api/platform/runtime/instances",
        instanceDetail: function (instanceId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId);
        },
        startedInstances: "/api/platform/instances/started",
        taskSubmit: "/api/platform/runtime/tasks/submit",
        taskApprove: "/api/platform/runtime/tasks/approve",
        taskReject: "/api/platform/runtime/tasks/reject",
        taskDirectSend: "/api/platform/runtime/tasks/direct-send",
        taskTransfer: "/api/platform/runtime/tasks/transfer",
        taskAddSign: "/api/platform/runtime/tasks/add-sign",
        taskWithdraw: "/api/platform/runtime/tasks/withdraw",
        delegateTask: "/api/platform/runtime/tasks/transfer",
        taskRemind: function (taskId) {
            return "/api/platform/tasks/" + encodeURIComponent(taskId) + "/remind";
        },
        taskClaim: "/api/platform/runtime/tasks/claim",
        taskUnclaim: "/api/platform/runtime/tasks/unclaim",
        remindTask: function (taskId) {
            return "/api/platform/tasks/" + encodeURIComponent(taskId) + "/remind";
        },
        directSendContext: function (taskId) {
            return "/api/platform/tasks/" + encodeURIComponent(taskId) + "/direct-send-context";
        },
        todoTasks: "/api/platform/tasks/todo",
        completedTasks: "/api/platform/tasks/completed",
        activeTasks: function (instanceId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId) + "/active-tasks";
        },
        historyTasks: function (instanceId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId) + "/history-tasks";
        },
        comments: function (instanceId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId) + "/comments";
        },
        adminHistoryTasks: "/api/platform/admin/history-tasks",
        callbackLogs: "/api/platform/admin/callback-logs",
        auditLogs: "/api/platform/admin/audit-logs",
        attachments: "/api/platform/attachments",
        instanceAttachments: function (instanceId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId) + "/attachments";
        },
        replaceInstanceAttachment: function (instanceId, attachmentId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId) + "/attachments/"
                + encodeURIComponent(attachmentId);
        },
        attachmentDownload: function (attachmentId) {
            return "/api/platform/attachments/" + encodeURIComponent(attachmentId) + "/download";
        },
        attachmentTemplates: "/api/platform/attachment-templates",
        reminders: "/api/platform/reminders",
        adminTimeoutScan: "/api/platform/admin/timeout-scan",
        readRecords: function (instanceId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId) + "/read-records";
        },
        markRead: function (instanceId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId) + "/read";
        }
    };

    var FLOW_TEST_USERS = [
        {userId: "u_sales_01", userName: "业务员", role: "TEST_OPERATOR", deptId: "dept_sales", departmentId: "dept_sales", deptName: "销售部", roleCodes: ["sales"]},
        {userId: "u_group_leader_01", userName: "组长", role: "TEST_OPERATOR", deptId: "dept_sales", departmentId: "dept_sales", deptName: "销售部", roleCodes: ["group"]},
        {userId: "u_dept_manager_01", userName: "部门经理1", role: "TEST_OPERATOR", deptId: "dept_manager", departmentId: "dept_sales", deptName: "销售部经理", roleCodes: ["manager"]},
        {userId: "u_dept_manager_02", userName: "部门经理2", role: "TEST_OPERATOR", deptId: "dept_manager", departmentId: "dept_finance", deptName: "财务部经理", roleCodes: ["manager"]},
        {userId: "u_finance_01", userName: "财务1", role: "TEST_OPERATOR", deptId: "dept_finance", departmentId: "dept_finance", deptName: "财务部",  roleCodes: ["finance"]},
        {userId: "u_finance_02", userName: "财务2", role: "TEST_OPERATOR", deptId: "dept_finance", departmentId: "dept_finance", deptName: "财务部",  roleCodes: ["finance"]},
        {userId: "u_ceo_01", userName: "CEO", role: "TEST_OPERATOR", deptId: "mock-dept", departmentId: "mock-dept", deptName: "Mock Department", departmentName: "Mock Department", roleCodes: ["admin"]},
        {userId: "u_admin_01", userName: "测试管理员", role: "TEST_ADMIN", deptId: "mock-dept", departmentId: "mock-dept", deptName: "Mock Department", departmentName: "Mock Department", roleCodes: ["admin"]}
    ];

    var FLOW_TEST_DEPARTMENTS = [
        {departmentId: "dept_sales", departmentName: "销售部"},
        {departmentId: "dept_finance", departmentName: "财务部"},
        {departmentId: "mock-dept", departmentName: "默认部门"}
    ];

    var FLOW_TEST_ROLES = [
        {roleCode: "manager", roleName: "经理"},
        {roleCode: "finance", roleName: "财务"},
        {roleCode: "sales", roleName: "业务"},
        {roleCode: "admin", roleName: "管理员"}
    ];

    var DEFAULT_TEMPLATE_LIBRARY = [
        {
            attachmentTemplateId: "tpl_bank_receipt_v1",
            attachmentCode: "bankReceipt",
            templateVersion: 1,
            attachmentName: "银行回单",
            allowedExtensions: ["pdf", "jpg", "png"],
            maxSizeBytes: 10485760
        },
        {
            attachmentTemplateId: "tpl_invoice_v1",
            attachmentCode: "invoice",
            templateVersion: 1,
            attachmentName: "发票",
            allowedExtensions: ["pdf", "jpg", "png"],
            maxSizeBytes: 10485760
        }
    ];

    var idSeed = 1;
    var CANVAS_MIN_WIDTH = 960;
    var CANVAS_READONLY_MIN_HEIGHT = 480;
    var CANVAS_DESIGNER_MIN_HEIGHT = 480;
    var CANVAS_NODE_WIDTH = 132;
    var CANVAS_NODE_HEIGHT = 58;
    var CANVAS_PADDING = 96;

    function nextLocalId(prefix) {
        idSeed += 1;
        return prefix + "_" + Date.now() + "_" + idSeed;
    }

    function hasText(value) {
        return value !== null && value !== undefined && String(value).trim() !== "";
    }

    function clone(value) {
        return JSON.parse(JSON.stringify(value));
    }

    function asArray(value) {
        if (Array.isArray(value)) {
            return value;
        }
        if (!hasText(value)) {
            return [];
        }
        if (typeof value === "string") {
            try {
                var parsed = JSON.parse(value);
                return Array.isArray(parsed) ? parsed : [value];
            } catch (ignore) {
                return value.split(",").map(function (item) {
                    return item.trim();
                }).filter(hasText);
            }
        }
        return [value];
    }

    function normalizeList(payload) {
        if (Array.isArray(payload)) {
            return payload;
        }
        if (!payload) {
            return [];
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
        if (payload.data) {
            return normalizeList(payload.data);
        }
        return [];
    }

    function extractTotalCount(payload, fallback) {
        if (!payload) {
            return fallback || 0;
        }
        var keys = ["total", "totalCount", "totalElements", "count"];
        for (var index = 0; index < keys.length; index += 1) {
            var value = payload[keys[index]];
            if (hasText(value)) {
                var parsed = Number(value);
                if (!Number.isNaN(parsed)) {
                    return parsed;
                }
            }
        }
        if (payload.data) {
            return extractTotalCount(payload.data, fallback);
        }
        return fallback || 0;
    }

    function toQuery(params) {
        var search = new URLSearchParams();
        Object.keys(params || {}).forEach(function (key) {
            var value = params[key];
            if (Array.isArray(value)) {
                if (value.length > 0) {
                    search.set(key, value.join(","));
                }
            } else if (hasText(value)) {
                search.set(key, value);
            }
        });
        var text = search.toString();
        return text ? "?" + text : "";
    }

    function parseJsonObject(text, fallback) {
        if (!hasText(text)) {
            return fallback || {};
        }
        try {
            var value = JSON.parse(text);
            return value && typeof value === "object" && !Array.isArray(value) ? value : (fallback || {});
        } catch (error) {
            throw new Error("JSON 格式不正确：" + error.message);
        }
    }

    function stringifyRule(value) {
        if (!value) {
            return "{}";
        }
        if (typeof value === "string") {
            return hasText(value) ? value : "{}";
        }
        return JSON.stringify(value);
    }

    function parseRuleConfig(value) {
        if (!hasText(value)) {
            return {};
        }
        if (typeof value === "string") {
            try {
                return parseJsonObject(value, {});
            } catch (ignore) {
                return {};
            }
        }
        return isJsonObject(value) ? value : {};
    }

    function approverExpressionText(value) {
        var expression = hasText(value) ? String(value).trim() : "";
        if (expression === "经理审批") {
            return "departmentManager(starterDeptId)";
        }
        return expression;
    }

    function userDepartmentId(user) {
        return user ? (user.departmentId || user.deptId || "") : "";
    }

    function normalizeMultiInstanceMode(mode, approverCount) {
        if (mode === "OR_SIGN" || mode === "COUNTERSIGN") {
            return mode;
        }
        return approverCount > 1 ? "OR_SIGN" : "SINGLE";
    }

    function isJsonObject(value) {
        return value !== null && typeof value === "object" && !Array.isArray(value);
    }

    function normalizeListenerConfigValue(value) {
        if (!hasText(value)) {
            return "";
        }
        return typeof value === "string" ? value : JSON.stringify(value);
    }

    function parseListenerConfig(value) {
        var text = normalizeListenerConfigValue(value);
        if (!hasText(text)) {
            return {config: {}, error: ""};
        }
        try {
            var config = JSON.parse(text);
            if (!isJsonObject(config)) {
                return {config: {}, error: "listenerConfig 必须是 JSON 对象"};
            }
            return {config: config, error: ""};
        } catch (error) {
            return {config: {}, error: "listenerConfig JSON 格式不正确：" + error.message};
        }
    }

    function readListenerRuleEditor(value) {
        var parsed = parseListenerConfig(value);
        var editor = {
            listenerRejectEnabled: false,
            listenerRejectTargetNodeCodes: [],
            listenerDirectSendEnabled: false,
            listenerConfigError: parsed.error
        };
        if (parsed.error) {
            return editor;
        }
        var rules = parsed.config.taskActionRules;
        if (rules === undefined || rules === null) {
            return editor;
        }
        if (!isJsonObject(rules)) {
            editor.listenerConfigError = "listenerConfig.taskActionRules 必须是 JSON 对象";
            return editor;
        }
        if (rules.reject !== undefined && rules.reject !== null) {
            if (!isJsonObject(rules.reject)) {
                editor.listenerConfigError = "listenerConfig.taskActionRules.reject 必须是 JSON 对象";
                return editor;
            }
            editor.listenerRejectEnabled = rules.reject.enabled === true;
            editor.listenerRejectTargetNodeCodes = Array.isArray(rules.reject.targetNodeCodes)
                ? rules.reject.targetNodeCodes.filter(hasText) : [];
        }
        if (rules.directSend !== undefined && rules.directSend !== null) {
            if (!isJsonObject(rules.directSend)) {
                editor.listenerConfigError = "listenerConfig.taskActionRules.directSend 必须是 JSON 对象";
                return editor;
            }
            editor.listenerDirectSendEnabled = rules.directSend.enabled === true;
        }
        return editor;
    }

    function applyListenerRuleEditor(node) {
        var editor = readListenerRuleEditor(node.listenerConfig);
        node.listenerRejectEnabled = editor.listenerRejectEnabled;
        node.listenerRejectTargetNodeCodes = editor.listenerRejectTargetNodeCodes;
        node.listenerDirectSendEnabled = editor.listenerDirectSendEnabled;
        node.listenerConfigError = editor.listenerConfigError;
        return node;
    }

    function extractDefinitionId(row) {
        return row && (row.definitionId || row.id);
    }

    function extractInstanceId(row) {
        return row && (row.instanceId || row.id);
    }

    function extractTaskId(row) {
        return row && (row.taskId || row.id);
    }

    function extractTaskVersion(row) {
        return row && (row.taskVersion || row.expectedTaskVersion || row.lockVersion || 0);
    }

    function nowText() {
        return new Date().toLocaleString("zh-CN", {hour12: false});
    }

    function defaultDefinitionDraft() {
        return {
            definitionId: "",
            processCode: "entry_application_" + Date.now(),
            processName: "入金申请测试流程",
            systemCode: "newoa-demo",
            remark: "",
            nodes: [
                buildNode("start", "开始", "START", 80, 160, 1),
                buildNode("apply", "申请", "USER_TASK", 260, 160, 2, "STARTER", [], "SINGLE"),
                buildNode("end", "结束", "END", 520, 160, 3)
            ],
            edges: [
                buildEdge("edge_start_apply", "start", "apply", 1),
                buildEdge("edge_apply_end", "apply", "end", 2)
            ],
            formFields: [
                {
                    localId: nextLocalId("field"),
                    fieldCode: "amount",
                    fieldName: "金额",
                    fieldType: "number",
                    controlType: "number",
                    required: true
                }
            ],
            attachmentConfigs: []
        };
    }

    function buildNode(nodeCode, nodeName, nodeType, x, y, sortOrder, ruleType, selectedApproverIds, mode) {
        return {
            localId: nextLocalId("node"),
            nodeCode: nodeCode,
            nodeName: nodeName,
            nodeType: nodeType,
            pairedGatewayCode: "",
            approverRuleType: ruleType || (nodeType === "USER_TASK" ? "USER" : ""),
            approverRuleConfig: stringifyRule(ruleType === "STARTER" ? {} : {userIds: selectedApproverIds || []}),
            selectedApproverIds: selectedApproverIds || [],
            selectedDepartmentId: "",
            selectedRoleCode: "",
            selectedRoleDepartmentMode: "STARTER",
            selectedRoleDepartmentId: "",
            approverExpression: "",
            multiInstanceMode: mode || "SINGLE",
            listenerConfig: "",
            listenerRejectEnabled: false,
            listenerRejectTargetNodeCodes: [],
            listenerDirectSendEnabled: false,
            listenerConfigError: "",
            positionX: x,
            positionY: y,
            sortOrder: sortOrder
        };
    }

    function buildEdge(edgeCode, sourceNodeCode, targetNodeCode, sortOrder) {
        return {
            localId: nextLocalId("edge"),
            edgeCode: edgeCode,
            sourceNodeCode: sourceNodeCode,
            targetNodeCode: targetNodeCode,
            conditionExpression: "",
            defaultEdge: false,
            sortOrder: sortOrder
        };
    }

    function normalizeNode(node, index) {
        var ruleConfig = node.approverRuleConfig;
        var parsedRuleConfig = parseRuleConfig(ruleConfig);
        var approverIds = [];
        var departmentId = "";
        var roleCode = "";
        var roleDepartmentMode = "STARTER";
        var roleDepartmentId = "";
        var approverExpression = "";
        approverIds = asArray(parsedRuleConfig.userIds);
        departmentId = hasText(parsedRuleConfig.departmentId) ? String(parsedRuleConfig.departmentId).trim() : "";
        roleCode = hasText(parsedRuleConfig.roleCode) ? String(parsedRuleConfig.roleCode).trim() : "";
        roleDepartmentId = departmentId;
        roleDepartmentMode = hasText(roleDepartmentId) ? "FIXED" : "STARTER";
        approverExpression = hasText(parsedRuleConfig.expression) ? String(parsedRuleConfig.expression).trim() : "";
        var normalized = Object.assign(buildNode(
            node.nodeCode || ("node_" + index),
            node.nodeName || ("节点" + (index + 1)),
            node.nodeType || "USER_TASK",
            Number(node.positionX || node.x || 120 + index * 180),
            Number(node.positionY || node.y || 160),
            node.sortOrder || index + 1,
            node.approverRuleType || (node.nodeType === "USER_TASK" ? "USER" : ""),
            approverIds,
            node.multiInstanceMode || "SINGLE"
        ), node, {
            localId: node.localId || nextLocalId("node"),
            selectedApproverIds: approverIds,
            selectedDepartmentId: departmentId,
            selectedRoleCode: roleCode,
            selectedRoleDepartmentMode: roleDepartmentMode,
            selectedRoleDepartmentId: roleDepartmentId,
            approverExpression: approverExpression,
            approverRuleConfig: stringifyRule(ruleConfig),
            listenerConfig: normalizeListenerConfigValue(node.listenerConfig)
        });
        return applyListenerRuleEditor(normalized);
    }

    function normalizeEdge(edge, index) {
        return Object.assign(buildEdge(
            edge.edgeCode || ("edge_" + (index + 1)),
            edge.sourceNodeCode,
            edge.targetNodeCode,
            edge.sortOrder || index + 1
        ), edge, {localId: edge.localId || nextLocalId("edge")});
    }

    function normalizeAttachmentConfig(config, index) {
        var template = findTemplate(config.attachmentTemplateId);
        return Object.assign({
            localId: config.localId || nextLocalId("attach"),
            sourceType: hasText(config.sourceType) ? config.sourceType : "existing",
            attachmentConfigId: config.attachmentConfigId || "",
            attachmentTemplateId: config.attachmentTemplateId || "",
            attachmentCode: config.attachmentCode || (template && template.attachmentCode) || "",
            attachmentName: config.attachmentName || (template && template.attachmentName) || "",
            allowedExtensionsText: asArray(config.allowedExtensions || (template && template.allowedExtensions)).join(","),
            maxSizeBytes: config.maxSizeBytes || (template && template.maxSizeBytes) || 10485760,
            required: Boolean(config.required),
            minCount: config.minCount === undefined ? 0 : config.minCount,
            maxCount: config.maxCount === undefined ? 1 : config.maxCount,
            applicableNodeCodes: asArray(config.applicableNodeCodes),
            sortOrder: config.sortOrder || index + 1,
            pendingTemplatePersist: Boolean(config.pendingTemplatePersist)
        }, config);
    }

    function findTemplate(attachmentTemplateId) {
        return DEFAULT_TEMPLATE_LIBRARY.find(function (template) {
            return template.attachmentTemplateId === attachmentTemplateId;
        });
    }

    function responseSummary(payload) {
        if (!payload) {
            return "无响应体";
        }
        if (payload.errorCode === "FLOW_FROZEN_MODEL_PARALLEL_GATEWAY_PAIR_INVALID") {
            return [payload.errorCode, "并行网关配对无效：请在流程图中为并行分支和并行汇聚设置互相配对，或删除会签定义里不需要的并行网关。"].filter(hasText).join(" ");
        }
        if (payload.errorCode || payload.message) {
            return [payload.errorCode, payload.message].filter(hasText).join(" ");
        }
        return JSON.stringify(payload).slice(0, 220);
    }

    var app = Vue.createApp({
        data: function () {
            return {
                API_PATHS: API_PATHS,
                users: FLOW_TEST_USERS,
                departments: FLOW_TEST_DEPARTMENTS,
                roles: FLOW_TEST_ROLES,
                apiBaseUrl: "",
                activeView: "definitions",
                currentUserId: "u_admin_01",
                currentRole: "TEST_ADMIN",
                operationState: {
                    status: "idle",
                    label: "准备就绪",
                    message: "请选择功能开始测试"
                },
                operationLogs: [],
                definitionFilters: {
                    processCode: "",
                    processName: "",
                    systemCode: "newoa-demo",
                    definitionStatus: "",
                    activationStatus: ""
                },
                definitionRows: [],
                selectedDefinitionId: "",
                selectedDefinitionDetail: null,
                selectedReadonlyNodeCode: "",
                definitionDialog: {
                    open: false,
                    mode: "create",
                    tab: "basic",
                    connectionMode: false
                },
                definitionDraft: defaultDefinitionDraft(),
                selectedDesigner: {
                    type: "",
                    code: ""
                },
                connectionClickQueue: [],
                dragState: null,
                attachmentTemplateLibrary: clone(DEFAULT_TEMPLATE_LIBRARY),
                instanceForm: {
                    definitionId: "",
                    processCode: "",
                    businessKey: "",
                    instanceTitle: "入金申请测试实例",
                    starterDeptId: "mock-dept"
                },
                instanceFieldValues: {},
                startFieldErrors: {},
                instanceVariableValues: {},
                instanceAttachments: [],
                instanceRows: [],
                selectedInstanceDetail: null,
                readRecordRows: [],
                historyTaskRows: [],
                commentRows: [],
                callbackLogRows: [],
                auditTraceRows: [],
                instanceQueryDialog: {
                    open: false,
                    queryType: "",
                    title: "",
                    rows: [],
                    pageNo: 1,
                    pageSize: 20,
                    total: 0,
                    loading: false
                },
                instanceOperationComment: "",
                todoRows: [],
                todoScope: "own",
                todoReminderStatusByTaskId: {},
                completedRows: [],
                startSubmitting: false,
                lastStartedInstanceId: "",
                taskDialog: {
                    open: false,
                    task: {},
                    variablesText: "{}",
                    comment: "",
                    attachments: [],
                    starterTask: false,
                    directSendContext: {allowed: false},
                    rejectTargetNodeCode: "",
                    transferUserId: "",
                    delegateUserId: "",
                    addSignUserIds: []
                },
                completedDialog: {
                    open: false,
                    task: {},
                    attachments: [],
                    currentNodeCodes: []
                }
            };
        },
        computed: {
            isTestAdmin: function () {
                return this.currentRole === "TEST_ADMIN";
            },
            canManageDefinitions: function () {
                return this.currentRole === "TEST_ADMIN" || this.currentRole === "DEFINITION_ADMIN";
            },
            canQuerySelectedInstanceReadRecords: function () {
                return !!extractInstanceId(this.selectedInstanceDetail);
            },
            canWithdrawSelectedInstance: function () {
                var previous = this.selectedPreviousHandlerTask;
                return !!previous && previous.assigneeUserId === this.currentUserId;
            },
            selectedPreviousHandlerTask: function () {
                var detail = this.selectedInstanceDetail || {};
                var histories = normalizeList(detail.historyTasks).concat(this.historyTaskRows || []);
                return histories.filter(function (task) {
                    return hasText(task.assigneeUserId) && task.actionType !== "WITHDRAW" && task.handleType !== "WITHDRAW";
                }).sort(function (left, right) {
                    return String(right.completedAt || right.endedAt || right.updatedAt || right.createdAt || right.startedAt || "")
                        .localeCompare(String(left.completedAt || left.endedAt || left.updatedAt || left.createdAt || left.startedAt || ""));
                })[0] || null;
            },
            canAdminOperateSelectedInstance: function () {
                return this.isTestAdmin && !!extractInstanceId(this.selectedInstanceDetail);
            },
            selectedGraphNodes: function () {
                var detail = this.selectedDefinitionDetail || {};
                return (detail.nodes || []).map(normalizeNode);
            },
            selectedGraphEdges: function () {
                var detail = this.selectedDefinitionDetail || {};
                return (detail.edges || []).map(normalizeEdge);
            },
            selectedAttachmentConfigs: function () {
                return this.normalizeAttachmentConfigsFromDetail(this.selectedDefinitionDetail);
            },
            selectedNode: function () {
                if (this.selectedDesigner.type !== "node") {
                    return null;
                }
                return this.definitionDraft.nodes.find(function (node) {
                    return node.nodeCode === this.selectedDesigner.code;
                }, this) || null;
            },
            selectedEdge: function () {
                if (this.selectedDesigner.type !== "edge") {
                    return null;
                }
                return this.definitionDraft.edges.find(function (edge) {
                    return edge.edgeCode === this.selectedDesigner.code;
                }, this) || null;
            },
            userTaskNodes: function () {
                return this.definitionDraft.nodes.filter(this.isUserTaskNode);
            },
            runnableDefinitions: function () {
                return this.definitionRows.filter(function (row) {
                    return row.activationStatus === "ACTIVE" || row.definitionStatus === "PUBLISHED";
                });
            },
            instanceDefinitionFields: function () {
                var definitionId = this.instanceForm.definitionId;
                var detail = this.selectedDefinitionDetail || {};
                var row = this.definitionRows.find(function (definition) {
                    return extractDefinitionId(definition) === definitionId;
                });
                if (extractDefinitionId(detail) === definitionId && Array.isArray(detail.formFields)) {
                    return detail.formFields;
                }
                return row && Array.isArray(row.formFields) ? row.formFields : [];
            },
            selectedInstanceFields: function () {
                var detail = this.selectedInstanceDetail || {};
                var definitionId = detail.definitionId || this.instanceForm.definitionId || this.selectedDefinitionId;
                var selectedDetail = this.selectedDefinitionDetail || {};
                var row = this.definitionRows.find(function (definition) {
                    return extractDefinitionId(definition) === definitionId;
                });
                if (extractDefinitionId(selectedDetail) === definitionId && Array.isArray(selectedDetail.formFields)) {
                    return selectedDetail.formFields;
                }
                return row && Array.isArray(row.formFields) ? row.formFields : [];
            },
            instanceVariableRows: function () {
                return this.selectedInstanceFields.map(function (field) {
                    var fieldCode = this.formFieldCode(field);
                    return {
                        field: field,
                        fieldCode: fieldCode,
                        fieldName: this.formFieldLabel(field),
                        value: this.instanceVariableValues[fieldCode]
                    };
                }, this);
            },
            queryDialogRows: function () {
                return this.instanceQueryDialog.rows || [];
            },
            queryDialogTotalPages: function () {
                var pageSize = Math.max(1, Number(this.instanceQueryDialog.pageSize) || 20);
                var total = Math.max(0, Number(this.instanceQueryDialog.total) || 0);
                return Math.max(1, Math.ceil(total / pageSize));
            },
            filteredTodoRows: function () {
                if (this.todoScope === "delegated") {
                    return this.todoRows.filter(function (row) {
                        return hasText(row.delegateFromUserId);
                    });
                }
                if (this.todoScope === "all") {
                    return this.todoRows;
                }
                return this.todoRows.filter(function (row) {
                    return this.isOwnTodoTask(row);
                }, this);
            },
            instanceDefinitionAttachments: function () {
                var row = this.definitionRows.find(function (definition) {
                    return extractDefinitionId(definition) === this.instanceForm.definitionId;
                }, this);
                if (row && Array.isArray(row.attachmentConfigs)) {
                    return row.attachmentConfigs;
                }
                return this.selectedAttachmentConfigs;
            },
            selectedInstanceActiveTasks: function () {
                return normalizeList(this.selectedInstanceDetail && this.selectedInstanceDetail.activeTasks);
            }
        },
        mounted: function () {
            this.applyCurrentUser();
            this.queryAttachmentTemplates().catch(function () {});
            this.addAttachmentConfig();
            this.addInstanceAttachment();
            this.queryDefinitions().catch(function () {});
        },
        methods: {
            switchView: function (view) {
                this.activeView = view;
                if (view === "definitions") {
                    this.queryDefinitions().catch(function () {});
                } else if (view === "instances") {
                    this.queryInstances().catch(function () {});
                } else if (view === "todo") {
                    this.queryTodoTasks().catch(function () {});
                } else if (view === "completed") {
                    this.queryCompletedTasks().catch(function () {});
                }
            },
            applyCurrentUser: function () {
                var user = this.currentUser();
                if (user) {
                    this.currentRole = user.role;
                    this.instanceForm.starterDeptId = userDepartmentId(user);
                }
                if (this.activeView === "todo") {
                    this.queryTodoTasks().catch(function () {});
                } else if (this.activeView === "completed") {
                    this.queryCompletedTasks().catch(function () {});
                }
            },
            currentUser: function () {
                return this.users.find(function (user) {
                    return user.userId === this.currentUserId;
                }, this) || this.users[0];
            },
            resetDefinitionFilters: function () {
                this.definitionFilters = {
                    processCode: "",
                    processName: "",
                    systemCode: "newoa-demo",
                    definitionStatus: "",
                    activationStatus: ""
                };
                this.queryDefinitions();
            },
            queryDefinitions: function () {
                var url = API_PATHS.definitions + toQuery(Object.assign({pageNo: 1, pageSize: 50}, this.definitionFilters));
                return this.sendRequest("查询流程定义", "GET", url).then(function (payload) {
                    this.definitionRows = normalizeList(payload);
                    if (!this.selectedDefinitionId && this.definitionRows.length > 0) {
                        return this.selectDefinition(this.definitionRows[0]);
                    }
                    return payload;
                }.bind(this));
            },
            selectDefinition: function (row) {
                var definitionId = extractDefinitionId(row);
                if (!definitionId) {
                    return Promise.resolve();
                }
                this.selectedDefinitionId = definitionId;
                return this.sendRequest("查询定义详情", "GET", API_PATHS.definitionDetail(definitionId)).then(function (payload) {
                    this.selectedDefinitionDetail = payload || row;
                    this.captureTemplatesFromDetail(payload);
                    return payload;
                }.bind(this));
            },
            openDefinitionDialog: function () {
                this.definitionDraft = defaultDefinitionDraft();
                this.selectedDesigner = {type: "", code: ""};
                this.connectionClickQueue = [];
                this.definitionDialog = {open: true, mode: "create", tab: "basic", connectionMode: false};
            },
            closeDefinitionDialog: function () {
                this.definitionDialog.open = false;
            },
            editDefinitionAttachments: function (row) {
                this.selectDefinition(row).then(function () {
                    this.definitionDraft = this.definitionDraftFromDetail(this.selectedDefinitionDetail);
                    this.definitionDialog = {open: true, mode: "attachments", tab: "attachments", connectionMode: false};
                }.bind(this));
            },
            definitionDraftFromDetail: function (detail) {
                var source = detail || {};
                return {
                    definitionId: source.definitionId || source.id || this.selectedDefinitionId,
                    processCode: source.processCode || "",
                    processName: source.processName || "",
                    systemCode: source.systemCode || "newoa-demo",
                    remark: source.remark || "",
                    nodes: (source.nodes || []).map(normalizeNode),
                    edges: (source.edges || []).map(normalizeEdge),
                    formFields: (source.formFields || []).map(function (field, index) {
                        return Object.assign({localId: nextLocalId("field"), sortOrder: index + 1}, field);
                    }),
                    attachmentConfigs: this.normalizeAttachmentConfigsFromDetail(source)
                };
            },
            addDesignerNode: function (nodeType) {
                var index = this.definitionDraft.nodes.length + 1;
                var code = nodeType.toLowerCase() + "_" + index;
                var nameMap = {
                    START: "开始",
                    USER_TASK: "用户任务",
                    EXCLUSIVE_GATEWAY: "排他网关",
                    PARALLEL_SPLIT_GATEWAY: "并行分支",
                    PARALLEL_JOIN_GATEWAY: "并行汇聚",
                    END: "结束"
                };
                var node = buildNode(code, nameMap[nodeType] || "节点", nodeType, 120 + index * 34, 90 + index * 28, index);
                this.definitionDraft.nodes.push(node);
                this.selectedDesigner = {type: "node", code: node.nodeCode};
                this.setOperationState("success", "本地草稿已更新", "节点已添加，保存后写入后端");
            },
            beginNodeDrag: function (event, node) {
                if (this.definitionDialog.connectionMode) {
                    return;
                }
                this.selectedDesigner = {type: "node", code: node.nodeCode};
                this.dragState = {
                    node: node,
                    startX: event.clientX,
                    startY: event.clientY,
                    originalX: Number(node.positionX || 0),
                    originalY: Number(node.positionY || 0),
                    moved: false
                };
                if (event.currentTarget.setPointerCapture) {
                    event.currentTarget.setPointerCapture(event.pointerId);
                }
            },
            dragDraftNode: function (event) {
                if (!this.dragState) {
                    return;
                }
                var deltaX = event.clientX - this.dragState.startX;
                var deltaY = event.clientY - this.dragState.startY;
                if (Math.abs(deltaX) > 2 || Math.abs(deltaY) > 2) {
                    this.dragState.moved = true;
                }
                this.dragState.node.positionX = Math.max(0, this.dragState.originalX + deltaX);
                this.dragState.node.positionY = Math.max(0, this.dragState.originalY + deltaY);
            },
            finishNodeDrag: function () {
                if (this.dragState && this.dragState.moved) {
                    this.setOperationState("success", "本地草稿已更新", "节点坐标已更新，保存后写入节点表");
                }
                this.dragState = null;
            },
            startConnectionMode: function () {
                this.definitionDialog.connectionMode = true;
                this.connectionClickQueue = [];
                this.setOperationState("idle", "连线模式", "按 1-2、3-4 的顺序点击节点创建连线");
            },
            stopConnectionMode: function () {
                this.definitionDialog.connectionMode = false;
                this.connectionClickQueue = [];
                this.setOperationState("idle", "准备就绪", "未配对节点已忽略");
            },
            handleCanvasNodeClick: function (nodeCode) {
                if (this.dragState && this.dragState.moved) {
                    return;
                }
                if (!this.definitionDialog.connectionMode) {
                    this.selectedDesigner = {type: "node", code: nodeCode};
                    return;
                }
                this.connectionClickQueue.push(nodeCode);
                this.selectedDesigner = {type: "node", code: nodeCode};
                if (this.connectionClickQueue.length % 2 === 0) {
                    var sourceNodeCode = this.connectionClickQueue[this.connectionClickQueue.length - 2];
                    var targetNodeCode = this.connectionClickQueue[this.connectionClickQueue.length - 1];
                    if (sourceNodeCode !== targetNodeCode) {
                        var edgeCode = "edge_" + sourceNodeCode + "_" + targetNodeCode + "_" + this.definitionDraft.edges.length;
                        var edge = buildEdge(edgeCode, sourceNodeCode, targetNodeCode, this.definitionDraft.edges.length + 1);
                        this.definitionDraft.edges.push(edge);
                        this.selectedDesigner = {type: "edge", code: edge.edgeCode};
                        this.setOperationState("success", "本地草稿已更新", "连线已创建");
                    }
                }
            },
            selectDesignerEdge: function (edge) {
                this.selectedDesigner = {type: "edge", code: edge.edgeCode};
            },
            handleDesignerDelete: function (event) {
                var tagName = event.target && event.target.tagName;
                if (["INPUT", "TEXTAREA", "SELECT"].indexOf(tagName) >= 0) {
                    return;
                }
                this.deleteSelectedDesignerItem();
            },
            deleteSelectedDesignerItem: function () {
                if (this.selectedDesigner.type === "edge") {
                    this.definitionDraft.edges = this.definitionDraft.edges.filter(function (edge) {
                        return edge.edgeCode !== this.selectedDesigner.code;
                    }, this);
                    this.selectedDesigner = {type: "", code: ""};
                    return this.persistDefinitionDraftAfterDesignerDelete("连线已删除并同步到后端", "连线已删除，保存后写入后端");
                } else if (this.selectedDesigner.type === "node") {
                    var nodeCode = this.selectedDesigner.code;
                    if (!window.confirm("删除节点会同步删除相关连线，是否继续？")) {
                        return;
                    }
                    this.definitionDraft.nodes = this.definitionDraft.nodes.filter(function (node) {
                        return node.nodeCode !== nodeCode;
                    });
                    this.definitionDraft.edges = this.definitionDraft.edges.filter(function (edge) {
                        return edge.sourceNodeCode !== nodeCode && edge.targetNodeCode !== nodeCode;
                    });
                    this.definitionDraft.nodes.forEach(function (node) {
                        if (node.pairedGatewayCode === nodeCode) {
                            node.pairedGatewayCode = "";
                        }
                        this.pruneListenerRejectTarget(node, nodeCode);
                    }, this);
                    this.selectedDesigner = {type: "", code: ""};
                    return this.persistDefinitionDraftAfterDesignerDelete("节点及相关连线已删除并同步到后端", "节点及相关连线已删除，保存后写入后端");
                }
            },
            persistDefinitionDraftAfterDesignerDelete: function (syncedMessage, draftMessage) {
                if (!hasText(this.definitionDraft.definitionId)) {
                    this.setOperationState("success", "本地草稿已更新", draftMessage);
                    return Promise.resolve(false);
                }
                if (!this.canManageDefinitions) {
                    this.setOperationState("error", "权限不足", "当前角色不能维护流程定义");
                    return Promise.resolve(false);
                }
                return this.prepareCustomAttachmentTemplates().then(function () {
                    var graphBody = this.buildGraphRequest();
                    return this.sendRequest("同步流程图", "PUT", API_PATHS.saveGraph(this.definitionDraft.definitionId), graphBody);
                }.bind(this)).then(function () {
                    this.setOperationState("success", "同步成功", syncedMessage);
                    return this.selectDefinition({definitionId: this.definitionDraft.definitionId});
                }.bind(this)).then(function () {
                    return this.queryDefinitions();
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", "同步失败", error.message);
                    return false;
                }.bind(this));
            },
            layoutDraftGraph: function () {
                this.definitionDraft.nodes.forEach(function (node, index) {
                    node.positionX = 90 + index * 170;
                    node.positionY = index % 2 === 0 ? 170 : 260;
                    node.sortOrder = index + 1;
                });
                this.setOperationState("success", "本地草稿已更新", "流程图已自动布局");
            },
            addFormField: function () {
                var index = this.definitionDraft.formFields.length + 1;
                this.definitionDraft.formFields.push({
                    localId: nextLocalId("field"),
                    fieldCode: "field_" + index,
                    fieldName: "字段" + index,
                    fieldType: "string",
                    controlType: "input",
                    required: false,
                    sortOrder: index
                });
            },
            removeFormField: function (index) {
                this.definitionDraft.formFields.splice(index, 1);
            },
            addAttachmentConfig: function () {
                var template = this.attachmentTemplateLibrary[0] || {};
                this.definitionDraft.attachmentConfigs.push(normalizeAttachmentConfig({
                    localId: nextLocalId("attach"),
                    sourceType: "existing",
                    attachmentTemplateId: template.attachmentTemplateId || "",
                    attachmentCode: template.attachmentCode || "",
                    attachmentName: template.attachmentName || "",
                    allowedExtensionsText: asArray(template.allowedExtensions).join(","),
                    maxSizeBytes: template.maxSizeBytes || 10485760,
                    required: true,
                    minCount: 1,
                    maxCount: 5,
                    applicableNodeCodes: this.userTaskNodes.length > 0 ? [this.userTaskNodes[0].nodeCode] : [],
                    sortOrder: this.definitionDraft.attachmentConfigs.length + 1
                }, this.definitionDraft.attachmentConfigs.length));
            },
            removeAttachmentConfig: function (index) {
                this.definitionDraft.attachmentConfigs.splice(index, 1);
                this.setOperationState("success", "本地草稿已更新", "附件配置行已删除，不会删除全局模板");
            },
            handleAttachmentSourceChange: function (item) {
                if (item.sourceType === "existing") {
                    this.selectExistingAttachmentTemplate(item);
                } else {
                    item.attachmentTemplateId = "";
                    item.attachmentCode = "";
                    item.attachmentName = "";
                    item.allowedExtensionsText = "pdf,jpg,png";
                    item.maxSizeBytes = 10485760;
                }
            },
            selectExistingAttachmentTemplate: function (item) {
                var template = this.attachmentTemplateLibrary.find(function (candidate) {
                    return candidate.attachmentTemplateId === item.attachmentTemplateId;
                });
                if (!template) {
                    return;
                }
                item.attachmentCode = template.attachmentCode;
                item.attachmentName = template.attachmentName;
                item.allowedExtensionsText = asArray(template.allowedExtensions).join(",");
                item.maxSizeBytes = template.maxSizeBytes;
                item.sourceType = "existing";
                item.pendingTemplatePersist = false;
            },
            queryAttachmentTemplates: function () {
                return this.sendRequest("查询附件模板", "GET", API_PATHS.attachmentTemplates + toQuery({
                    templateStatus: "ENABLED"
                })).then(function (payload) {
                    var templates = normalizeList(payload).map(function (item) {
                        return {
                            attachmentTemplateId: item.attachmentTemplateId || item.id,
                            attachmentCode: item.attachmentCode,
                            templateVersion: item.templateVersion || 1,
                            attachmentName: item.attachmentName || item.attachmentCode,
                            allowedExtensions: asArray(item.allowedExtensions),
                            maxSizeBytes: item.maxSizeBytes || 10485760
                        };
                    }).filter(function (item) {
                        return hasText(item.attachmentTemplateId) && hasText(item.attachmentCode);
                    });
                    if (templates.length > 0) {
                        this.attachmentTemplateLibrary = templates;
                    }
                    return payload;
                }.bind(this)).catch(function (error) {
                    this.addOperationLog("查询附件模板", API_PATHS.attachmentTemplates, "error", "", error.message);
                    throw error;
                }.bind(this));
            },
            createAttachmentTemplate: function (item) {
                return this.sendRequest("创建附件模板", "POST", API_PATHS.attachmentTemplates,
                        this.buildAttachmentTemplatePayload(item))
                        .then(function (created) {
                            item.attachmentTemplateId = created.attachmentTemplateId || created.id;
                            item.templateVersion = created.templateVersion;
                            item.pendingTemplatePersist = false;
                            this.captureTemplatesFromDetail({attachmentConfigs: [created]});
                            return item;
                        }.bind(this));
            },
            prepareCustomAttachmentTemplates: function () {
                var jobs = this.definitionDraft.attachmentConfigs.map(function (item) {
                    if (item.sourceType !== "custom" || hasText(item.attachmentTemplateId)) {
                        return Promise.resolve(item);
                    }
                    return this.createAttachmentTemplate(item);
                }, this);
                return Promise.all(jobs);
            },
            buildAttachmentTemplatePayload: function (item) {
                return {
                    attachmentCode: item.attachmentCode,
                    attachmentName: item.attachmentName,
                    description: item.description || "",
                    allowedExtensions: asArray(item.allowedExtensionsText),
                    maxSizeBytes: item.maxSizeBytes,
                    templateStatus: "ENABLED",
                    createdBy: this.currentUserId,
                    updatedBy: this.currentUserId
                };
            },
            saveDefinitionDraft: function () {
                if (!this.canManageDefinitions) {
                    this.setOperationState("error", "权限不足", "当前角色不能维护流程定义");
                    return;
                }
                var errors = this.validateDefinitionDraft();
                if (errors.length > 0) {
                    this.setOperationState("error", "本地校验失败", errors.join("；"));
                    return;
                }
                var createOrReuse = Promise.resolve({definitionId: this.definitionDraft.definitionId});
                if (this.definitionDialog.mode === "create" && !hasText(this.definitionDraft.definitionId)) {
                    var createBody = {
                        operationId: this.createOperationId("create_definition"),
                        processCode: this.definitionDraft.processCode,
                        processName: this.definitionDraft.processName,
                        systemCode: this.definitionDraft.systemCode,
                        remark: this.definitionDraft.remark,
                        operatorUserId: this.currentUserId
                    };
                    createOrReuse = this.sendRequest("创建流程定义", "POST", API_PATHS.definitions, createBody);
                }
                createOrReuse.then(function (definition) {
                    this.definitionDraft.definitionId = definition.definitionId || definition.id || this.definitionDraft.definitionId;
                    return this.prepareCustomAttachmentTemplates();
                }.bind(this)).then(function () {
                    var graphBody = this.buildGraphRequest();
                    return this.sendRequest("保存流程图", "PUT", API_PATHS.saveGraph(this.definitionDraft.definitionId), graphBody);
                }.bind(this)).then(function () {
                    return this.sendRequest("图校验", "GET", API_PATHS.validateDefinition(this.definitionDraft.definitionId), {
                        operationId: this.createOperationId("validate_definition"),
                        definitionId: this.definitionDraft.definitionId,
                        operatorUserId: this.currentUserId
                    });
                }.bind(this)).then(function () {
                    this.setOperationState("success", "保存成功", "流程定义保存成功，图校验通过");
                    this.closeDefinitionDialog();
                    return this.queryDefinitions();
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", "保存失败", error.message);
                }.bind(this));
            },
            buildGraphRequest: function () {
                return {
                    operationId: this.createOperationId("save_graph"),
                    operatorUserId: this.currentUserId,
                    nodes: this.definitionDraft.nodes.map(function (node, index) {
                        var copy = Object.assign({}, node);
                        if (copy.nodeType === "USER_TASK") {
                            this.buildUserApproverRule(copy);
                        } else {
                            copy.approverRuleType = null;
                            copy.approverRuleConfig = null;
                            copy.multiInstanceMode = "SINGLE";
                        }
                        delete copy.localId;
                        delete copy.selectedApproverIds;
                        delete copy.selectedDepartmentId;
                        delete copy.selectedRoleCode;
                        delete copy.selectedRoleDepartmentMode;
                        delete copy.selectedRoleDepartmentId;
                        delete copy.approverExpression;
                        delete copy.listenerRejectEnabled;
                        delete copy.listenerRejectTargetNodeCodes;
                        delete copy.listenerDirectSendEnabled;
                        delete copy.listenerConfigError;
                        delete copy.timeoutEnabled;
                        delete copy.timeoutDurationMinutes;
                        delete copy.timeoutAction;
                        delete copy.timeoutSeverity;
                        delete copy.timeoutTargetNodeCode;
                        delete copy.reminderEnabled;
                        delete copy.reminderMaxCount;
                        delete copy.reminderMessageTemplate;
                        delete copy.timeoutConfigError;
                        copy.sortOrder = index + 1;
                        return copy;
                    }, this),
                    edges: this.definitionDraft.edges.map(function (edge, index) {
                        var copy = Object.assign({}, edge);
                        delete copy.localId;
                        copy.sortOrder = index + 1;
                        return copy;
                    }),
                    formFields: this.definitionDraft.formFields.map(function (field, index) {
                        var copy = Object.assign({}, field);
                        delete copy.localId;
                        copy.sortOrder = index + 1;
                        return copy;
                    }),
                    attachmentConfigs: this.definitionDraft.attachmentConfigs.map(function (item, index) {
                        return this.toAttachmentConfigPayload(item, index);
                    }, this)
                };
            },
            buildUserApproverRule: function (node) {
                if (node.approverRuleType === "STARTER") {
                    node.approverRuleConfig = "{}";
                    node.multiInstanceMode = "SINGLE";
                    return node;
                }
                if (node.approverRuleType === "USER") {
                    var ids = asArray(node.selectedApproverIds);
                    node.approverRuleConfig = JSON.stringify({userIds: ids});
                    node.multiInstanceMode = normalizeMultiInstanceMode(node.multiInstanceMode, ids.length);
                    return node;
                }
                if (node.approverRuleType === "DEPARTMENT") {
                    node.approverRuleConfig = JSON.stringify({departmentId: String(node.selectedDepartmentId || "").trim()});
                    node.multiInstanceMode = normalizeMultiInstanceMode(node.multiInstanceMode, 0);
                    return node;
                }
                if (node.approverRuleType === "ROLE") {
                    node.approverRuleConfig = JSON.stringify({roleCode: String(node.selectedRoleCode || "").trim()});
                    node.multiInstanceMode = normalizeMultiInstanceMode(node.multiInstanceMode, 0);
                    return node;
                }
                if (node.approverRuleType === "ROLE_IN_DEPARTMENT") {
                    var roleDepartmentConfig = {roleCode: String(node.selectedRoleCode || "").trim()};
                    if (node.selectedRoleDepartmentMode === "FIXED") {
                        roleDepartmentConfig.departmentId = String(node.selectedRoleDepartmentId || "").trim();
                    }
                    node.approverRuleConfig = JSON.stringify(roleDepartmentConfig);
                    node.multiInstanceMode = normalizeMultiInstanceMode(node.multiInstanceMode, 0);
                    return node;
                }
                if (node.approverRuleType === "APPROVER_EXPRESSION") {
                    node.approverRuleConfig = JSON.stringify({expression: approverExpressionText(node.approverExpression)});
                    node.multiInstanceMode = normalizeMultiInstanceMode(node.multiInstanceMode, 0);
                    return node;
                }
                node.approverRuleConfig = stringifyRule(node.approverRuleConfig);
                node.multiInstanceMode = normalizeMultiInstanceMode(node.multiInstanceMode, 0);
                return node;
            },
            toAttachmentConfigPayload: function (item, index) {
                return {
                    attachmentConfigId: item.attachmentConfigId || "",
                    definitionId: this.definitionDraft.definitionId,
                    attachmentTemplateId: item.attachmentTemplateId,
                    attachmentCode: item.attachmentCode,
                    required: Boolean(item.required),
                    minCount: Number(item.minCount || 0),
                    maxCount: Number(item.maxCount || 0),
                    applicableNodeCodes: asArray(item.applicableNodeCodes),
                    sortOrder: item.sortOrder || index + 1
                };
            },
            validateDefinitionDraft: function () {
                var errors = [];
                if (!hasText(this.definitionDraft.processCode)) {
                    errors.push("流程编码必填");
                }
                if (!hasText(this.definitionDraft.processName)) {
                    errors.push("流程名称必填");
                }
                var nodeCodes = {};
                this.definitionDraft.nodes.forEach(function (node) {
                    if (!hasText(node.nodeCode)) {
                        errors.push("存在未填写编码的节点");
                    } else if (nodeCodes[node.nodeCode]) {
                        errors.push("节点编码重复：" + node.nodeCode);
                    }
                    nodeCodes[node.nodeCode] = true;
                    if (node.nodeType === "USER_TASK") {
                        if (node.approverRuleType === "USER" && asArray(node.selectedApproverIds).length === 0) {
                            errors.push("用户任务必须选择审批人：" + node.nodeName);
                        }
                        if (node.approverRuleType === "DEPARTMENT" && !hasText(node.selectedDepartmentId)) {
                            errors.push("指定部门审批必须选择部门：" + node.nodeName);
                        }
                        if (node.approverRuleType === "ROLE" && !hasText(node.selectedRoleCode)) {
                            errors.push("指定角色审批必须选择角色：" + node.nodeName);
                        }
                        if (node.approverRuleType === "ROLE_IN_DEPARTMENT") {
                            if (!hasText(node.selectedRoleCode)) {
                                errors.push("部门角色审批必须选择角色：" + node.nodeName);
                            }
                            if (node.selectedRoleDepartmentMode === "FIXED" && !hasText(node.selectedRoleDepartmentId)) {
                                errors.push("部门角色审批使用指定部门时必须选择部门：" + node.nodeName);
                            }
                        }
                        if (node.approverRuleType === "APPROVER_EXPRESSION" && !hasText(node.approverExpression)) {
                            errors.push("表达式审批必须填写审批人表达式：" + node.nodeName);
                        }
                    }
                    if (node.nodeType === "USER_TASK") {
                        var listenerEditor = readListenerRuleEditor(node.listenerConfig);
                        node.listenerConfigError = listenerEditor.listenerConfigError;
                        if (listenerEditor.listenerConfigError) {
                            errors.push(node.nodeName + "：" + listenerEditor.listenerConfigError);
                        } else if (listenerEditor.listenerRejectEnabled
                                && listenerEditor.listenerRejectTargetNodeCodes.length === 0) {
                            errors.push(node.nodeName + "：启用驳回时必须至少选择一个允许驳回节点");
                        }
                    }
                });
                this.definitionDraft.nodes.forEach(function (node) {
                    var paired;
                    if (!this.isParallelGatewayNode(node)) {
                        return;
                    }
                    if (!hasText(node.pairedGatewayCode)) {
                        errors.push("并行网关必须选择配对网关：" + node.nodeName);
                        return;
                    }
                    paired = this.definitionDraft.nodes.find(function (candidate) {
                        return candidate.nodeCode === node.pairedGatewayCode;
                    });
                    if (!this.isExpectedParallelGatewayPair(node, paired)) {
                        errors.push("并行网关配对类型不匹配：" + node.nodeName);
                        return;
                    }
                    if (paired.pairedGatewayCode !== node.nodeCode) {
                        errors.push("并行网关配对必须互相指向：" + node.nodeName);
                    }
                }, this);
                this.definitionDraft.edges.forEach(function (edge) {
                    if (!nodeCodes[edge.sourceNodeCode] || !nodeCodes[edge.targetNodeCode]) {
                        errors.push("连线引用不存在的节点：" + edge.edgeCode);
                    }
                });
                var attachmentCodes = {};
                this.definitionDraft.attachmentConfigs.forEach(function (item) {
                    if (!hasText(item.attachmentCode)) {
                        errors.push("附件编码必填");
                    } else if (attachmentCodes[item.attachmentCode]) {
                        errors.push("附件编码重复：" + item.attachmentCode);
                    }
                    attachmentCodes[item.attachmentCode] = true;
                    if (item.required && Number(item.minCount || 0) < 1) {
                        errors.push("必填附件最小数量必须大于等于 1：" + item.attachmentCode);
                    }
                    if (Number(item.maxCount || 0) < Number(item.minCount || 0)) {
                        errors.push("附件最大数量不能小于最小数量：" + item.attachmentCode);
                    }
                    if (asArray(item.applicableNodeCodes).length === 0) {
                        errors.push("附件必须选择适用节点：" + item.attachmentCode);
                    }
                });
                return errors;
            },
            copyDefinition: function (row) {
                var definitionId = extractDefinitionId(row);
                var body = {
                    operationId: this.createOperationId("复制流程定义"),
                    operatorUserId: this.currentUserId
                };
                this.sendRequest("复制流程定义", "POST", API_PATHS.copyDefinition(definitionId), body).then(function (definition) {
                    var copiedDefinitionId = extractDefinitionId(definition);
                    this.selectedDefinitionId = copiedDefinitionId || definitionId;
                    return this.queryDefinitions();
                }.bind(this)).then(function () {
                    if (!this.selectedDefinitionId) {
                        return null;
                    }
                    var selected = this.definitionRows.find(function (definitionRow) {
                        return extractDefinitionId(definitionRow) === this.selectedDefinitionId;
                    }, this);
                    return selected ? this.selectDefinition(selected) : null;
                }.bind(this)).catch(function () {});
            },
            publishDefinition: function (row) {
                this.definitionOperation(row, "发布流程定义", API_PATHS.publishDefinition);
            },
            activateDefinition: function (row) {
                this.definitionOperation(row, "激活流程定义", API_PATHS.activateDefinition);
            },
            deactivateDefinition: function (row) {
                this.definitionOperation(row, "停用流程定义", API_PATHS.deactivateDefinition);
            },
            archiveDefinition: function (row) {
                this.definitionOperation(row, "归档流程定义", API_PATHS.archiveDefinition);
            },
            deleteDefinition: function (row) {
                var definitionId = extractDefinitionId(row);
                if (!window.confirm("确认删除流程定义 " + definitionId + "？")) {
                    return;
                }
                this.definitionOperation(row, "删除流程定义", API_PATHS.deleteDefinition, "DELETE");
            },
            definitionOperation: function (row, label, pathBuilder, method) {
                var definitionId = extractDefinitionId(row);
                var path = typeof pathBuilder === "function" ? pathBuilder(definitionId) : pathBuilder;
                var body = {
                    operationId: this.createOperationId(label),
                    definitionId: definitionId,
                    operatorUserId: this.currentUserId
                };
                this.sendRequest(label, method || "POST", path, body).then(function () {
                    return this.queryDefinitions();
                }.bind(this)).catch(function () {});
            },
            applyInstanceDefinition: function () {
                var row = this.definitionRows.find(function (definition) {
                    return extractDefinitionId(definition) === this.instanceForm.definitionId;
                }, this);
                this.instanceForm.processCode = row ? row.processCode : "";
                if (row) {
                    this.instanceForm.instanceTitle = row.processName + "测试实例";
                    return this.selectDefinition(row).then(function () {
                        this.applyInstanceFormFields();
                    }.bind(this));
                }
                this.applyInstanceFormFields();
                return Promise.resolve();
            },
            startAndSubmitInstance: function () {
                if (this.startSubmitting) {
                    return;
                }
                this.startSubmitting = true;
                try {
                    var startBody = this.buildStartSubmitBody();
                    this.sendRequest("启动流程", "POST", API_PATHS.startAndSubmit, startBody).then(function (instance) {
                        this.lastStartedInstanceId = extractInstanceId(instance) || "";
                        return this.autoClaimSpecifiedUserTasks(instance).then(function (claimedCount) {
                            return Promise.all([
                                this.queryInstances(),
                                this.queryTodoTasks()
                            ]).then(function () {
                                var message = "实例已创建并提交申请节点";
                                if (claimedCount > 0) {
                                    message += " Auto-claimed " + claimedCount + " specified-user task(s).";
                                }
                                this.setOperationState("success", "启动并提交成功", message);
                                return instance;
                            }.bind(this));
                        }.bind(this));
                    }.bind(this)).catch(function (error) {
                        this.setOperationState("error", "启动并提交失败", error.message);
                    }.bind(this)).then(function () {
                        this.startSubmitting = false;
                    }.bind(this));
                } catch (error) {
                    this.startSubmitting = false;
                    this.setOperationState("error", "启动参数错误", error.message);
                }
            },
            buildStartSubmitBody: function () {
                if (!hasText(this.instanceForm.processCode)) {
                    throw new Error("请选择流程定义");
                }
                this.validateStartFormFields();
                var user = this.currentUser();
                return {
                    operationId: this.createOperationId("start_and_submit"),
                    processCode: this.instanceForm.processCode,
                    instanceTitle: this.instanceForm.instanceTitle,
                    starterUserId: this.currentUserId,
                    starterDeptId: userDepartmentId(user),
                    variables: this.buildStartVariables(),
                    attachments: this.buildInstanceAttachments()
                };
            },
            buildStartVariables: function () {
                return this.buildVariablesFromFields(this.instanceDefinitionFields, this.instanceFieldValues, true);
            },
            addInstanceAttachment: function () {
                this.instanceAttachments.push({
                    localId: nextLocalId("instance_attach"),
                    enabled: false,
                    attachmentCode: "bankReceipt",
                    ownerType: "INSTANCE",
                    fileName: "",
                    contentType: "",
                    sizeBytes: 0,
                    content: ""
                });
            },
            removeInstanceAttachment: function (index) {
                this.instanceAttachments.splice(index, 1);
            },
            handleInstanceAttachmentFileChange: function (attachment, event) {
                var file = event && event.target && event.target.files && event.target.files[0];
                if (!file) {
                    return;
                }
                this.readFileAsBase64(file).then(function (content) {
                    attachment.enabled = true;
                    attachment.fileName = file.name;
                    attachment.contentType = file.type || "application/octet-stream";
                    attachment.sizeBytes = file.size;
                    attachment.content = content;
                    if (!hasText(attachment.attachmentCode) && this.instanceDefinitionAttachments.length > 0) {
                        attachment.attachmentCode = this.instanceDefinitionAttachments[0].attachmentCode;
                    }
                    this.setOperationState("success", "附件已读取", file.name + " / " + file.size + " bytes");
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", "附件读取失败", error.message);
                }.bind(this));
            },
            readFileAsBase64: function (file) {
                return new Promise(function (resolve, reject) {
                    var reader = new FileReader();
                    reader.onload = function () {
                        var result = String(reader.result || "");
                        var marker = result.indexOf(",");
                        resolve(marker >= 0 ? result.substring(marker + 1) : result);
                    };
                    reader.onerror = function () {
                        reject(reader.error || new Error("file read failed"));
                    };
                    reader.readAsDataURL(file);
                });
            },
            buildInstanceAttachments: function () {
                return this.instanceAttachments.filter(function (attachment) {
                    return attachment.enabled;
                }).map(function (attachment) {
                    if (!hasText(attachment.attachmentCode)) {
                        throw new Error("附件模板必选");
                    }
                    if (!hasText(attachment.fileName) || !hasText(attachment.content)) {
                        throw new Error("请先选择附件文件");
                    }
                    return {
                        attachmentCode: attachment.attachmentCode,
                        ownerType: "INSTANCE",
                        fileName: attachment.fileName,
                        contentType: attachment.contentType,
                        sizeBytes: attachment.sizeBytes,
                        content: attachment.content
                    };
                });
            },
            downloadAttachment: function (attachment) {
                var attachmentId = attachment && (attachment.attachmentId || attachment.id);
                if (!hasText(attachmentId)) {
                    this.setOperationState("error", "附件下载失败", "附件 ID 为空");
                    return;
                }
                this.sendRequest("附件下载", "GET", API_PATHS.attachmentDownload(attachmentId) + toQuery({
                    operatorUserId: this.currentUserId
                })).then(function (payload) {
                    this.saveAttachmentContent(payload);
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", "附件下载失败", error.message);
                }.bind(this));
            },
            saveAttachmentContent: function (payload) {
                var meta = (payload && payload.attachment) || {};
                var content = payload && payload.content;
                if (!hasText(content)) {
                    throw new Error("附件内容为空");
                }
                var binary = window.atob(content);
                var bytes = new Uint8Array(binary.length);
                var index;
                for (index = 0; index < binary.length; index += 1) {
                    bytes[index] = binary.charCodeAt(index);
                }
                var blob = new Blob([bytes], {type: meta.contentType || "application/octet-stream"});
                var url = URL.createObjectURL(blob);
                var link = document.createElement("a");
                link.href = url;
                link.download = meta.fileName || "attachment";
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                window.setTimeout(function () {
                    URL.revokeObjectURL(url);
                }, 0);
            },
            findCreatedApplyTask: function (instance) {
                var tasks = normalizeList(instance && (instance.createdTasks || instance.tasks));
                return tasks.find(function (task) {
                    return task.nodeCode === "apply" || task.nodeCode === "APPLY";
                }) || tasks[0];
            },
            queryAdminInstances: function () {
                if (!this.isTestAdmin) {
                    this.setOperationState("error", "查询全部实例失败", "仅测试管理员可以查询全部实例");
                    return Promise.resolve([]);
                }
                return this.sendRequest("查询全部实例", "GET", API_PATHS.adminInstances + toQuery({pageNo: 1, pageSize: 50})).then(function (payload) {
                    this.instanceRows = normalizeList(payload);
                    return payload;
                }.bind(this));
            },
            autoClaimSpecifiedUserTasks: function (instance) {
                var tasks = normalizeList(instance && (instance.createdTasks || instance.tasks));
                if (tasks.length === 0) {
                    return Promise.resolve(0);
                }
                return this.loadDefinitionForAutoClaim(instance, tasks).then(function (definition) {
                    var claimableTasks = tasks.filter(function (task) {
                        return this.isSpecifiedUserTask(task, definition) && this.canClaimTask(task);
                    }, this);
                    if (claimableTasks.length === 0) {
                        return 0;
                    }
                    return claimableTasks.reduce(function (chain, task) {
                        return chain.then(function (claimedCount) {
                            return this.autoClaimTask(task).then(function () {
                                return claimedCount + 1;
                            });
                        }.bind(this));
                    }.bind(this), Promise.resolve(0));
                }.bind(this));
            },
            loadDefinitionForAutoClaim: function (instance, tasks) {
                var definitionId = (instance && instance.definitionId)
                    || (tasks[0] && tasks[0].definitionId)
                    || this.instanceForm.definitionId;
                if (!hasText(definitionId)) {
                    return Promise.resolve(this.selectedDefinitionDetail || {});
                }
                if (this.selectedDefinitionDetail
                        && extractDefinitionId(this.selectedDefinitionDetail) === definitionId) {
                    return Promise.resolve(this.selectedDefinitionDetail);
                }
                return this.sendRequest("Load definition for auto claim", "GET",
                    API_PATHS.definitionDetail(definitionId)).then(function (definition) {
                    this.selectedDefinitionId = definitionId;
                    this.selectedDefinitionDetail = definition || {};
                    return this.selectedDefinitionDetail;
                }.bind(this));
            },
            isSpecifiedUserTask: function (task, definition) {
                var node = normalizeList(definition && definition.nodes).find(function (candidate) {
                    return candidate.nodeCode === task.nodeCode;
                });
                return Boolean(node && node.approverRuleType === "USER");
            },
            autoClaimTask: function (task) {
                var taskId = extractTaskId(task);
                var body = {
                    operationId: this.createOperationId("auto_claim"),
                    taskId: taskId,
                    expectedTaskVersion: extractTaskVersion(task),
                    operatorUserId: this.currentUserId,
                    comment: ""
                };
                return this.sendRequest("Auto claim", "POST", API_PATHS.taskClaim, body).then(function (payload) {
                    var updatedTask = this.extractUpdatedTask(payload, taskId);
                    if (updatedTask) {
                        this.replaceTodoTask(updatedTask);
                    }
                    return payload;
                }.bind(this));
            },
            queryInstances: function () {
                if (this.isTestAdmin) {
                    return this.queryAdminInstances();
                }
                return this.sendRequest("查询实例", "GET", API_PATHS.startedInstances + toQuery({
                    starterUserId: this.currentUserId,
                    pageNo: 1,
                    pageSize: 50
                })).then(function (payload) {
                    this.instanceRows = normalizeList(payload);
                    return payload;
                }.bind(this));
            },
            markSelectedInstanceRead: function (instanceId) {
                return this.sendRequest("记录已阅", "POST", API_PATHS.markRead(instanceId), {});
            },
            recordSelectedInstanceRead: function (instanceId) {
                if (!hasText(instanceId)) {
                    return Promise.resolve(null);
                }
                return this.markSelectedInstanceRead(instanceId).catch(function () {
                    return null;
                });
            },
            selectInstance: function (row) {
                var instanceId = extractInstanceId(row);
                if (!instanceId) {
                    return Promise.resolve();
                }
                return this.loadSelectedInstanceDetail(instanceId);
            },
            loadSelectedInstanceDetail: function (instanceId) {
                this.readRecordRows = [];
                this.historyTaskRows = [];
                this.commentRows = [];
                this.callbackLogRows = [];
                this.auditTraceRows = [];
                this.instanceVariableValues = {};
                return this.sendRequest("查询实例详情", "GET", API_PATHS.instanceDetail(instanceId)).then(function (payload) {
                    var detail = payload || {};
                    var activeTasks = normalizeList(detail.activeTasks);
                    var detailJob = (activeTasks.length > 0 || asArray(detail.currentNodeCodes).length === 0)
                        ? Promise.resolve(detail)
                        : this.sendRequest("查询当前活动任务", "GET", API_PATHS.activeTasks(instanceId))
                            .then(function (tasksPayload) {
                                return Object.assign({}, detail, {
                                    activeTasks: normalizeList(tasksPayload)
                                });
                            });
                    return detailJob.then(function (enrichedDetail) {
                        this.selectedInstanceDetail = enrichedDetail;
                        this.historyTaskRows = normalizeList(enrichedDetail && enrichedDetail.historyTasks);
                        this.commentRows = normalizeList(enrichedDetail && enrichedDetail.comments);
                        var variables = this.extractInstanceVariables(enrichedDetail);
                        this.recordSelectedInstanceRead(instanceId);
                        if (enrichedDetail && enrichedDetail.definitionId) {
                            return this.selectDefinition({definitionId: enrichedDetail.definitionId}).then(function () {
                                this.initializeInstanceVariableValues(this.selectedInstanceFields, variables);
                                return enrichedDetail;
                            }.bind(this));
                        }
                        this.initializeInstanceVariableValues(this.selectedInstanceFields, variables);
                        return enrichedDetail;
                    }.bind(this));
                }.bind(this));
            },
            selectedInstanceId: function () {
                return extractInstanceId(this.selectedInstanceDetail);
            },
            requireSelectedInstanceId: function (label) {
                var instanceId = this.selectedInstanceId();
                if (!instanceId) {
                    this.setOperationState("error", label + "失败", "请先选择流程实例");
                    return "";
                }
                return instanceId;
            },
            instanceQueryConfig: function (queryType) {
                var configs = {
                    historyTasks: {title: "历史任务", label: "查询历史任务"},
                    comments: {title: "审批意见", label: "查询审批意见"},
                    callbackLogs: {title: "回调日志", label: "查询回调日志", adminOnly: true},
                    readRecords: {title: "已阅记录", label: "查询已阅记录"},
                    auditTrace: {title: "审计追溯", label: "查询审计追溯", adminOnly: true}
                };
                return configs[queryType] || null;
            },
            openInstanceQueryDialog: function (queryType) {
                var config = this.instanceQueryConfig(queryType);
                if (!config) {
                    this.setOperationState("error", "查询失败", "未知查询类型：" + queryType);
                    return Promise.resolve([]);
                }
                var instanceId = this.requireSelectedInstanceId(config.label);
                if (!instanceId) {
                    return Promise.resolve([]);
                }
                if (config.adminOnly && !this.isTestAdmin) {
                    this.setOperationState("error", config.label + "失败", "仅测试管理员可以" + config.label);
                    return Promise.resolve([]);
                }
                if (queryType === "readRecords" && !this.canQuerySelectedInstanceReadRecords) {
                    this.setOperationState("error", "查询已阅记录失败", "请先选择流程实例");
                    return Promise.resolve([]);
                }
                this.instanceQueryDialog = {
                    open: true,
                    queryType: queryType,
                    title: config.title,
                    rows: [],
                    pageNo: 1,
                    pageSize: Math.max(1, Number(this.instanceQueryDialog.pageSize) || 20),
                    total: 0,
                    loading: false
                };
                return this.queryInstanceDialogPage();
            },
            closeInstanceQueryDialog: function () {
                this.instanceQueryDialog.open = false;
            },
            changeInstanceQueryPage: function (delta) {
                var current = Math.max(1, Number(this.instanceQueryDialog.pageNo) || 1);
                var next = Math.max(1, Math.min(this.queryDialogTotalPages, current + delta));
                if (next === current) {
                    return Promise.resolve([]);
                }
                this.instanceQueryDialog.pageNo = next;
                return this.queryInstanceDialogPage();
            },
            queryInstanceDialogPage: function () {
                var dialog = this.instanceQueryDialog;
                var config = this.instanceQueryConfig(dialog.queryType);
                if (!dialog.open || !config) {
                    return Promise.resolve([]);
                }
                var instanceId = this.requireSelectedInstanceId(config.label);
                if (!instanceId) {
                    return Promise.resolve([]);
                }
                dialog.pageNo = Math.max(1, Number(dialog.pageNo) || 1);
                dialog.pageSize = Math.min(100, Math.max(1, Number(dialog.pageSize) || 20));
                dialog.loading = true;
                var path = this.buildInstanceQueryPath(dialog.queryType, instanceId, dialog.pageNo, dialog.pageSize);
                return this.sendRequest(config.label, "GET", path).then(function (payload) {
                    var rows = normalizeList(payload);
                    this.instanceQueryDialog.rows = rows;
                    this.instanceQueryDialog.total = extractTotalCount(payload, rows.length);
                    this.instanceQueryDialog.loading = false;
                    this.syncInstanceQueryRows(dialog.queryType, rows);
                    return payload;
                }.bind(this)).catch(function (error) {
                    this.instanceQueryDialog.rows = [];
                    this.instanceQueryDialog.total = 0;
                    this.instanceQueryDialog.loading = false;
                    throw error;
                }.bind(this));
            },
            buildInstanceQueryPath: function (queryType, instanceId, pageNo, pageSize) {
                var paging = {pageNo: pageNo, pageSize: pageSize};
                if (queryType === "historyTasks") {
                    return this.isTestAdmin ? API_PATHS.adminHistoryTasks + toQuery(Object.assign({instanceId: instanceId}, paging)) : API_PATHS.historyTasks(instanceId) + toQuery(paging);
                }
                if (queryType === "comments") {
                    return API_PATHS.comments(instanceId) + toQuery(paging);
                }
                if (queryType === "callbackLogs") {
                    return API_PATHS.callbackLogs + toQuery(Object.assign({instanceId: instanceId}, paging));
                }
                if (queryType === "readRecords") {
                    return API_PATHS.readRecords(instanceId) + toQuery(paging);
                }
                return API_PATHS.auditLogs + toQuery(Object.assign({instanceId: instanceId}, paging));
            },
            syncInstanceQueryRows: function (queryType, rows) {
                if (queryType === "historyTasks") {
                    this.historyTaskRows = rows;
                } else if (queryType === "comments") {
                    this.commentRows = rows;
                } else if (queryType === "callbackLogs") {
                    this.callbackLogRows = rows;
                } else if (queryType === "readRecords") {
                    this.readRecordRows = rows;
                } else if (queryType === "auditTrace") {
                    this.auditTraceRows = rows;
                }
            },
            queryInstanceHistoryTasks: function () {
                return this.openInstanceQueryDialog("historyTasks");
            },
            queryInstanceComments: function () {
                return this.openInstanceQueryDialog("comments");
            },
            queryInstanceCallbackLogs: function () {
                return this.openInstanceQueryDialog("callbackLogs");
            },
            queryInstanceAuditTrace: function () {
                return this.openInstanceQueryDialog("auditTrace");
            },
            queryInstanceReadRecords: function () {
                return this.openInstanceQueryDialog("readRecords");
            },
            updateSelectedInstanceVariables: function () {
                var instanceId = this.requireSelectedInstanceId("更新表单字段");
                if (!instanceId) {
                    return Promise.resolve(null);
                }
                var body = {
                    operationId: this.createOperationId("update_variables"),
                    instanceId: instanceId,
                    operatorUserId: this.currentUserId,
                    variables: this.buildVariablesFromFields(this.selectedInstanceFields, this.instanceVariableValues, true)
                };
                return this.sendRequest("更新表单字段", "PUT", API_PATHS.updateVariables, body).then(function (payload) {
                    return this.selectInstance({instanceId: extractInstanceId(payload) || instanceId});
                }.bind(this)).then(function (payload) {
                    if (this.instanceQueryDialog.open && this.instanceQueryDialog.queryType === "auditTrace") {
                        this.queryInstanceDialogPage().catch(function () {});
                    }
                    return payload;
                }.bind(this));
            },
            withdrawSelectedInstance: function () {
                var instanceId = this.requireSelectedInstanceId("撤回实例");
                if (!instanceId) {
                    return Promise.resolve(null);
                }
                if (!this.canWithdrawSelectedInstance) {
                    this.setOperationState("error", "撤回实例失败", "只有当前节点的上一办理人可以撤回");
                    return Promise.resolve(null);
                }
                return this.sendRequest("查询活动任务", "GET", API_PATHS.activeTasks(instanceId)).then(function (payload) {
                    return this.submitStandaloneTaskAction("撤回实例", API_PATHS.taskWithdraw, normalizeList(payload)[0], {});
                }.bind(this));
            },
            terminateSelectedInstance: function () {
                var instanceId = this.requireSelectedInstanceId("终止流程");
                if (!instanceId) {
                    return Promise.resolve(null);
                }
                return this.sendRequest("终止流程", "POST", API_PATHS.terminateInstance, {
                    operationId: this.createOperationId("terminate_instance"),
                    instanceId: instanceId,
                    operatorUserId: this.currentUserId,
                    comment: this.instanceOperationComment
                }).then(function () {
                    return this.selectInstance({instanceId: instanceId});
                }.bind(this)).then(function () {
                    return this.queryInstances();
                }.bind(this));
            },
            deleteSelectedInstance: function () {
                var instanceId = this.requireSelectedInstanceId("删除流程实例");
                if (!instanceId) {
                    return Promise.resolve(null);
                }
                if (!window.confirm("确认删除流程实例 " + instanceId + "？")) {
                    return Promise.resolve(null);
                }
                return this.sendRequest("删除流程实例", "DELETE", API_PATHS.deleteInstance, {
                    operationId: this.createOperationId("delete_instance"),
                    instanceId: instanceId,
                    operatorUserId: this.currentUserId
                }).then(function () {
                    this.selectedInstanceDetail = null;
                    this.instanceVariableValues = {};
                    return this.queryInstances();
                }.bind(this));
            },
            queryTodoTasks: function () {
                return this.sendRequest("查询待办", "GET", API_PATHS.todoTasks + toQuery({
                    userId: this.currentUserId,
                    pageNo: 1,
                    pageSize: 50
                })).then(function (payload) {
                    this.todoReminderStatusByTaskId = {};
                    this.todoRows = this.focusCurrentStartedTodos(normalizeList(payload));
                    return this.recordTodoRowsRead(this.todoRows).then(function () {
                        return this.enrichTodoTimeoutReminders(this.todoRows);
                    }.bind(this)).then(function () {
                        return payload;
                    });
                }.bind(this));
            },
            recordTodoRowsRead: function (rows) {
                var seen = {};
                var instanceIds = normalizeList(rows).map(function (row) {
                    return row && row.instanceId;
                }).filter(function (instanceId) {
                    if (!hasText(instanceId) || seen[instanceId]) {
                        return false;
                    }
                    seen[instanceId] = true;
                    return true;
                });
                return Promise.all(instanceIds.map(function (instanceId) {
                    return this.recordSelectedInstanceRead(instanceId);
                }, this));
            },
            focusCurrentStartedTodos: function (rows) {
                var normalized = this.sortTodoRows(rows.slice());
                if (hasText(this.lastStartedInstanceId)) {
                    var focused = normalized.filter(function (row) {
                        return row.instanceId === this.lastStartedInstanceId;
                    }, this);
                    if (focused.length > 0) {
                        return this.sortTodoRows(focused);
                    }
                }
                return normalized;
            },
            sortTodoRows: function (rows) {
                return rows.sort(function (left, right) {
                    var leftPriority = todoTimeoutPriority(left);
                    var rightPriority = todoTimeoutPriority(right);
                    if (leftPriority !== rightPriority) {
                        return leftPriority - rightPriority;
                    }
                    return String(right.createdAt || "").localeCompare(String(left.createdAt || ""));
                });
            },
            enrichTodoTimeoutReminders: function (rows) {
                var tasks = rows.filter(function (task) {
                    return hasText(task.dueAt) && hasText(extractTaskId(task));
                });
                if (tasks.length === 0) {
                    return Promise.resolve(rows);
                }
                return Promise.all(tasks.map(function (task) {
                    return this.queryTaskTimeoutReminder(task).then(function (payload) {
                        this.rememberTaskReminderStatus(task, normalizeList(payload));
                        return payload;
                    }.bind(this)).catch(function (error) {
                        this.todoReminderStatusByTaskId[extractTaskId(task)] = {
                            label: "提醒失败",
                            className: "failed",
                            errorMessage: error.message
                        };
                        return null;
                    }.bind(this));
                }, this)).then(function () {
                    return rows;
                });
            },
            queryTaskTimeoutReminder: function (task) {
                var taskId = extractTaskId(task);
                if (!taskId) {
                    return Promise.resolve([]);
                }
                return this.sendRequest("查询超时提醒", "GET", API_PATHS.reminders + toQuery({
                    taskId: taskId,
                    reminderType: "TIMEOUT",
                    pageNo: 1,
                    pageSize: 5
                }));
            },
            rememberTaskReminderStatus: function (task, reminders) {
                var taskId = extractTaskId(task);
                if (!taskId || reminders.length === 0) {
                    return;
                }
                var latest = reminders.slice().sort(function (left, right) {
                    return String(right.sentAt || right.createdAt || "").localeCompare(String(left.sentAt || left.createdAt || ""));
                })[0];
                var failed = latest.reminderStatus === "FAILED";
                this.todoReminderStatusByTaskId[taskId] = {
                    label: failed ? "提醒失败" : "已提醒",
                    className: failed ? "failed" : "reminded",
                    reminderStatus: latest.reminderStatus || "",
                    errorMessage: latest.errorMessage || ""
                };
            },
            queryCompletedTasks: function () {
                return this.sendRequest("查询已办", "GET", API_PATHS.completedTasks + toQuery({
                    userId: this.currentUserId,
                    pageNo: 1,
                    pageSize: 50
                })).then(function (payload) {
                    this.completedRows = normalizeList(payload);
                    return payload;
                }.bind(this));
            },
            openTodoTaskDialog: function (row) {
                this.taskDialog = {
                    open: true,
                    task: row,
                    variablesText: "{}",
                    comment: "",
                    attachments: [],
                    starterTask: false,
                    directSendContext: {allowed: false},
                    rejectTargetNodeCode: "",
                    transferUserId: "",
                    delegateUserId: "",
                    addSignUserIds: []
                };
                if (hasText(row.instanceId)) {
                    this.recordSelectedInstanceRead(row.instanceId);
                    this.loadTaskDialogContext(row).catch(function () {});
                }
            },
            openCompletedTaskDialog: function (row) {
                this.completedDialog = {
                    open: true,
                    task: row,
                    attachments: [],
                    currentNodeCodes: []
                };
                var instanceId = row.instanceId;
                if (!hasText(instanceId)) {
                    return;
                }
                this.recordSelectedInstanceRead(instanceId);
                this.sendRequest("查询已办实例详情", "GET", API_PATHS.instanceDetail(instanceId)).then(function (payload) {
                    this.completedDialog.currentNodeCodes = asArray(payload.currentNodeCodes);
                }.bind(this));
                this.sendRequest("查询已办附件", "GET", this.attachmentQueryPath(instanceId)).then(function (payload) {
                    this.completedDialog.attachments = normalizeList(payload);
                }.bind(this));
            },
            loadTaskDialogContext: function (task) {
                var instanceId = task.instanceId;
                var taskId = extractTaskId(task);
                var definitionId = task.definitionId;
                var definitionJob = hasText(definitionId)
                    ? this.sendRequest("查询待办流程定义", "GET", API_PATHS.definitionDetail(definitionId))
                    : Promise.resolve(null);
                return Promise.all([
                    this.sendRequest("查询任务实例详情", "GET", API_PATHS.instanceDetail(instanceId)),
                    this.sendRequest("查询任务附件", "GET", this.attachmentQueryPath(instanceId)),
                    definitionJob,
                    this.sendRequest("查询直送上下文", "GET", API_PATHS.directSendContext(taskId))
                ]).then(function (results) {
                    if (!this.taskDialog.open || extractTaskId(this.taskDialog.task) !== taskId) {
                        return results;
                    }
                    var instance = results[0] || {};
                    var definition = results[2] || this.selectedDefinitionDetail || {};
                    this.taskDialog.variablesText = JSON.stringify(
                        instance.variables || instance.variablesJson || {}, null, 2);
                    this.taskDialog.attachments = normalizeList(results[1]).map(function (attachment) {
                        return Object.assign({}, attachment, {pendingReplacement: null});
                    });
                    this.taskDialog.directSendContext = results[3] || {allowed: false};
                    this.selectedDefinitionId = definition.definitionId || definitionId || "";
                    this.selectedDefinitionDetail = definition;
                    var node = normalizeList(definition.nodes).find(function (candidate) {
                        return candidate.nodeCode === task.nodeCode;
                    });
                    this.taskDialog.starterTask = Boolean(node && node.approverRuleType === "STARTER");
                    return results;
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", "加载待办上下文失败", error.message);
                    throw error;
                }.bind(this));
            },
            taskAttachmentConfigs: function (definition, nodeCode) {
                return this.normalizeAttachmentConfigsFromDetail(definition).filter(function (config) {
                    var nodes = asArray(config.applicableNodeCodes);
                    return nodes.length === 0 || nodes.indexOf(nodeCode) >= 0;
                });
            },
            attachmentQueryPath: function (instanceId) {
                return API_PATHS.attachments + toQuery({
                    instanceId: instanceId,
                    operatorUserId: this.currentUserId
                });
            },
            isTaskClaimed: function (task) {
                return !!(task && (hasText(task.assigneeUserId) || task.taskStatus === "CLAIMED"));
            },
            isTaskClaimedByCurrentUser: function (task) {
                return !!(task && hasText(task.assigneeUserId) && task.assigneeUserId === this.currentUserId);
            },
            isCurrentUserTaskCandidate: function (task) {
                if (!task) {
                    return false;
                }
                var candidates = asArray(task.candidateUserIds);
                return candidates.indexOf(this.currentUserId) >= 0 || hasText(task.delegateFromUserId);
            },
            canClaimTask: function (task) {
                return !!(task && extractTaskId(task) && !this.isTaskClaimed(task)
                    && this.isCurrentUserTaskCandidate(task));
            },
            canUnclaimTask: function (task) {
                return this.isTaskClaimedByCurrentUser(task);
            },
            canHandleTask: function (task) {
                return this.isTaskClaimedByCurrentUser(task);
            },
            canRemindInstanceTask: function (task) {
                return !!(task && extractTaskId(task));
            },
            currentInstanceNodeCodes: function (row) {
                return asArray(row && row.currentNodeCodes).filter(hasText);
            },
            canRemindInstanceNode: function (row, nodeCode) {
                return !!(row && extractInstanceId(row) && hasText(nodeCode));
            },
            findInstanceActiveTaskByNode: function (tasks, nodeCode) {
                return normalizeList(tasks).find(function (task) {
                    return task && task.nodeCode === nodeCode && extractTaskId(task);
                });
            },
            taskClaimStatusText: function (task) {
                return this.isTaskClaimed(task) ? "已认领" : "未认领";
            },
            taskAssigneeText: function (task) {
                if (!task || !this.isTaskClaimed(task)) {
                    return "-";
                }
                return task.assigneeUserName || task.assigneeUserId || "-";
            },
            claimTask: function (row) {
                this.submitTaskClaimAction("认领", API_PATHS.taskClaim, row);
            },
            unclaimTask: function (row) {
                this.submitTaskClaimAction("取消认领", API_PATHS.taskUnclaim, row);
            },
            claimCurrentTask: function () {
                this.claimTask(this.taskDialog.task);
            },
            unclaimCurrentTask: function () {
                this.unclaimTask(this.taskDialog.task);
            },
            remindInstanceTask: function (task) {
                var taskId = extractTaskId(task);
                var instanceId = task && task.instanceId;
                if (!hasText(taskId)) {
                    this.setOperationState("error", "手动催办失败", "缺少任务 ID");
                    return Promise.resolve(null);
                }
                var body = {
                    operationId: this.createOperationId("remind"),
                    taskId: taskId,
                    expectedTaskVersion: extractTaskVersion(task),
                    operatorUserId: this.currentUserId
                };
                return this.sendRequest("手动催办", "POST", API_PATHS.remindTask(taskId), body).then(function (payload) {
                    var refresh = hasText(instanceId)
                        ? this.loadSelectedInstanceDetail(instanceId)
                        : Promise.resolve(null);
                    return refresh.then(function () {
                        return this.queryInstances().then(function () {
                            var reminderId = payload && (payload.reminderId || payload.id);
                            this.setOperationState("success", "手动催办成功", reminderId || taskId);
                            return payload;
                        }.bind(this));
                    }.bind(this));
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", "手动催办失败", error.message);
                }.bind(this));
            },
            remindInstanceNode: function (row, nodeCode) {
                var instanceId = extractInstanceId(row);
                if (!hasText(instanceId) || !hasText(nodeCode)) {
                    this.setOperationState("error", "手动催办失败", "缺少实例 ID 或节点编码");
                    return Promise.resolve(null);
                }
                return this.loadSelectedInstanceDetail(instanceId).then(function (detail) {
                    var task = this.findInstanceActiveTaskByNode(detail && detail.activeTasks, nodeCode);
                    if (!task) {
                        this.setOperationState("error", "手动催办失败", "当前节点未找到活动任务");
                        return null;
                    }
                    task.instanceId = task.instanceId || instanceId;
                    return this.remindInstanceTask(task);
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", "手动催办失败", error.message);
                }.bind(this));
            },
            submitTaskClaimAction: function (label, path, task) {
                var taskId = extractTaskId(task);
                if (!hasText(taskId)) {
                    this.setOperationState("error", label + "失败", "缺少任务 ID");
                    return;
                }
                var body = {
                    operationId: this.createOperationId(label),
                    taskId: taskId,
                    expectedTaskVersion: extractTaskVersion(task),
                    operatorUserId: this.currentUserId,
                    comment: this.taskDialog.open ? this.taskDialog.comment : ""
                };
                this.sendRequest(label, "POST", path, body).then(function (payload) {
                    var updatedTask = this.extractUpdatedTask(payload, taskId);
                    if (updatedTask) {
                        this.replaceTodoTask(updatedTask);
                    }
                    return this.queryTodoTasks().then(function () {
                        this.syncOpenTaskDialog(taskId, updatedTask);
                        this.setOperationState("success", label + "成功", "待办已刷新");
                    }.bind(this));
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", label + "失败", error.message);
                }.bind(this));
            },
            extractUpdatedTask: function (payload, taskId) {
                var updatedTasks = normalizeList(payload && payload.updatedTasks);
                return updatedTasks.find(function (task) {
                    return extractTaskId(task) === taskId;
                }) || updatedTasks[0] || null;
            },
            replaceTodoTask: function (updatedTask) {
                var updatedTaskId = extractTaskId(updatedTask);
                this.todoRows = this.todoRows.map(function (row) {
                    return extractTaskId(row) === updatedTaskId ? updatedTask : row;
                });
            },
            syncOpenTaskDialog: function (taskId, fallbackTask) {
                if (!this.taskDialog.open || extractTaskId(this.taskDialog.task) !== taskId) {
                    return;
                }
                var freshTask = this.todoRows.find(function (row) {
                    return extractTaskId(row) === taskId;
                }) || fallbackTask;
                if (freshTask) {
                    this.taskDialog.task = freshTask;
                }
            },
            approveCurrentTask: function () {
                this.submitTaskAction("审批通过", API_PATHS.taskApprove, {}).catch(function () {});
            },
            submitCurrentStarterTask: function () {
                try {
                    this.runTaskActionAfterSaving("重新提交", API_PATHS.taskSubmit, {
                        variables: parseJsonObject(this.taskDialog.variablesText, {}),
                        attachments: []
                    });
                } catch (error) {
                    this.setOperationState("error", "重新提交参数错误", error.message);
                }
            },
            directSendCurrentTask: function () {
                var context = this.taskDialog.directSendContext || {};
                if (!context.allowed || !hasText(context.targetNodeCode)) {
                    this.setOperationState("error", "直送失败", "服务端未返回可信的直送目标");
                    return;
                }
                try {
                    var extra = {targetNodeCode: context.targetNodeCode};
                    if (this.taskDialog.starterTask) {
                        extra.variables = parseJsonObject(this.taskDialog.variablesText, {});
                    }
                    this.runTaskActionAfterSaving("直送", API_PATHS.taskDirectSend, extra);
                } catch (error) {
                    this.setOperationState("error", "直送参数错误", error.message);
                }
            },
            rejectCurrentTask: function () {
                this.submitTaskAction("驳回", API_PATHS.taskReject, {
                    targetNodeCode: this.taskDialog.rejectTargetNodeCode
                }).catch(function () {});
            },
            transferCurrentTask: function () {
                this.submitTaskAction("转办", API_PATHS.taskTransfer, {
                    targetUserId: this.taskDialog.transferUserId
                }).catch(function () {});
            },
            addSignCurrentTask: function () {
                this.submitTaskAction("加签", API_PATHS.taskAddSign, {
                    addSignUserIds: this.taskDialog.addSignUserIds
                }).catch(function () {});
            },
            delegateCurrentTask: function () {
                if (!hasText(this.taskDialog.delegateUserId)) {
                    this.setOperationState("error", "委托代办失败", "请选择代办人");
                    return;
                }
                this.submitTaskAction("委托代办", API_PATHS.delegateTask, {
                    targetUserId: this.taskDialog.delegateUserId
                }).catch(function () {});
            },
            remindCurrentTask: function () {
                this.submitTaskAction("手动催办", API_PATHS.taskRemind, {}).catch(function () {});
            },
            runTaskActionAfterSaving: function (label, path, extra) {
                return this.savePendingTaskAttachmentReplacements().then(function () {
                    return this.submitTaskAction(label, path, extra);
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", label + "失败", error.message);
                }.bind(this));
            },
            handleTaskReplacementFileChange: function (attachment, event) {
                var file = event && event.target && event.target.files && event.target.files[0];
                if (!file) {
                    return;
                }
                this.readFileAsBase64(file).then(function (content) {
                    attachment.pendingReplacement = {
                        operationId: this.createOperationId("replace_attachment"),
                        attachmentCode: attachment.attachmentCode,
                        fieldCode: attachment.fieldCode || "",
                        ownerType: "INSTANCE",
                        fileName: file.name,
                        contentType: file.type || "application/octet-stream",
                        sizeBytes: file.size,
                        content: content,
                        saved: false
                    };
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", "读取替换附件失败", error.message);
                }.bind(this));
            },
            savePendingTaskAttachmentReplacements: function () {
                if (!this.taskDialog.starterTask) {
                    return Promise.resolve([]);
                }
                var task = this.taskDialog.task;
                var taskId = extractTaskId(task);
                var instanceId = task.instanceId;
                var expectedVersion = extractTaskVersion(task);
                var jobs = [];
                this.taskDialog.attachments.forEach(function (attachment) {
                    var replacement = attachment.pendingReplacement;
                    if (!replacement || replacement.saved) {
                        return;
                    }
                    jobs.push(function () {
                        return this.sendRequest("替换实例附件", "PUT",
                            API_PATHS.replaceInstanceAttachment(instanceId,
                                attachment.attachmentId || attachment.id), {
                                operationId: replacement.operationId,
                                sourceTaskId: taskId,
                                expectedTaskVersion: expectedVersion,
                                operatorUserId: this.currentUserId,
                                attachment: this.attachmentUploadPayload(replacement)
                            }).then(function (saved) {
                                replacement.saved = true;
                                Object.assign(attachment, saved || {});
                                attachment.pendingReplacement = null;
                                return saved;
                            });
                    }.bind(this));
                }, this);
                return jobs.reduce(function (chain, job) {
                    return chain.then(function (results) {
                        return job().then(function (saved) {
                            results.push(saved);
                            return results;
                        });
                    });
                }, Promise.resolve([]));
            },
            attachmentUploadPayload: function (attachment) {
                return {
                    attachmentCode: attachment.attachmentCode,
                    fieldCode: attachment.fieldCode || "",
                    ownerType: "INSTANCE",
                    fileName: attachment.fileName,
                    contentType: attachment.contentType,
                    sizeBytes: attachment.sizeBytes,
                    content: attachment.content
                };
            },
            submitTaskAction: function (label, pathBuilder, extra) {
                var taskId = extractTaskId(this.taskDialog.task);
                var instanceId = this.taskDialog.task.instanceId;
                if (!this.canHandleTask(this.taskDialog.task)) {
                    this.setOperationState("error", label + "失败", "请先认领任务");
                    return;
                }
                var path = typeof pathBuilder === "function" ? pathBuilder(taskId) : pathBuilder;
                var body = Object.assign({
                    operationId: this.createOperationId(label),
                    taskId: taskId,
                    expectedTaskVersion: extractTaskVersion(this.taskDialog.task),
                    operatorUserId: this.currentUserId,
                    comment: this.taskDialog.comment
                }, extra || {});
                return this.sendRequest(label, "POST", path, body).then(function (result) {
                    return this.refreshAfterTaskAction(instanceId).then(function () {
                        this.taskDialog.open = false;
                        return result;
                    }.bind(this));
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", label + "失败", error.message);
                    throw error;
                }.bind(this));
            },
            submitStandaloneTaskAction: function (label, pathBuilder, task, extra) {
                var taskId = extractTaskId(task);
                if (!taskId) {
                    this.setOperationState("error", label + "失败", "未找到可操作的活动任务");
                    return Promise.resolve(null);
                }
                var path = typeof pathBuilder === "function" ? pathBuilder(taskId) : pathBuilder;
                var body = Object.assign({
                    operationId: this.createOperationId(label),
                    taskId: taskId,
                    expectedTaskVersion: extractTaskVersion(task),
                    operatorUserId: this.currentUserId,
                    comment: this.instanceOperationComment
                }, extra || {});
                return this.sendRequest(label, "POST", path, body).then(function (payload) {
                    return Promise.all([
                        this.queryTodoTasks(),
                        this.queryCompletedTasks(),
                        this.queryInstances()
                    ]).then(function () {
                        if (this.selectedInstanceId()) {
                            return this.selectInstance({instanceId: this.selectedInstanceId()}).then(function () {
                                return payload;
                            });
                        }
                        return payload;
                    }.bind(this));
                }.bind(this));
            },
            refreshAfterTaskAction: function (instanceId) {
                return Promise.all([
                    this.queryTodoTasks(),
                    this.queryCompletedTasks(),
                    this.queryInstances(),
                    this.sendRequest("刷新实例变量", "GET", API_PATHS.instanceDetail(instanceId)),
                    this.sendRequest("刷新实例附件", "GET", this.attachmentQueryPath(instanceId))
                ]).then(function (results) {
                    this.selectedInstanceDetail = results[3];
                    this.taskDialog.attachments = normalizeList(results[4]);
                    return results;
                }.bind(this));
            },
            appendRequestQuery: function (url, params) {
                var query = toQuery(params);
                if (!query) {
                    return url;
                }
                return url + (url.indexOf("?") >= 0 ? "&" : "?") + query.substring(1);
            },
            sendRequest: function (label, method, path, body) {
                var methodName = String(method || "GET").toUpperCase();
                var url = this.apiBaseUrl + path;
                var operationId = body && body.operationId;
                this.setOperationState("loading", label + "中", path);
                this.addOperationLog(label, path, "loading", operationId, "请求发送中");
                var options = {
                    method: methodName,
                    headers: {
                        "Accept": "application/json",
                        "X-Flow-User-Id": this.currentUserId,
                        "X-Flow-Dept-Id": userDepartmentId(this.currentUser())
                    }
                };
                if (operationId) {
                    options.headers["Idempotency-Key"] = operationId;
                }
                if (body && (methodName === "GET" || methodName === "HEAD")) {
                    url = this.appendRequestQuery(url, body);
                } else if (methodName !== "GET" && methodName !== "HEAD") {
                    options.headers["Content-Type"] = "application/json";
                    body = body || {};
                    options.body = JSON.stringify(body);
                }
                return fetch(url, options).then(function (response) {
                    return response.text().then(function (text) {
                        var payload = text ? JSON.parse(text) : null;
                        if (!response.ok) {
                            throw new Error(responseSummary(payload) || response.statusText);
                        }
                        this.setOperationState("success", label + "成功", path);
                        this.addOperationLog(label, path, "success", operationId, responseSummary(payload));
                        return payload;
                    }.bind(this));
                }.bind(this)).catch(function (error) {
                    this.addOperationLog(label, path, "error", operationId, error.message);
                    this.setOperationState("error", label + "失败", error.message);
                    throw error;
                }.bind(this));
            },
            setOperationState: function (status, label, message) {
                this.operationState = {
                    status: status,
                    label: label,
                    message: message || ""
                };
            },
            addOperationLog: function (label, path, status, operationId, summary) {
                this.operationLogs.unshift({
                    logId: nextLocalId("log"),
                    time: nowText(),
                    label: label,
                    path: path,
                    status: status,
                    operationId: operationId,
                    summary: summary || ""
                });
                if (this.operationLogs.length > 80) {
                    this.operationLogs.pop();
                }
            },
            createOperationId: function (prefix) {
                var safePrefix = this.toAsciiToken(prefix);
                var safeUserId = this.toAsciiToken(this.currentUserId);
                return safePrefix + "_" + safeUserId + "_" + Date.now() + "_" + Math.floor(Math.random() * 10000);
            },
            toAsciiToken: function (value) {
                var raw = String(value || "");
                var token = raw.replace(/[^A-Za-z0-9_-]+/g, "_").replace(/^_+|_+$/g, "");
                var hash = 0;
                var index;
                if (token) {
                    return token;
                }
                if (!raw) {
                    return "op";
                }
                for (index = 0; index < raw.length; index += 1) {
                    hash = ((hash << 5) - hash) + raw.charCodeAt(index);
                    hash |= 0;
                }
                return "op_" + Math.abs(hash).toString(36);
            },
            captureTemplatesFromDetail: function (detail) {
                var configs = this.normalizeAttachmentConfigsFromDetail(detail);
                configs.forEach(function (config) {
                    if (!hasText(config.attachmentTemplateId)) {
                        return;
                    }
                    var exists = this.attachmentTemplateLibrary.some(function (template) {
                        return template.attachmentTemplateId === config.attachmentTemplateId;
                    });
                    if (!exists) {
                        this.attachmentTemplateLibrary.push({
                            attachmentTemplateId: config.attachmentTemplateId,
                            attachmentCode: config.attachmentCode,
                            templateVersion: config.templateVersion || 1,
                            attachmentName: config.attachmentName || config.attachmentCode,
                            allowedExtensions: asArray(config.allowedExtensions || config.allowedExtensionsText),
                            maxSizeBytes: config.maxSizeBytes || 10485760
                        });
                    }
                }, this);
            },
            normalizeAttachmentConfigsFromDetail: function (detail) {
                if (!detail) {
                    return [];
                }
                var configs = detail.attachmentConfigs || detail.attachmentTemplates || [];
                return normalizeList(configs).map(normalizeAttachmentConfig);
            },
            syncSelectedNodeApprover: function () {
                if (this.selectedNode) {
                    this.selectedNode.approverRuleConfig = JSON.stringify({userIds: asArray(this.selectedNode.selectedApproverIds)});
                }
            },
            syncSelectedNodeDepartment: function () {
                if (this.selectedNode) {
                    this.selectedNode.approverRuleConfig = JSON.stringify({
                        departmentId: String(this.selectedNode.selectedDepartmentId || "").trim()
                    });
                }
            },
            syncSelectedNodeRole: function () {
                if (this.selectedNode) {
                    this.selectedNode.approverRuleConfig = JSON.stringify({
                        roleCode: String(this.selectedNode.selectedRoleCode || "").trim()
                    });
                }
            },
            syncSelectedNodeRoleDepartment: function () {
                if (!this.selectedNode) {
                    return;
                }
                var config = {roleCode: String(this.selectedNode.selectedRoleCode || "").trim()};
                if (this.selectedNode.selectedRoleDepartmentMode === "FIXED") {
                    config.departmentId = String(this.selectedNode.selectedRoleDepartmentId || "").trim();
                }
                this.selectedNode.approverRuleConfig = JSON.stringify(config);
            },
            syncSelectedNodeExpression: function () {
                if (this.selectedNode) {
                    this.selectedNode.approverRuleConfig = JSON.stringify({
                        expression: approverExpressionText(this.selectedNode.approverExpression)
                    });
                }
            },
            syncSelectedNodeApproverRule: function () {
                var node = this.selectedNode;
                if (!node || node.nodeType !== "USER_TASK") {
                    return;
                }
                if (node.approverRuleType === "STARTER") {
                    node.approverRuleConfig = "{}";
                    node.multiInstanceMode = "SINGLE";
                    return;
                }
                if (node.approverRuleType === "USER") {
                    node.selectedApproverIds = asArray(node.selectedApproverIds);
                    this.syncSelectedNodeApprover();
                    return;
                }
                if (node.approverRuleType === "DEPARTMENT") {
                    node.selectedDepartmentId = node.selectedDepartmentId || "";
                    this.syncSelectedNodeDepartment();
                    return;
                }
                if (node.approverRuleType === "ROLE") {
                    node.selectedRoleCode = node.selectedRoleCode || "";
                    this.syncSelectedNodeRole();
                    return;
                }
                if (node.approverRuleType === "ROLE_IN_DEPARTMENT") {
                    node.selectedRoleCode = node.selectedRoleCode || "";
                    node.selectedRoleDepartmentMode = node.selectedRoleDepartmentMode === "FIXED" ? "FIXED" : "STARTER";
                    node.selectedRoleDepartmentId = node.selectedRoleDepartmentId || "";
                    this.syncSelectedNodeRoleDepartment();
                    return;
                }
                if (node.approverRuleType === "APPROVER_EXPRESSION") {
                    node.approverExpression = node.approverExpression || "";
                    this.syncSelectedNodeExpression();
                }
            },
            listenerRejectTargetNodes: function (node) {
                return this.definitionDraft.nodes.filter(function (candidate) {
                    return candidate.nodeType === "USER_TASK" && candidate.nodeCode !== node.nodeCode;
                });
            },
            taskRejectTargetNodes: function () {
                var task = this.taskDialog.task || {};
                var detailDefinitionId = extractDefinitionId(this.selectedDefinitionDetail);
                if (hasText(task.definitionId) && task.definitionId !== detailDefinitionId) {
                    return [];
                }
                var currentNode = this.selectedGraphNodes.find(function (node) {
                    return node.nodeCode === task.nodeCode;
                });
                if (!currentNode) {
                    return [];
                }
                var editor = readListenerRuleEditor(currentNode.listenerConfig);
                if (editor.listenerConfigError || !editor.listenerRejectEnabled) {
                    return [];
                }
                return editor.listenerRejectTargetNodeCodes.map(function (targetNodeCode) {
                    return this.selectedGraphNodes.find(function (node) {
                        return node.nodeCode === targetNodeCode && this.isUserTaskNode(node);
                    }, this);
                }, this).filter(function (node, index, nodes) {
                    return node && nodes.indexOf(node) === index;
                });
            },
            syncSelectedNodeListenerRules: function () {
                if (this.selectedNode) {
                    this.syncNodeListenerRules(this.selectedNode);
                }
            },
            syncNodeListenerRules: function (node) {
                var parsed = parseListenerConfig(node.listenerConfig);
                if (parsed.error) {
                    node.listenerConfigError = parsed.error;
                    return false;
                }
                var config = parsed.config;
                var rules = isJsonObject(config.taskActionRules) ? clone(config.taskActionRules) : {};
                if (node.listenerRejectEnabled) {
                    var reject = isJsonObject(rules.reject) ? clone(rules.reject) : {};
                    reject.enabled = true;
                    reject.targetNodeCodes = asArray(node.listenerRejectTargetNodeCodes).filter(hasText);
                    rules.reject = reject;
                } else {
                    delete rules.reject;
                }
                if (node.listenerDirectSendEnabled) {
                    var directSend = isJsonObject(rules.directSend) ? clone(rules.directSend) : {};
                    directSend.enabled = true;
                    directSend.targetMode = "REJECT_SOURCE";
                    rules.directSend = directSend;
                } else {
                    delete rules.directSend;
                }
                if (Object.keys(rules).length > 0) {
                    config.taskActionRules = rules;
                } else {
                    delete config.taskActionRules;
                }
                node.listenerConfig = Object.keys(config).length > 0 ? JSON.stringify(config) : "";
                node.listenerConfigError = "";
                return true;
            },
            syncSelectedNodeListenerJson: function () {
                if (this.selectedNode) {
                    applyListenerRuleEditor(this.selectedNode);
                }
            },
            syncSelectedNodeTimeoutConfig: function () {
                if (this.selectedNode) {
                    this.syncNodeTimeoutConfig(this.selectedNode);
                }
            },
            syncNodeTimeoutConfig: function (node) {
                var duration = Number(node.timeoutDurationMinutes || 0);
                var maxCount = Number(node.reminderMaxCount || 0);
                node.timeoutConfigError = "";
                if (node.reminderEnabled && !node.timeoutEnabled) {
                    node.timeoutConfigError = "启用提醒前必须启用超时";
                    return false;
                }
                if (node.timeoutEnabled && (!Number.isFinite(duration) || duration < 0)) {
                    node.timeoutConfigError = "超时时长必须为非负数";
                    return false;
                }
                if (node.timeoutEnabled && node.timeoutAction === "JUMP" && !hasText(node.timeoutTargetNodeCode)) {
                    node.timeoutConfigError = "超时动作选择 JUMP 时必须选择目标节点";
                    return false;
                }
                if (node.reminderEnabled && (!Number.isFinite(maxCount) || maxCount < 0)) {
                    node.timeoutConfigError = "最大提醒次数不得为负数";
                    return false;
                }
                if (node.timeoutEnabled) {
                    var timeoutConfig = {
                        enabled: true,
                        durationMinutes: duration,
                        action: node.timeoutAction || "REMIND"
                    };
                    if (hasText(node.timeoutSeverity)) {
                        timeoutConfig.severity = node.timeoutSeverity;
                    }
                    if (node.timeoutAction === "JUMP") {
                        timeoutConfig.targetNodeCode = node.timeoutTargetNodeCode;
                    }
                    node.timeoutConfig = JSON.stringify(timeoutConfig);
                } else {
                    node.timeoutConfig = "";
                }
                if (node.reminderEnabled) {
                    var reminderConfig = {
                        enabled: true,
                        maxCount: maxCount
                    };
                    if (hasText(node.reminderMessageTemplate)) {
                        reminderConfig.messageTemplate = node.reminderMessageTemplate;
                    }
                    node.reminderConfig = JSON.stringify(reminderConfig);
                } else {
                    node.reminderConfig = "";
                }
                return true;
            },
            validateNodeTimeoutConfig: function (node) {
                if (!node || node.nodeType !== "USER_TASK") {
                    return "";
                }
                return this.syncNodeTimeoutConfig(node) ? "" : node.timeoutConfigError;
            },
            timeoutReminderTargetNodes: function (node) {
                return this.definitionDraft.nodes.filter(function (candidate) {
                    return this.isUserTaskNode(candidate) && candidate.nodeCode !== node.nodeCode;
                }, this);
            },
            pruneListenerRejectTarget: function (node, removedNodeCode) {
                var editor = readListenerRuleEditor(node.listenerConfig);
                if (editor.listenerConfigError
                        || editor.listenerRejectTargetNodeCodes.indexOf(removedNodeCode) < 0) {
                    return;
                }
                node.listenerRejectEnabled = editor.listenerRejectEnabled;
                node.listenerRejectTargetNodeCodes = editor.listenerRejectTargetNodeCodes.filter(function (targetNodeCode) {
                    return targetNodeCode !== removedNodeCode;
                });
                node.listenerDirectSendEnabled = editor.listenerDirectSendEnabled;
                if (node.listenerRejectTargetNodeCodes.length === 0) {
                    node.listenerRejectEnabled = false;
                }
                this.syncNodeListenerRules(node);
            },
            canvasMetrics: function (nodes, minHeight) {
                var metrics = {
                    width: CANVAS_MIN_WIDTH,
                    height: Number(minHeight || CANVAS_READONLY_MIN_HEIGHT)
                };
                asArray(nodes).forEach(function (node) {
                    metrics.width = Math.max(metrics.width, Number(node.positionX || 0) + CANVAS_NODE_WIDTH + CANVAS_PADDING);
                    metrics.height = Math.max(metrics.height, Number(node.positionY || 0) + CANVAS_NODE_HEIGHT + CANVAS_PADDING);
                });
                return metrics;
            },
            canvasSurfaceStyle: function (nodes, minHeight) {
                var metrics = this.canvasMetrics(nodes, minHeight || CANVAS_DESIGNER_MIN_HEIGHT);
                return {
                    width: metrics.width + "px",
                    height: metrics.height + "px"
                };
            },
            canvasViewBox: function (nodes, minHeight) {
                var metrics = this.canvasMetrics(nodes, minHeight || CANVAS_DESIGNER_MIN_HEIGHT);
                return "0 0 " + metrics.width + " " + metrics.height;
            },
            nodeStyle: function (node) {
                return {
                    left: Number(node.positionX || 0) + "px",
                    top: Number(node.positionY || 0) + "px"
                };
            },
            nodeClass: function (node) {
                if (node.nodeType === "START") {
                    return "start";
                }
                if (node.nodeType === "END") {
                    return "end";
                }
                if (node.nodeType === "USER_TASK") {
                    return "user";
                }
                return "gateway";
            },
            draftEdgeLine: function (edge) {
                return this.edgeLineFromNodes(edge, this.definitionDraft.nodes);
            },
            edgeLine: function (edge) {
                return this.edgeLineFromNodes(edge, this.selectedGraphNodes);
            },
            edgeLineFromNodes: function (edge, nodes) {
                var source = nodes.find(function (node) {
                    return node.nodeCode === edge.sourceNodeCode;
                });
                var target = nodes.find(function (node) {
                    return node.nodeCode === edge.targetNodeCode;
                });
                if (!source || !target) {
                    return {x1: 0, y1: 0, x2: 0, y2: 0};
                }
                return {
                    x1: Number(source.positionX || 0) + 66,
                    y1: Number(source.positionY || 0) + 29,
                    x2: Number(target.positionX || 0) + 66,
                    y2: Number(target.positionY || 0) + 29
                };
            },
            formFieldCode: function (field) {
                return field && (field.fieldCode || field.code || field.name || field.fieldName || "field");
            },
            formFieldLabel: function (field) {
                return field && (field.fieldName || field.label || field.fieldCode || field.code || "字段");
            },
            formFieldInputType: function (field) {
                var controlType = String((field && (field.controlType || field.inputType || field.fieldType)) || "").toLowerCase();
                if (controlType === "textarea" || controlType === "multi_line") {
                    return "textarea";
                }
                if (["number", "date", "datetime-local", "checkbox"].indexOf(controlType) >= 0) {
                    return controlType;
                }
                return "text";
            },
            isTextareaField: function (field) {
                var controlType = String((field && (field.controlType || field.inputType || field.fieldType)) || "").toLowerCase();
                return controlType === "textarea" || controlType === "multi_line";
            },
            initializeInstanceFieldValues: function (fields, sourceValues) {
                var values = {};
                var source = sourceValues || {};
                normalizeList(fields).forEach(function (field) {
                    var code = this.formFieldCode(field);
                    if (Object.prototype.hasOwnProperty.call(source, code)) {
                        values[code] = source[code];
                    } else if (Object.prototype.hasOwnProperty.call(field, "defaultValue")) {
                        values[code] = field.defaultValue;
                    } else {
                        values[code] = this.formFieldInputType(field) === "checkbox" ? false : "";
                    }
                }, this);
                this.instanceFieldValues = values;
                this.startFieldErrors = {};
            },
            initializeInstanceVariableValues: function (fields, sourceValues) {
                var values = {};
                var source = sourceValues || {};
                normalizeList(fields).forEach(function (field) {
                    var code = this.formFieldCode(field);
                    values[code] = Object.prototype.hasOwnProperty.call(source, code) ? source[code] : "";
                }, this);
                this.instanceVariableValues = values;
            },
            isStartFieldRequired: function (field) {
                return !!field;
            },
            startFormFieldError: function (field) {
                var code = this.formFieldCode(field);
                return this.startFieldErrors[code] || "";
            },
            clearStartFormFieldError: function (field) {
                var code = this.formFieldCode(field);
                if (this.startFieldErrors[code]) {
                    var errors = Object.assign({}, this.startFieldErrors);
                    delete errors[code];
                    this.startFieldErrors = errors;
                }
            },
            validateStartFormFields: function () {
                var fields = normalizeList(this.instanceDefinitionFields);
                var errors = {};
                var missingLabels = [];
                if (fields.length === 0) {
                    throw new Error("请先为流程定义配置表单字段");
                }
                fields.forEach(function (field) {
                    var code = this.formFieldCode(field);
                    var value = this.instanceFieldValues ? this.instanceFieldValues[code] : undefined;
                    if (!this.isStartFieldValueFilled(field, value)) {
                        errors[code] = "必填";
                        missingLabels.push(this.formFieldLabel(field));
                    }
                }, this);
                this.startFieldErrors = errors;
                if (missingLabels.length > 0) {
                    throw new Error("请填写必填表单字段：" + missingLabels.join("、"));
                }
                return true;
            },
            isStartFieldValueFilled: function (field, value) {
                if (this.formFieldInputType(field) === "checkbox") {
                    return value === true;
                }
                return hasText(value);
            },
            buildVariablesFromFields: function (fields, values, includeBlank) {
                var variables = {};
                var source = values || {};
                normalizeList(fields).forEach(function (field) {
                    var code = this.formFieldCode(field);
                    var value = source[code];
                    if (!includeBlank && !hasText(value)) {
                        return;
                    }
                    variables[code] = this.coerceFormFieldValue(field, value);
                }, this);
                return variables;
            },
            coerceFormFieldValue: function (field, value) {
                var inputType = this.formFieldInputType(field);
                if (inputType === "number") {
                    return hasText(value) ? Number(value) : null;
                }
                if (inputType === "checkbox") {
                    return value === true;
                }
                return value;
            },
            extractInstanceVariables: function (detail) {
                var source = detail || {};
                if (isJsonObject(source.variables)) {
                    return source.variables;
                }
                if (isJsonObject(source.variablesJson)) {
                    return source.variablesJson;
                }
                if (hasText(source.variablesJson)) {
                    try {
                        return JSON.parse(source.variablesJson);
                    } catch (ignore) {
                        return {};
                    }
                }
                return {};
            },
            applyInstanceFormFields: function () {
                this.initializeInstanceFieldValues(this.instanceDefinitionFields, this.instanceFieldValues);
            },
            buildInstanceVariableRows: function () {
                this.initializeInstanceVariableValues(this.selectedInstanceFields, this.extractInstanceVariables(this.selectedInstanceDetail));
                return this.instanceVariableRows;
            },
            isOwnTodoTask: function (row) {
                return !!row && !hasText(row.delegateFromUserId)
                        && (!hasText(row.assigneeUserId) || row.assigneeUserId === this.currentUserId);
            },
            todoScopeLabel: function (task) {
                if (task && hasText(task.delegateFromUserId)) {
                    return "委托代办";
                }
                if (task && hasText(task.assigneeUserId) && task.assigneeUserId === this.currentUserId) {
                    return "自己的任务";
                }
                return "候选任务";
            },
            taskSourceLabel: function (task) {
                return this.todoScopeLabel(task);
            },
            formatJsonSummary: function (value) {
                if (value === null || typeof value === "undefined" || value === "") {
                    return "-";
                }
                if (typeof value === "string") {
                    return value;
                }
                return JSON.stringify(value).slice(0, 180);
            },
            attachmentRowKey: function (item) {
                return item.localId || item.configId || item.attachmentTemplateId || item.attachmentCode;
            },
            joinList: function (value) {
                return asArray(value).join(", ");
            },
            formatAttachmentSize: function (sizeBytes) {
                var size = Number(sizeBytes || 0);
                if (!size || size < 0) {
                    return "0 B";
                }
                if (size < 1024) {
                    return size + " B";
                }
                if (size < 1024 * 1024) {
                    return (size / 1024).toFixed(size >= 10 * 1024 ? 0 : 1) + " KB";
                }
                return (size / (1024 * 1024)).toFixed(size >= 10 * 1024 * 1024 ? 0 : 1) + " MB";
            },
            formatDateTime: function (value) {
                if (!hasText(value)) {
                    return "-";
                }
                return String(value).replace("T", " ").slice(0, 19);
            },
            formatDurationMillis: function (milliseconds) {
                var minutes = Math.max(1, Math.ceil(milliseconds / 60000));
                if (minutes < 60) {
                    return "剩余" + minutes + "分钟";
                }
                var hours = Math.ceil(minutes / 60);
                return "剩余" + hours + "小时";
            },
            taskTimeoutState: function (task) {
                var dueAt = parseDateTimeValue(task && task.dueAt);
                if (!dueAt) {
                    return "";
                }
                var diff = dueAt.getTime() - Date.now();
                if (diff <= 0) {
                    return "overdue";
                }
                return diff <= 30 * 60 * 1000 ? "soon" : "due";
            },
            taskTimeoutBadge: function (task) {
                var dueAt = parseDateTimeValue(task && task.dueAt);
                var parts = [];
                if (dueAt) {
                    var diff = dueAt.getTime() - Date.now();
                    if (diff <= 0) {
                        parts.push("已超时");
                    } else if (diff <= 30 * 60 * 1000) {
                        parts.push("即将超时");
                    } else {
                        parts.push(this.formatDurationMillis(diff));
                    }
                }
                var reminder = this.todoReminderStatusByTaskId[extractTaskId(task)];
                if (reminder && reminder.label) {
                    parts.push(reminder.label);
                }
                return parts.join(" / ");
            },
            taskTimeoutBadgeClass: function (task) {
                var reminder = this.todoReminderStatusByTaskId[extractTaskId(task)] || {};
                return {
                    overdue: this.taskTimeoutState(task) === "overdue",
                    soon: this.taskTimeoutState(task) === "soon",
                    reminded: reminder.className === "reminded",
                    failed: reminder.className === "failed"
                };
            },
            isUserTaskNode: function (node) {
                return node && node.nodeType === "USER_TASK";
            },
            isParallelGatewayNode: function (node) {
                return node && (node.nodeType === "PARALLEL_SPLIT_GATEWAY"
                    || node.nodeType === "PARALLEL_JOIN_GATEWAY");
            },
            parallelGatewayPairOptions: function (node) {
                var expectedType = this.expectedParallelGatewayPairType(node);
                if (!expectedType) {
                    return [];
                }
                return this.definitionDraft.nodes.filter(function (candidate) {
                    return candidate.nodeCode !== node.nodeCode && candidate.nodeType === expectedType;
                });
            },
            syncParallelGatewayPair: function (node) {
                var pairedCode;
                if (!node) {
                    return;
                }
                pairedCode = node.pairedGatewayCode;
                this.definitionDraft.nodes.forEach(function (candidate) {
                    if (candidate.nodeCode !== node.nodeCode
                            && (candidate.pairedGatewayCode === node.nodeCode || candidate.pairedGatewayCode === pairedCode)) {
                        candidate.pairedGatewayCode = "";
                    }
                });
                if (!hasText(pairedCode)) {
                    return;
                }
                this.definitionDraft.nodes.forEach(function (candidate) {
                    if (candidate.nodeCode === pairedCode && this.isExpectedParallelGatewayPair(node, candidate)) {
                        candidate.pairedGatewayCode = node.nodeCode;
                    }
                }, this);
            },
            expectedParallelGatewayPairType: function (node) {
                if (!node) {
                    return "";
                }
                if (node.nodeType === "PARALLEL_SPLIT_GATEWAY") {
                    return "PARALLEL_JOIN_GATEWAY";
                }
                if (node.nodeType === "PARALLEL_JOIN_GATEWAY") {
                    return "PARALLEL_SPLIT_GATEWAY";
                }
                return "";
            },
            isExpectedParallelGatewayPair: function (node, paired) {
                return Boolean(node && paired && paired.nodeType === this.expectedParallelGatewayPairType(node));
            }
        }
    });

    app.mount("#app");
}());
