# Business Base 任务提醒与告警设计

## 1. 目标

在 `business-base` 中建立完整的任务时限消息闭环：Platform 根据节点配置识别即将超时和已经超时的任务，通过 `MessagePublisher` SPI 发布提醒；Business Base 持久化每个用户的消息，并通过 SSE 实时推送到前端。流程发起人可以催办当前任务，管理员可以接收并处理异常告警。

用户侧采用“消息中心 + 轻提示 + 严重消息弹窗”组合：即将超时和人工催办使用消息中心与 Toast，任务超时和异常告警使用消息中心与弹窗。

## 2. 范围

本次包含：

- 节点级临期提醒配置，默认提前 30 分钟；
- Platform 临期和超时扫描、提醒去重及消息发布；
- Business Base 的 `MessagePublisher` SPI 实现、消息持久化和 SSE；
- 消息查询、未读计数、已读操作；
- 流程发起人对当前任务的人工催办；
- 管理员异常告警查询、推送和处理；
- 待办时限状态、消息中心、Toast、弹窗和告警页面；
- 自动化测试和本地集成验收。

本次不包含短信、邮件、企业微信等外部渠道，不引入消息队列、Redis 或 WebSocket，不改变任务办理和超时动作的既有业务语义。

## 3. 总体架构

```text
TimeoutScanScheduler
  -> ProcessMonitorService
     -> 临期扫描（dueAt - beforeDueMinutes）
     -> 超时扫描（dueAt）
     -> reminder/alert record
     -> MessagePublisher.publish(ProcessMessage)
        -> BusinessMessagePublisher
           -> business_user_message（每个接收人一条）
           -> 事务提交后通知 UserMessageSseHub

business-base frontend
  -> GET /api/messages（登录补拉/消息中心）
  -> GET /api/messages/unread-count
  -> GET /api/messages/events（SSE）
  -> Toast / 严重消息弹窗 / 消息中心
```

Platform 是任务时限、提醒和告警规则的事实来源。Business Base 负责接收人落库、当前登录用户的消息读取状态、实时连接和具体前端交互。SSE 只降低通知延迟，消息表是恢复和未读状态的事实来源。

## 4. 节点时限与提醒规则

节点继续使用 `timeoutConfig` 定义任务总时限和超时动作，使用 `reminderConfig` 定义提醒策略：

```json
{
  "timeoutConfig": {
    "enabled": true,
    "durationMinutes": 1440,
    "action": "REMIND",
    "severity": "MEDIUM"
  },
  "reminderConfig": {
    "enabled": true,
    "beforeDueMinutes": 30,
    "maxCount": 2,
    "messageTemplate": "您有待办，请及时处理。"
  }
}
```

规则如下：

- `beforeDueMinutes` 未配置时默认 `30`；必须为正整数。
- 如果提前量大于任务总时限，运行时按任务总时限截断，最早在任务创建后进入临期状态。
- 临期扫描产生 `DUE_SOON` 提醒；到期扫描产生 `TIMEOUT` 提醒，并继续执行节点现有超时动作。
- 临期和超时分别按“任务 ID + 提醒类型”去重；重复扫描不能重复生成同一阶段的提醒。
- 去重只阻止创建新的提醒记录；已有 `FAILED` 记录在后续扫描中复用原提醒 ID 重发，成功后更新为 `SENT`。
- `reminderConfig.enabled=false` 时不产生用户提醒，但配置的超时动作仍按既有规则执行。

超时扫描默认每 10 秒运行一次，保留现有批次上限。Business Base 的本地和正式配置都必须显式启用扫描；测试通过可控时钟直接验证边界，不等待真实分钟数。

## 5. SPI 消息契约

继续使用现有 `ProcessMessage`，不增加与 Business Base 绑定的 Platform 类型。稳定消息类型为：

- `TASK_DUE_SOON`：任务即将超时；
- `TASK_TIMEOUT`：任务已经超时；
- `TASK_REMIND`：流程发起人人工催办；
- `ALERT`：需要管理员处理的异常告警。

