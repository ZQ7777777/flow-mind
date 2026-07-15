# M0

## SPI说明

| SPI | 说明 |
| ------------------------ | ------------------------------------------------------------ |
| ApproverResolver | 解析审批人，返回可办理人员列表 |
| AttachmentAccessProvider | 校验附件访问权限 |
| CurrentUserProvider | 获取当前登录用户信息 |
| DelegateProvider | 查询指定时刻有效的委托关系 |
| FileStorageProvider | 存储、读取和删除文件 |
| MessagePublisher | 推送消息 |
| OrganizationProvider | 查询部门、用户及角色关系 |
| WorkflowCallbackHandler | 处理工作流回调 |

## Start服务接口说明

| service | 说明 |
| ------------------------ | ------------------------------------------------------------ |
| AdminProcessService | 管理流程实例、任务、日志及节点跳转 |
| AttachmentService | 保存、下载、查询、删除和校验附件 |
| CallbackService | 发布回调事件并查询回调日志 |
| ProcessDefinitionService | 管理流程定义的创建、配置、校验、发布、状态、灰度、复制、删除和查询 |
| ProcessMonitorService | 处理任务催办、超时扫描和告警 |
| ProcessRuntimeService | 管理流程启动、任务操作、实例终止与删除、变量更新和详情查询 |
| TaskQueryService | 查询待办、已办、发起流程、活动与历史任务、审批意见和已阅记录 |

## 实体类说明

### DTO说明

| DTO | 说明 |
| -------------------------- | ---------------------------------------------------- |
| AlertDTO | 异常告警记录 |
| AttachmentDownloadDTO | 附件下载结果 |
| AttachmentDTO | 附件元数据 |
| AuditLogDTO | 审计日志 |
| CallbackLogDTO | 回调日志 |
| DelegateRelationDTO | 委托关系，包括委托人和代理人快照信息以及委托事件信息 |
| DepartmentDTO | 部门信息 |
| HistoryTaskDTO | 历史任务 |
| ProcessDefinitionDetailDTO | 流程定义详情 |
| ProcessDefinitionDTO | 流程定义 |
| ProcessInstanceDTO | 流程实例 |
| ProcessInstanceDTO | 流程实例 |
| ProcessNodeDTO | 流程节点定义 |
| ReadRecordDTO | 流程已阅记录 |
| ReminderDTO | 提醒或催办记录 |
| TaskDTO | 任务 |
| UserDTO | 用户DTO包括userID和userName |

### Request说明

| Request | 说明 |
| ------------------------------ | ------------------ |
| AddSignRequest | 加签请求 |
| ApproverResolveRequest | 审批人解析请求 |
| ApproveTaskRequest | 审批任务请求 |
| AttachmentAccessRequest | 附件范文请求 |
| CheckAttachmentRequest | 附件校验请求 |
| ClaimTaskRequest | 认领请求 |
| CopyProcessDefinitionRequest | 复制流程定义请求 |
| CreateProcessDefinitionRequest | 创建流程定义请求 |
| DefinitionOperationRequest | 流程定义操作请求 |
| DeleteAttachmentRequest | 删除附件请求 |
| DeleteProcessInstanceRequest | 删除流程实例请求 |
| DirectSendRequest | 直送节点请求 |
| DownloadAttachmentRequest | 下载附件请求 |
| ForceCompleteRequest | 强制办结请求 |
| GrayReleaseRequest | 灰度发布请求 |
| HandleAlertRequest | 处理告警请求 |
| JumpNodeRequest | 跳转请求 |
| RejectTaskRequest | 驳回任务请求 |
| RemindTaskRequest | 催办请求 |
| ReturnTaskRequest | 退回请求 |
| SaveInstanceAttachmentRequest | 保存实例级附件请求 |
| SaveProcessGraphRequest | 保存流程图请求 |
| SaveTaskAttachmentRequest | 保存任务级附件请求 |
| StartProcessRequest | 启动流程请求 |
| StoreFileRequest | 文件存储请求 |
| SubmitTaskRequest | 提交任务请求 |
| TerminateProcessRequest | 终止流程请求 |
| TimeoutScanRequest | 超时任务扫描请求 |
| TransferTaskRequest | 转办请求 |
| UnclaimTaskRequest | 取消认领请求 |
| UpdateVariablesRequest | 更新流程变量请求 |
| WithdrawTaskRequest | 撤回请求 |

### po说明

| po | 说明 |
| -------------- | ------------------ |
| FileContent | 文件内容 |
| ProcessMessage | 推送的消息内容 |
| StoredFile | 存储的文件 |
| UserContext | 当前用户上下文信息 |
| WorkflowEvent | 回调事件实体 |

### result说明

| result | 说明 |
| ----------------------------- | ---------------- |
| AttachmentTemplateCheckResult | 附件模板校验结果 |
| TaskActionResult | 任务动作结果 |
| ValidationResult | 流程定义校验结果 |

### Query说明

| Query | 说明 |
| --------------------- | ------------------------ |
| AdminHistoryTaskQuery | 管理端历史任务查询条件 |
| AdminInstanceQuery | 管理端流程查询 |
| AdminTaskQuery | 管理端查询条件 |
| AlertQuery | 告警查询条件 |
| AttachmentQuery | 附件查询条件 |
| AuditLogQuery | 审计日志查询 |
| CallbackLogQuery | 回调日志查询 |
| CompletedTaskQuery | 已办任务查询条件 |
| ProcessDefinitionQuery | 流程定义分页查询条件 |
| ReadRecordQuery | 已阅记录查询条件 |
| ReminderQuery | 提醒记录查询条件 |
| StartedInstanceQuery | 我发起的流程实例查询条件 |
| TodoTaskQuery | 待办任务查询条件 |

