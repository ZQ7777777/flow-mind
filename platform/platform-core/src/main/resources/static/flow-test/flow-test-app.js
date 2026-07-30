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
        instanceDetail: function (instanceId) {
            return "/api/platform/instances/" + encodeURIComponent(instanceId);
        },
        startedInstances: "/api/platform/instances/started",
        taskSubmit: "/api/platform/runtime/tasks/submit",
        taskApprove: "/api/platform/runtime/tasks/approve",
        taskReject: "/api/platform/runtime/tasks/reject",
        taskTransfer: "/api/platform/runtime/tasks/transfer",
        taskAddSign: "/api/platform/runtime/tasks/add-sign",
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
        attachments: "/api/platform/attachments",
        attachmentTemplates: "/api/platform/attachment-templates"
    };

    var FLOW_TEST_USERS = [
        {userId: "u_sales_01", userName: "业务员", role: "TEST_OPERATOR", deptId: "mock-dept", deptName: "Mock Department"},
        {userId: "u_group_leader_01", userName: "组长", role: "TEST_OPERATOR", deptId: "mock-dept", deptName: "Mock Department"},
        {userId: "u_dept_manager_01", userName: "部门经理1", role: "TEST_OPERATOR", deptId: "mock-dept", deptName: "Mock Department"},
        {userId: "u_dept_manager_02", userName: "部门经理2", role: "TEST_OPERATOR", deptId: "mock-dept", deptName: "Mock Department"},
        {userId: "u_finance_01", userName: "财务1", role: "TEST_OPERATOR", deptId: "mock-dept", deptName: "Mock Department"},
        {userId: "u_finance_02", userName: "财务2", role: "TEST_OPERATOR", deptId: "mock-dept", deptName: "Mock Department"},
        {userId: "u_ceo_01", userName: "CEO", role: "TEST_OPERATOR", deptId: "mock-dept", deptName: "Mock Department"},
        {userId: "u_admin_01", userName: "测试管理员", role: "TEST_ADMIN", deptId: "mock-dept", deptName: "Mock Department"}
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
            multiInstanceMode: mode || "SINGLE",
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
        var approverIds = [];
        if (typeof ruleConfig === "string") {
            try {
                approverIds = asArray(JSON.parse(ruleConfig).userIds);
            } catch (ignore) {
                approverIds = [];
            }
        } else if (ruleConfig) {
            approverIds = asArray(ruleConfig.userIds);
        }
        return Object.assign(buildNode(
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
            approverRuleConfig: stringifyRule(ruleConfig)
        });
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
                instanceVariablesText: "{\n  \"amount\": 10000\n}",
                instanceAttachments: [],
                instanceRows: [],
                selectedInstanceDetail: null,
                todoRows: [],
                completedRows: [],
                taskDialog: {
                    open: false,
                    task: {},
                    variablesText: "{}",
                    comment: "",
                    attachments: [],
                    rejectTargetNodeCode: "",
                    transferUserId: "",
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
            canManageDefinitions: function () {
                return this.currentRole === "TEST_ADMIN" || this.currentRole === "DEFINITION_ADMIN";
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
            instanceDefinitionAttachments: function () {
                var row = this.definitionRows.find(function (definition) {
                    return extractDefinitionId(definition) === this.instanceForm.definitionId;
                }, this);
                if (row && Array.isArray(row.attachmentConfigs)) {
                    return row.attachmentConfigs;
                }
                return this.selectedAttachmentConfigs;
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
                    this.instanceForm.starterDeptId = user.deptId;
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
                    });
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
                    node.multiInstanceMode = ids.length <= 1 ? "SINGLE" : (node.multiInstanceMode || "OR_SIGN");
                    return node;
                }
                node.approverRuleConfig = stringifyRule(node.approverRuleConfig);
                node.multiInstanceMode = node.multiInstanceMode || "SINGLE";
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
                    if (node.nodeType === "USER_TASK" && node.approverRuleType === "USER" && asArray(node.selectedApproverIds).length === 0) {
                        errors.push("用户任务必须选择审批人：" + node.nodeName);
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
                    this.selectDefinition(row);
                }
            },
            startAndSubmitInstance: function () {
                try {
                    var startBody = this.buildStartSubmitBody();
                    this.sendRequest("启动流程", "POST", API_PATHS.startAndSubmit, startBody).then(function (instance) {
                        this.setOperationState("success", "启动并提交成功", "实例已创建并提交申请节点");
                        this.queryInstances();
                        this.queryTodoTasks();
                        return instance;
                    }.bind(this)).catch(function (error) {
                        this.setOperationState("error", "启动并提交失败", error.message);
                    }.bind(this));
                } catch (error) {
                    this.setOperationState("error", "启动参数错误", error.message);
                }
            },
            buildStartSubmitBody: function () {
                if (!hasText(this.instanceForm.processCode)) {
                    throw new Error("请选择流程定义");
                }
                var user = this.currentUser();
                return {
                    operationId: this.createOperationId("start_and_submit"),
                    processCode: this.instanceForm.processCode,
                    businessKey: this.instanceForm.businessKey,
                    instanceTitle: this.instanceForm.instanceTitle,
                    starterUserId: this.currentUserId,
                    starterDeptId: user.deptId,
                    variables: parseJsonObject(this.instanceVariablesText, {}),
                    attachments: this.buildInstanceAttachments()
                };
            },
            addInstanceAttachment: function () {
                this.instanceAttachments.push({
                    localId: nextLocalId("instance_attach"),
                    enabled: true,
                    attachmentCode: "bankReceipt",
                    ownerType: "INSTANCE",
                    fileName: "bank-receipt.pdf",
                    contentType: "application/pdf",
                    sizeBytes: 14,
                    content: "Zmxvdy1taW5kLXRlc3Q="
                });
            },
            removeInstanceAttachment: function (index) {
                this.instanceAttachments.splice(index, 1);
            },
            buildInstanceAttachments: function () {
                return this.instanceAttachments.filter(function (attachment) {
                    return attachment.enabled;
                }).map(function (attachment) {
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
            findCreatedApplyTask: function (instance) {
                var tasks = normalizeList(instance && (instance.createdTasks || instance.tasks));
                return tasks.find(function (task) {
                    return task.nodeCode === "apply" || task.nodeCode === "APPLY";
                }) || tasks[0];
            },
            queryInstances: function () {
                return this.sendRequest("查询实例", "GET", API_PATHS.startedInstances + toQuery({
                    starterUserId: this.currentUserId,
                    pageNo: 1,
                    pageSize: 50
                })).then(function (payload) {
                    this.instanceRows = normalizeList(payload);
                    return payload;
                }.bind(this));
            },
            selectInstance: function (row) {
                var instanceId = extractInstanceId(row);
                if (!instanceId) {
                    return;
                }
                this.sendRequest("查询实例详情", "GET", API_PATHS.instanceDetail(instanceId)).then(function (payload) {
                    this.selectedInstanceDetail = payload;
                }.bind(this));
            },
            queryTodoTasks: function () {
                return this.sendRequest("查询待办", "GET", API_PATHS.todoTasks + toQuery({
                    userId: this.currentUserId,
                    pageNo: 1,
                    pageSize: 50
                })).then(function (payload) {
                    this.todoRows = normalizeList(payload);
                    return payload;
                }.bind(this));
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
                    rejectTargetNodeCode: "",
                    transferUserId: "",
                    addSignUserIds: []
                };
                var instanceId = row.instanceId;
                if (hasText(instanceId)) {
                    this.loadTaskDialogContext(instanceId, "todo");
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
                this.sendRequest("查询已办实例详情", "GET", API_PATHS.instanceDetail(instanceId)).then(function (payload) {
                    this.completedDialog.currentNodeCodes = asArray(payload.currentNodeCodes);
                }.bind(this));
                this.sendRequest("查询已办附件", "GET", API_PATHS.attachments + toQuery({instanceId: instanceId})).then(function (payload) {
                    this.completedDialog.attachments = normalizeList(payload);
                }.bind(this));
            },
            loadTaskDialogContext: function (instanceId) {
                this.sendRequest("查询任务附件", "GET", API_PATHS.attachments + toQuery({instanceId: instanceId})).then(function (payload) {
                    this.taskDialog.attachments = normalizeList(payload);
                }.bind(this));
                this.sendRequest("查询任务实例详情", "GET", API_PATHS.instanceDetail(instanceId)).then(function (payload) {
                    this.taskDialog.variablesText = JSON.stringify(payload.variables || payload.variablesJson || {}, null, 2);
                    if (payload.definitionId && payload.definitionId !== this.selectedDefinitionId) {
                        this.selectedDefinitionId = payload.definitionId;
                        return this.selectDefinition({definitionId: payload.definitionId});
                    }
                    return payload;
                }.bind(this));
            },
            approveCurrentTask: function () {
                this.submitTaskAction("审批通过", API_PATHS.taskApprove, {});
            },
            rejectCurrentTask: function () {
                this.submitTaskAction("驳回", API_PATHS.taskReject, {
                    targetNodeCode: this.taskDialog.rejectTargetNodeCode
                });
            },
            transferCurrentTask: function () {
                this.submitTaskAction("转办", API_PATHS.taskTransfer, {
                    targetUserId: this.taskDialog.transferUserId
                });
            },
            addSignCurrentTask: function () {
                this.submitTaskAction("加签", API_PATHS.taskAddSign, {
                    addSignUserIds: this.taskDialog.addSignUserIds
                });
            },
            submitTaskAction: function (label, pathBuilder, extra) {
                var taskId = extractTaskId(this.taskDialog.task);
                var path = typeof pathBuilder === "function" ? pathBuilder(taskId) : pathBuilder;
                var body = Object.assign({
                    operationId: this.createOperationId(label),
                    taskId: taskId,
                    expectedTaskVersion: extractTaskVersion(this.taskDialog.task),
                    operatorUserId: this.currentUserId,
                    comment: this.taskDialog.comment
                }, extra || {});
                this.sendRequest(label, "POST", path, body).then(function () {
                    this.taskDialog.open = false;
                    this.queryTodoTasks();
                    this.queryCompletedTasks();
                }.bind(this)).catch(function (error) {
                    this.setOperationState("error", label + "失败", error.message);
                }.bind(this));
            },
            sendRequest: function (label, method, path, body) {
                var url = this.apiBaseUrl + path;
                var operationId = body && body.operationId;
                this.setOperationState("loading", label + "中", path);
                this.addOperationLog(label, path, "loading", operationId, "请求发送中");
                var options = {
                    method: method,
                    headers: {
                        "Accept": "application/json",
                        "X-Flow-User-Id": this.currentUserId,
                        "X-Flow-Dept-Id": this.currentUser().deptId
                    }
                };
                if (operationId) {
                    options.headers["Idempotency-Key"] = operationId;
                }
                if (method !== "GET" && method !== "HEAD") {
                    options.headers["Content-Type"] = "application/json";
                    options.body = JSON.stringify(body || {});
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
                    if (this.selectedNode.selectedApproverIds.length <= 1) {
                        this.selectedNode.multiInstanceMode = "SINGLE";
                    }
                }
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
            attachmentRowKey: function (item) {
                return item.localId || item.configId || item.attachmentTemplateId || item.attachmentCode;
            },
            joinList: function (value) {
                return asArray(value).join(", ");
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