普通任务消息的 `targetUserIds` 由 Platform 根据办理人或候选人解析。`ALERT` 由 Business Base 的 Publisher 解析当前有效管理员并逐人持久化。消息 `payload` 只包含跳转与展示所需的安全字段：

```json
{
  "instanceId": "...",
  "taskId": "...",
  "processCode": "...",
  "instanceTitle": "...",
  "nodeCode": "...",
  "dueAt": "...",
  "severity": "MEDIUM",
  "alertId": "..."
}
```

不得在消息中放入表单变量、附件信息、Cookie、凭据或异常堆栈。

## 6. 消息持久化

Business Base 新增并拥有 `business_user_message` 表：

| 字段 | 含义 |
| --- | --- |
| `id` | 用户消息 ID |
| `source_message_id` | SPI 消息 ID |
| `recipient_user_id` | 接收用户 ID |
| `message_type` | 稳定消息类型 |
| `title` / `content` | 展示文本 |
| `payload_json` | 安全扩展数据 |
| `severity` | 消息级别 |
| `read_status` | `UNREAD` / `READ` |
| `created_at` | 创建时间 |
| `read_at` | 阅读时间 |

对 `(source_message_id, recipient_user_id)` 建唯一约束，防止 SPI 重试造成重复消息；对 `(recipient_user_id, read_status, created_at)` 建查询索引。

一个 `ProcessMessage` 有多个接收人时拆成多条用户消息，每个用户独立维护已读状态。SPI 重发同一消息 ID 时，已存在的用户消息视为持久化成功，不重置已读状态，也不重复推送。消息写入和业务提醒记录使用同一数据源；SSE 广播必须注册为事务提交后的动作，避免回滚事务产生不可恢复的幽灵消息。SSE 客户端失败不能让其他接收人的消息持久化失败。

## 7. Business Base API

所有接口使用当前 Business Base Session，不接受调用方传入用户 ID。

### 7.1 用户消息

- `GET /api/messages?pageNo=&pageSize=&readStatus=&messageType=`：查询当前用户消息；
- `GET /api/messages/unread-count`：查询当前用户未读数量；
- `POST /api/messages/{messageId}/read`：标记当前用户的一条消息已读；
- `POST /api/messages/read-all`：标记当前用户全部消息已读；
- `GET /api/messages/events`：建立当前用户 SSE 连接。

SSE 每 20 秒写入心跳。连接建立后只推送新消息；断线重连和页面启动通过消息查询接口补拉，服务端不依赖 SSE 连接保存未读状态。

### 7.2 人工催办

- `POST /api/workflow/tasks/{taskId}/remind`

请求包含催办内容、任务版本和幂等键。服务端必须校验：

- 当前用户是该流程实例的发起人；
- 实例仍为运行中；
- 任务仍为活动或已认领状态；
- 任务版本未变化；
- 同一发起人对同一任务五分钟内没有成功催办。

校验通过后调用 Platform `ProcessMonitorService.remindTask`，消息目标仍由 Platform 根据任务当前办理人或候选人解析。其他用户调用返回 403；重复或过期任务返回稳定的 409 错误。

### 7.3 管理员告警

- `GET /api/admin/alerts`：分页查询异常告警；
- `POST /api/admin/alerts/{alertId}/handle`：确认或关闭告警。

两项接口都必须通过 `BusinessAuthorizationProvider.isAdministrator` 校验。弹窗确认只表示用户看到了消息，不自动改变告警状态；告警状态必须通过处理接口显式变更。

## 8. 前端交互

### 8.1 消息中心

顶部用户区域增加消息铃铛和未读徽标。消息中心按创建时间倒序展示类型、标题、内容、时间和已读状态，并提供单条已读、全部已读及跳转操作。

点击任务消息进入任务或流程详情；点击告警消息进入管理员告警页面。消息已被其他页面处理或关联任务已结束时，详情页显示当前真实状态，不重放旧操作。

### 8.2 实时提示

- `TASK_DUE_SOON`、`TASK_REMIND`：右上角 Toast，并写入消息中心；
- `TASK_TIMEOUT`：警告弹窗，并写入消息中心；
- `ALERT`：仅管理员显示严重告警弹窗，并写入消息中心和告警列表。

只有当前在线期间由 SSE 新到达的严重消息逐条弹窗。登录时补拉的历史未读消息只显示一次汇总提示，避免连续弹窗。严重消息进入队列，一次只显示一条。

### 8.3 任务时限状态

后端任务 DTO 增加：

- `timingStatus`：`NORMAL`、`DUE_SOON`、`OVERDUE`；
- `remainingMinutes`：正数表示剩余分钟，负数表示已超时分钟；
- `dueAt`：既有到期时间。

状态和剩余时间由服务端统一计算。待办列表和任务详情据此显示标签及剩余/超时时长，前端不复制节点配置解析规则。

### 8.4 催办与告警操作

流程发起人在“我发起的”流程详情中，对仍在运行的当前任务看到催办按钮。操作前显示确认框，可填写催办内容。任务办理人和其他参与人不显示催办按钮，后端仍执行完整权限校验。

管理员导航增加异常告警入口，展示严重级别、关联流程、节点、创建时间和处理状态，并支持确认或关闭。

## 9. 错误与恢复

- 消息持久化失败：SPI 抛出异常，提醒记录保留失败状态；后续扫描复用原提醒和消息 ID 重发，用户消息唯一约束保证幂等；
- 单个 SSE 连接断开：清理连接，不影响消息落库和其他连接；
- SSE 重连：前端指数退避，并立即补拉未读消息；
- Session 失效：SSE 返回 401，复用现有全局认证失效处理；
- 催办并发冲突：刷新流程详情并提示任务状态已变化；
- 消息关联对象不存在：消息仍可标记已读，但跳转页显示对象已结束或不存在；
- 告警重复处理：返回 409 并刷新告警状态。

## 10. 测试

### 10.1 Platform

- `beforeDueMinutes` 默认值、自定义值、非法值和截断规则；
- 临期与超时边界扫描；
- 临期、超时分别去重；
- 失败提醒复用原 ID 重发，成功提醒不再发送；
- 超时动作保持原有行为；
- 四种消息类型的 SPI 契约；
- 告警创建后发布管理员告警消息。

### 10.2 Business Base 后端

- 多接收人拆分、唯一约束、事务提交后 SSE 广播；
- 当前用户消息隔离、分页、未读计数和已读操作；
- SSE 身份隔离、心跳、断开清理；
- 离线消息重新登录后可见；
- 仅流程发起人催办、五分钟防重复、任务版本冲突；
- 仅管理员接收、查询和处理告警；
- 任务时限状态映射。

### 10.3 前端

- 消息中心、未读徽标和已读操作；
- SSE 消息分类为 Toast 或弹窗；
- 历史未读只汇总、实时严重消息进入弹窗队列；
- SSE 断线重连和 401；
- 待办临期/超时标签；
- 发起人催办按钮和管理员告警入口的可见性；
- 催办和告警处理失败后的状态刷新。

## 11. 本地验收

1. 创建短时限任务，验证待办显示即将超时标签并收到 Toast。
2. 让办理人离线，验证重新登录后消息仍为未读。
3. 任务到期后验证超时标签、消息中心和警告弹窗。
4. 流程发起人催办，验证当前办理人实时收到消息。
5. 非发起人调用催办接口，验证返回 403。
6. 制造异常告警，验证只有管理员收到弹窗并能确认或关闭。
7. 重启 Business Base，验证未读消息和已读状态不丢失。

## 12. 上线顺序

1. 部署消息表迁移和 Business Base 消息后端；
2. 部署 SSE、消息接口和前端消息中心；
3. 部署 Platform 临期扫描扩展和节点配置支持；
4. 显式启用超时扫描；
5. 观察提醒失败记录、SSE 连接数和消息积压后再扩大使用范围。

该顺序确保扫描开始产生消息之前，持久化消费者和用户界面已经可用。
