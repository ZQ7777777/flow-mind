# Business Base Task Reminder And Alert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver persisted personal task reminders, real-time SSE notifications, starter-only manual urging, overdue popups, and administrator exception-alert handling in Business Base.

**Architecture:** Platform remains the source of task deadlines, reminder deduplication, timeout scanning, and standard `ProcessMessage` events. Business Base supplies the host `MessagePublisher`, persists one inbox row per recipient, exposes authenticated inbox/SSE/remind/admin APIs, and maps deadline state into workflow DTOs; Vue consumes only those Business Base contracts.

**Tech Stack:** Java 8, Spring Boot 2.7.18, Spring JDBC, SQLite, Spring MVC `SseEmitter`, JUnit 5, Mockito, MockMvc, Vue 3.5, Pinia 3, Vue Router 4, Element Plus 2.11, Vitest 3, TypeScript 5.9.

## Global Constraints

- Preserve all unrelated dirty-worktree changes; inspect the current file before every edit and stage only files named by the active task.
- Node `reminderConfig.beforeDueMinutes` is a positive integer, defaults to `30`, and is clamped to `timeoutConfig.durationMinutes` when the configured lead time is longer than the task duration.
- Publish only the stable message types `TASK_DUE_SOON`, `TASK_TIMEOUT`, `TASK_REMIND`, and `ALERT`.
- Persist one `business_user_message` row per recipient and enforce uniqueness on `(source_message_id, recipient_user_id)`.
- A repeated SPI delivery with the same message ID is successful, never resets read state, and never emits a second SSE event.
- `TASK_DUE_SOON` and `TASK_REMIND` use toast notifications; `TASK_TIMEOUT` and `ALERT` use a serialized modal queue. Historical unread messages never open a modal during page bootstrap.
- Only the process starter may manually remind an active task. The endpoint requires `expectedTaskVersion` and `Idempotency-Key`, and applies a five-minute task cooldown.
- Alerts with no explicit recipients expand to all active administrators; alert query and handle APIs are administrator-only.
- SSE sends a heartbeat every 20 seconds and reconnects with bounded backoff. The inbox API remains the recovery source after disconnects or offline periods.
- The runtime timeout scanner keeps the approved 10-second fixed delay and the existing batch limit behavior.
- Platform reminder records and `business_user_message` use the same configured `DataSource` so persistence participates in the caller transaction.
- Do not add Redis, MQ, WebSocket, SMS, email, or external notification providers.
- Use server time for `timingStatus` and `remainingMinutes`; the frontend must not independently classify deadlines.
- Enable the existing timeout scan scheduler in Business Base only after the custom `MessagePublisher` bean and persistence path are verified.
- Release in this order: Business message schema/publisher/API, SSE and frontend, Platform scan extension, then the configuration that enables scanning.

---

### Task 1: Platform Reminder Policy Contract

**Files:**
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/api/enums/ReminderTypeEnum.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/ReminderPolicy.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/ReminderPolicyReader.java`
- Test: `platform/platform-core/src/test/java/com/flowmind/platform/core/monitor/ReminderPolicyReaderTest.java`

**Interfaces:**
- Produces: `ReminderTypeEnum.DUE_SOON`.
- Produces: `ReminderPolicy#getBeforeDueMinutes(): Integer`, defaulting to `30`.
- Produces: `ReminderPolicyReader#read(ProcessNodeEntity)` validation for positive `beforeDueMinutes`.

- [ ] **Step 1: Write the failing policy tests**

```java
class ReminderPolicyReaderTest {
    private final ReminderPolicyReader reader = new ReminderPolicyReader();

    @Test
    void defaultsBeforeDueMinutesToThirty() {
        ProcessNodeEntity node = new ProcessNodeEntity();
        node.setReminderConfig("{\"enabled\":true}");
        assertEquals(Integer.valueOf(30), reader.read(node).getBeforeDueMinutes());
    }

    @Test
    void readsPositiveBeforeDueMinutes() {
        ProcessNodeEntity node = new ProcessNodeEntity();
        node.setReminderConfig("{\"enabled\":true,\"beforeDueMinutes\":45}");
        assertEquals(Integer.valueOf(45), reader.read(node).getBeforeDueMinutes());
    }

    @Test
    void rejectsNonPositiveBeforeDueMinutes() {
        ProcessNodeEntity node = new ProcessNodeEntity();
        node.setReminderConfig("{\"enabled\":true,\"beforeDueMinutes\":0}");
        RuntimeValidationException error = assertThrows(RuntimeValidationException.class,
                () -> reader.read(node));
        assertEquals(RuntimeErrorCodes.NODE_CONFIG_INVALID, error.getErrorCode());
    }

    @Test
    void rejectsFractionalBeforeDueMinutes() {
        ProcessNodeEntity node = new ProcessNodeEntity();
        node.setReminderConfig("{\"enabled\":true,\"beforeDueMinutes\":1.5}");
        assertThrows(RuntimeValidationException.class, () -> reader.read(node));
    }
}
```

- [ ] **Step 2: Run the test and verify the contract is absent**

Run: `mvn -f platform/platform-core/pom.xml -Dtest=ReminderPolicyReaderTest test`

Expected: compilation fails because `getBeforeDueMinutes()` does not exist.

- [ ] **Step 3: Add the enum value and policy field**

```java
public enum ReminderTypeEnum {
    MANUAL,
    AUTO,
    DUE_SOON,
    TIMEOUT
}
```

```java
private Integer beforeDueMinutes = Integer.valueOf(30);

public Integer getBeforeDueMinutes() { return beforeDueMinutes; }
public void setBeforeDueMinutes(Integer beforeDueMinutes) { this.beforeDueMinutes = beforeDueMinutes; }
```

Add this block to `ReminderPolicyReader#read` after `maxCount` parsing:

```java
Object beforeDueMinutes = config.get("beforeDueMinutes");
if (beforeDueMinutes != null) {
    int value = positiveInteger(beforeDueMinutes, "beforeDueMinutes");
    if (value <= 0) {
        throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                "reminder beforeDueMinutes must be positive");
    }
    policy.setBeforeDueMinutes(Integer.valueOf(value));
}

private int positiveInteger(Object value, String fieldName) {
    if (value instanceof Number) {
        double decimal = ((Number) value).doubleValue();
        int integer = ((Number) value).intValue();
        if (decimal != integer) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    fieldName + " must be an integer");
        }
        return integer;
    }
    try {
        return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException error) {
        throw new RuntimeValidationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                fieldName + " must be an integer");
    }
}
```

- [ ] **Step 4: Run focused and existing monitor tests**

Run: `mvn -f platform/platform-core/pom.xml -Dtest=ReminderPolicyReaderTest,DefaultProcessMonitorServiceTest test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit the policy contract**

```bash
git add platform/platform-core/src/main/java/com/flowmind/platform/api/enums/ReminderTypeEnum.java platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/ReminderPolicy.java platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/ReminderPolicyReader.java platform/platform-core/src/test/java/com/flowmind/platform/core/monitor/ReminderPolicyReaderTest.java
git commit -m "feat(platform): add due-soon reminder policy"
```

### Task 2: Platform Due-Soon Scan And Stable SPI Messages

**Files:**
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ActiveTaskRepository.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ReminderRecordRepository.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/DefaultProcessMonitorService.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/ActionExceptionAlertWriter.java`
- Modify: `platform/platform-starter/src/main/java/com/flowmind/platform/starter/PlatformAutoConfiguration.java`
- Test: `platform/platform-core/src/test/java/com/flowmind/platform/persistence/repository/M5MonitorRepositoryIntegrationTest.java`
- Test: `platform/platform-core/src/test/java/com/flowmind/platform/core/monitor/DefaultProcessMonitorServiceTest.java`
- Create test: `platform/platform-core/src/test/java/com/flowmind/platform/core/monitor/ActionExceptionAlertWriterTest.java`
- Modify test: `platform/platform-starter/src/test/java/com/flowmind/platform/starter/PlatformAutoConfigurationTest.java`

**Interfaces:**
- Produces: `ActiveTaskRepository#findDueSoonOpenTasks(LocalDateTime scanAt, int limit)`.
- Produces: retry semantics that reuse the existing failed reminder ID.
- Publishes recipient task messages with the safe payload keys `taskId`, `instanceId`, `processCode`, `instanceTitle`, `nodeCode`, `dueAt`, and `severity`.
- Publishes administrator `ALERT` messages with an empty target list so Business Base can expand recipients.

- [ ] **Step 1: Add failing repository and service tests**

Add an integration test that seeds three nodes and tasks: default 30 minutes, custom 45 minutes, and a 90-minute reminder clamped to a 60-minute timeout. At `10:00`, assert tasks due at `10:30`, `10:45`, and `11:00` are returned, while a task due at `11:01` is not.

```java
List<ProcessActiveTaskEntity> dueSoon = activeTaskRepository.findDueSoonOpenTasks(
        LocalDateTime.of(2026, 8, 11, 10, 0), 20);
assertEquals(Arrays.asList("task-default", "task-custom", "task-clamped"),
        dueSoon.stream().map(ProcessActiveTaskEntity::getId).collect(Collectors.toList()));
```

Add service assertions using an `ArgumentCaptor<ProcessMessage>`:

```java
assertEquals("TASK_DUE_SOON", message.getMessageType());
assertEquals("task-due-soon", message.getPayload().get("taskId"));
assertEquals("LOW", message.getPayload().get("severity"));
assertEquals(ReminderTypeEnum.DUE_SOON, reminder.getReminderType());
```

Add retry coverage:

```java
when(reminderRepository.findLatestByTaskAndType("task-due-soon", "DUE_SOON"))
        .thenReturn(failedReminder("reminder-stable"));
service.scanTimeoutTasks(scanRequest(scanAt));
verify(messagePublisher).publish(argThat(message ->
        "reminder-stable".equals(message.getMessageId())));
verify(reminderRepository, never()).insert(any(ProcessReminderRecordEntity.class));
```

Add timeout and alert assertions:

```java
assertEquals("TASK_TIMEOUT", timeoutMessage.getMessageType());
assertEquals("HIGH", timeoutMessage.getPayload().get("severity"));
assertEquals("ALERT", alertMessage.getMessageType());
assertTrue(alertMessage.getTargetUserIds().isEmpty());
```

Add `ActionExceptionAlertWriterTest` to verify an inserted exception alert publishes one sanitized admin event without `errorSummary`, `operationId`, or a stack trace:

```java
writer.write("operation-1", "TRANSFER", "instance-1", "task-1",
        "FLOW_DEPENDENCY_FAILED", "internal database detail", "user-1");
verify(messagePublisher).publish(argThat(message -> "ALERT".equals(message.getMessageType())
        && message.getTargetUserIds().isEmpty()
        && "task-1".equals(message.getPayload().get("taskId"))
        && !message.getPayload().containsKey("errorSummary")
        && !message.getPayload().containsKey("operationId")));
```

- [ ] **Step 2: Run tests and observe missing query/message behavior**

Run: `mvn -f platform/platform-core/pom.xml -Dtest=M5MonitorRepositoryIntegrationTest,DefaultProcessMonitorServiceTest test`

Expected: compilation fails on `findDueSoonOpenTasks`; after adding only the signature, assertions fail because the scanner publishes only generic `REMIND` messages.

- [ ] **Step 3: Implement the due-soon query and failed-record lookup**

Use SQLite JSON functions to calculate the effective trigger in the database, so large future task sets cannot hide eligible reminders behind the scan limit:

```java
public List<ProcessActiveTaskEntity> findDueSoonOpenTasks(LocalDateTime scanAt, int limit) {
    String effectiveMinutes = effectiveBeforeDueMinutesSql("n");
    String sql = "SELECT t.* FROM process_active_task t "
            + "JOIN process_node n ON n.definition_id = t.definition_id AND n.node_code = t.node_code "
            + "WHERE t.due_at IS NOT NULL AND t.due_at > ? "
            + "AND t.task_status IN ('ACTIVE', 'CLAIMED') "
            + "AND COALESCE(json_extract(n.reminder_config, '$.enabled'), 0) IN (1, 'true') "
            + "AND datetime(t.due_at, printf('-%d minutes', " + effectiveMinutes + ")) <= datetime(?) "
            + "ORDER BY t.due_at ASC, t.created_at ASC, t.id ASC LIMIT ?";
    String value = DefinitionRowMappers.toDbString(scanAt);
    return jdbcTemplate.query(sql, TASK_ROW_MAPPER, value, value, limit);
}

private String effectiveBeforeDueMinutesSql(String nodeAlias) {
    return "CASE WHEN COALESCE(json_extract(" + nodeAlias
            + ".timeout_config, '$.durationMinutes'), 0) > 0 THEN MIN(COALESCE(json_extract("
            + nodeAlias + ".reminder_config, '$.beforeDueMinutes'), 30), json_extract("
            + nodeAlias + ".timeout_config, '$.durationMinutes')) ELSE COALESCE(json_extract("
            + nodeAlias + ".reminder_config, '$.beforeDueMinutes'), 30) END";
}
```

Add to `ReminderRecordRepository`:

```java
public ProcessReminderRecordEntity findLatestByTaskAndType(String taskId, String reminderType) {
    List<ProcessReminderRecordEntity> rows = jdbcTemplate.query(
            "SELECT * FROM process_reminder_record WHERE task_id = ? AND reminder_type = ? "
                    + "ORDER BY created_at DESC, id DESC LIMIT 1",
            ROW_MAPPER, taskId, reminderType);
    return rows.isEmpty() ? null : rows.get(0);
}
```

- [ ] **Step 4: Implement scan ordering and standard message construction**

Inside `scanTimeoutTasks`, query and process due-soon tasks before timeout tasks. Do not return due-soon tasks from the existing method; preserve its public return contract as the list of overdue tasks.

```java
if (!Boolean.TRUE.equals(request.getDryRun())) {
    for (ProcessActiveTaskEntity task : activeTaskRepository.findDueSoonOpenTasks(scanAt, limit)) {
        ReminderPolicy policy = reminderPolicy(task);
        createAutomaticReminder(task, policy, ReminderTypeEnum.DUE_SOON);
    }
}
List<ProcessActiveTaskEntity> timeoutTasks = activeTaskRepository.findTimeoutOpenTasks(scanAt, limit);
```

Implement one retry-aware helper for both automatic stages:

```java
private void createAutomaticReminder(ProcessActiveTaskEntity task,
                                     ReminderPolicy policy,
                                     ReminderTypeEnum type) {
    if (!policy.isEnabled()) return;
    ProcessReminderRecordEntity existing = reminderRepository.findLatestByTaskAndType(
            task.getId(), type.name());
    if (existing != null) {
        if (ReminderStatusEnum.FAILED.name().equals(existing.getReminderStatus())) {
            publishAndUpdate(existing, task);
        }
        return;
    }
    if (!reminderDeduplicationGuard.canCreate(task.getId(), type, policy.getMaxCount())) return;
    ProcessReminderRecordEntity reminder = new ProcessReminderRecordEntity();
    reminder.setId(UUID.randomUUID().toString());
    reminder.setInstanceId(task.getInstanceId());
    reminder.setTaskId(task.getId());
    reminder.setReminderType(type.name());
    reminder.setTargetUserIds(RuntimeJsonCodec.toJson(resolveTargets(task)));
    reminder.setMessage(automaticMessage(policy, type));
    reminder.setReminderStatus(ReminderStatusEnum.PENDING.name());
    reminder.setCreatedBy("system_timeout");
    reminder.setCreatedAt(LocalDateTime.now());
    if (reminderRepository.insert(reminder) == 1) publishAndUpdate(reminder, task);
}

private String automaticMessage(ReminderPolicy policy, ReminderTypeEnum type) {
    if (policy.getMessageTemplate() != null && !policy.getMessageTemplate().trim().isEmpty()) {
        return policy.getMessageTemplate().trim();
    }
    return ReminderTypeEnum.TIMEOUT.equals(type)
            ? "任务已超时，请尽快处理" : "任务即将超时，请及时处理";
}
```

`createTimeoutReminder` delegates to this helper with `ReminderTypeEnum.TIMEOUT`. Timeout action execution remains after reminder handling and is never conditioned on `reminderConfig.enabled`.

Centralize message creation:

```java
private ProcessMessage reminderMessage(ProcessReminderRecordEntity reminder,
                                       ProcessActiveTaskEntity task,
                                       ProcessInstanceEntity instance) {
    String messageType = ReminderTypeEnum.DUE_SOON.name().equals(reminder.getReminderType())
            ? "TASK_DUE_SOON"
            : ReminderTypeEnum.TIMEOUT.name().equals(reminder.getReminderType())
                    ? "TASK_TIMEOUT" : "TASK_REMIND";
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("taskId", task.getId());
    payload.put("instanceId", task.getInstanceId());
    payload.put("processCode", instance.getProcessCode());
    payload.put("instanceTitle", instance.getInstanceTitle());
    payload.put("nodeCode", task.getNodeCode());
    payload.put("dueAt", task.getDueAt());
    payload.put("severity", "TASK_TIMEOUT".equals(messageType) ? "HIGH" : "LOW");
    return new ProcessMessage(reminder.getId(), messageType,
            "TASK_TIMEOUT".equals(messageType) ? "任务已超时" :
                    "TASK_DUE_SOON".equals(messageType) ? "任务即将超时" : "任务催办",
            reminder.getMessage(), targetUserIds(reminder), payload, reminder.getCreatedAt());
}
```

For a failed prior record, call `publishAndUpdate(existing, task)` without inserting. After creating a timeout alert, publish this event:

```java
Map<String, Object> payload = new LinkedHashMap<String, Object>();
payload.put("alertId", alert.getId());
payload.put("taskId", alert.getTaskId());
payload.put("instanceId", alert.getInstanceId());
payload.put("processCode", instance.getProcessCode());
payload.put("instanceTitle", instance.getInstanceTitle());
payload.put("alertType", alert.getAlertType());
payload.put("severity", alert.getSeverity());
messagePublisher.publish(new ProcessMessage(alert.getId(), "ALERT", "流程异常告警",
        "任务超时且已触发异常处理", Collections.<String>emptyList(), payload, alert.getCreatedAt()));
```

Publish action-exception alerts from `ActionExceptionAlertWriter` only after `alertRepository.insert(alert) == 1`:

```java
if (alertRepository.insert(alert) == 1 && messagePublisher != null) {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("alertId", alert.getId());
    payload.put("instanceId", instanceId);
    payload.put("taskId", taskId);
    payload.put("alertType", AlertTypeEnum.ACTION_EXCEPTION.name());
    payload.put("severity", AlertSeverityEnum.HIGH.name());
    messagePublisher.publish(new ProcessMessage(alert.getId(), "ALERT", "流程异常告警",
            "流程动作执行异常，请管理员处理", Collections.<String>emptyList(), payload,
            alert.getCreatedAt()));
}
```

Wire the same host `MessagePublisher` into `ActionExceptionAlertWriter` from `PlatformAutoConfiguration`; retain its repository-only constructor for direct unit-test compatibility and have it delegate with a null publisher.

- [ ] **Step 5: Verify platform behavior and commit**

Run: `mvn -pl platform/platform-starter -am -Dtest=M5MonitorRepositoryIntegrationTest,DefaultProcessMonitorServiceTest,ActionExceptionAlertWriterTest,RecordingMessagePublisherTest,PlatformAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all selected tests pass; captured message types are stable and a failed record is retried with its original ID.

```bash
git add platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ActiveTaskRepository.java platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ReminderRecordRepository.java platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/DefaultProcessMonitorService.java platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/ActionExceptionAlertWriter.java platform/platform-starter/src/main/java/com/flowmind/platform/starter/PlatformAutoConfiguration.java platform/platform-core/src/test/java/com/flowmind/platform/persistence/repository/M5MonitorRepositoryIntegrationTest.java platform/platform-core/src/test/java/com/flowmind/platform/core/monitor/DefaultProcessMonitorServiceTest.java platform/platform-core/src/test/java/com/flowmind/platform/core/monitor/ActionExceptionAlertWriterTest.java platform/platform-starter/src/test/java/com/flowmind/platform/starter/PlatformAutoConfigurationTest.java
git commit -m "feat(platform): publish due and timeout notifications"
```

### Task 3: Business Message Persistence And SPI Publisher

**Files:**
- Create: `business-base/backend/src/main/resources/business-message/001_user_message.sql`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageSchemaInitializer.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessUserMessage.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageMapper.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageRepository.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageEventSink.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessagePublisher.java`
- Test: `business-base/backend/src/test/java/com/flowmind/business/message/BusinessMessagePublisherTest.java`
- Test: `business-base/backend/src/test/java/com/flowmind/business/message/BusinessMessageRepositoryTest.java`

**Interfaces:**
- Produces: `BusinessMessageRepository#insertIfAbsent(BusinessUserMessage): boolean`.
- Produces: a primary `MessagePublisher` Spring bean that overrides the starter fallback.
- Produces: `BusinessMessageEventSink#publishAfterCommit(BusinessUserMessage)`; Task 4 supplies its live implementation.
- Consumes: host `OrganizationProvider#listUsersByRole("admin")`, which returns active administrator users in the current Business Base provider.

- [ ] **Step 1: Write failing schema, repository, and publisher tests**

```java
@Test
void duplicateSourceAndRecipientDoesNotResetReadState() {
    assertTrue(repository.insertIfAbsent(message("source-1", "user-1")));
    repository.markRead("message-1", "user-1", LocalDateTime.of(2026, 8, 11, 10, 1));
    assertFalse(repository.insertIfAbsent(message("source-1", "user-1")));
    assertEquals("READ", repository.findOwned("message-1", "user-1").getReadStatus());
}
```

```java
@Test
void alertWithoutTargetsExpandsToAllActiveAdministrators() {
    publisher.publish(processMessage("alert-1", "ALERT", Collections.<String>emptyList()));
    assertEquals(Arrays.asList("admin01", "admin02"), repository.recipientIds("alert-1"));
}

@Test
void duplicateDeliveryDoesNotPublishSecondLiveEvent() {
    ProcessMessage source = processMessage("reminder-1", "TASK_REMIND",
            Collections.singletonList("user-1"));
    publisher.publish(source);
    publisher.publish(source);
    verify(eventSink, times(1)).publishAfterCommit(any(BusinessUserMessage.class));
}

@Test
void stripsUnsafePayloadFieldsBeforePersistence() {
    ProcessMessage source = processMessage("reminder-2", "TASK_REMIND",
            Collections.singletonList("user-1"));
    source.getPayload().put("variables", Collections.singletonMap("bankAccount", "secret"));
    publisher.publish(source);
    assertFalse(repository.findBySourceAndRecipient("reminder-2", "user-1")
            .getPayloadJson().contains("bankAccount"));
}
```

- [ ] **Step 2: Run tests and verify missing persistence types**

Run: `mvn -pl business-base/backend -am -Dtest=BusinessMessageRepositoryTest,BusinessMessagePublisherTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because the message classes do not exist.

- [ ] **Step 3: Create the idempotent message schema and model**

```sql
CREATE TABLE IF NOT EXISTS business_user_message (
    id VARCHAR(64) PRIMARY KEY,
    source_message_id VARCHAR(64) NOT NULL,
    recipient_user_id VARCHAR(64) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    payload_json TEXT NOT NULL DEFAULT '{}',
    severity VARCHAR(16) NOT NULL,
    read_status VARCHAR(16) NOT NULL DEFAULT 'UNREAD',
    created_at DATETIME NOT NULL,
    read_at DATETIME,
    CONSTRAINT uk_business_message_recipient UNIQUE (source_message_id, recipient_user_id)
);
CREATE INDEX IF NOT EXISTS idx_business_message_inbox
    ON business_user_message(recipient_user_id, read_status, created_at DESC);
```

`BusinessUserMessage` must expose all columns with Java 8 getters/setters. `BusinessMessageMapper#from(ProcessMessage, String)` generates deterministic row IDs so replay produces the same primary key:

```java
private String rowId(String sourceMessageId, String recipientUserId) {
    return UUID.nameUUIDFromBytes((sourceMessageId + "|" + recipientUserId)
            .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
}
```

Load the SQL with `ResourceDatabasePopulator` in an `InitializingBean` implementation:

```java
@Override
public void afterPropertiesSet() {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("business-message/001_user_message.sql"));
    DatabasePopulatorUtils.execute(populator, dataSource);
}
```

- [ ] **Step 4: Implement insert-once publishing and administrator expansion**

Add the repository insert:

```java
public boolean insertIfAbsent(BusinessUserMessage message) {
    return jdbcTemplate.update("INSERT OR IGNORE INTO business_user_message "
                    + "(id, source_message_id, recipient_user_id, message_type, title, content, "
                    + "payload_json, severity, read_status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UNREAD', ?)",
            message.getId(), message.getSourceMessageId(), message.getRecipientUserId(),
            message.getMessageType(), message.getTitle(), message.getContent(),
            message.getPayloadJson(), message.getSeverity(), toDb(message.getCreatedAt())) == 1;
}
```

Define the live-delivery port without depending on the SSE implementation from Task 4:

```java
public interface BusinessMessageEventSink {
    void publishAfterCommit(BusinessUserMessage message);
}
```

Implement the SPI bean:

```java
@Component
@Primary
public class BusinessMessagePublisher implements MessagePublisher {
    @Override
    public void publish(ProcessMessage source) {
        List<String> recipients = new ArrayList<String>(source.getTargetUserIds() == null
                ? Collections.<String>emptyList() : source.getTargetUserIds());
        if ("ALERT".equals(source.getMessageType()) && recipients.isEmpty()) {
            for (UserDTO administrator : organizationProvider.listUsersByRole("admin")) {
                if (Boolean.TRUE.equals(administrator.getActive())) recipients.add(administrator.getUserId());
            }
        }
        for (String recipient : new LinkedHashSet<String>(recipients)) {
            BusinessUserMessage message = mapper.from(source, recipient);
            if (repository.insertIfAbsent(message)) {
                eventSinkProvider.ifAvailable(sink -> sink.publishAfterCommit(message));
            }
        }
    }
}
```

`BusinessMessageMapper#from` serializes `payload` with the existing Jackson `ObjectMapper`, reads `severity` from the payload, and defaults to `MEDIUM` for `ALERT`, `HIGH` for `TASK_TIMEOUT`, and `LOW` otherwise:

```java
public BusinessUserMessage from(ProcessMessage source, String recipientUserId) {
    BusinessUserMessage target = new BusinessUserMessage();
    target.setId(rowId(source.getMessageId(), recipientUserId));
    target.setSourceMessageId(source.getMessageId());
    target.setRecipientUserId(recipientUserId);
    target.setMessageType(source.getMessageType());
    target.setTitle(source.getTitle());
    target.setContent(source.getContent());
    target.setPayloadJson(writeJson(safePayload(source.getPayload())));
    target.setSeverity(severity(source));
    target.setReadStatus("UNREAD");
    target.setCreatedAt(source.getCreatedAt());
    return target;
}

private Map<String, Object> safePayload(Map<String, Object> source) {
    Map<String, Object> target = new LinkedHashMap<String, Object>();
    if (source == null) return target;
    for (String key : Arrays.asList("instanceId", "taskId", "processCode", "instanceTitle",
            "nodeCode", "dueAt", "severity", "alertId", "alertType")) {
        if (source.containsKey(key)) target.put(key, source.get(key));
    }
    return target;
}
```

- [ ] **Step 5: Verify persistence and commit**

Run: `mvn -pl business-base/backend -am -Dtest=BusinessMessageRepositoryTest,BusinessMessagePublisherTest,MockOrganizationSchemaInitializerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all selected tests pass; the duplicate test stores one row and emits one live event.

```bash
git add business-base/backend/src/main/resources/business-message/001_user_message.sql business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageSchemaInitializer.java business-base/backend/src/main/java/com/flowmind/business/message/BusinessUserMessage.java business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageMapper.java business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageRepository.java business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageEventSink.java business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessagePublisher.java business-base/backend/src/test/java/com/flowmind/business/message/BusinessMessagePublisherTest.java business-base/backend/src/test/java/com/flowmind/business/message/BusinessMessageRepositoryTest.java
git commit -m "feat(business): persist platform notifications"
```

### Task 4: Authenticated Inbox And SSE APIs

**Files:**
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageQuery.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageResponse.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessagePageResponse.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageService.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/MessageEventHub.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageController.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/common/BusinessResourceNotFoundException.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/config/BusinessTimeConfiguration.java`
- Modify: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageMapper.java`
- Modify: `business-base/backend/src/main/java/com/flowmind/business/message/BusinessMessageRepository.java`
- Test: `business-base/backend/src/test/java/com/flowmind/business/message/BusinessMessageControllerTest.java`
- Test: `business-base/backend/src/test/java/com/flowmind/business/message/MessageEventHubTest.java`

**Interfaces:**
- Produces: `GET /api/messages`, `GET /api/messages/unread-count`, `POST /api/messages/{messageId}/read`, `POST /api/messages/read-all`, and `GET /api/messages/events`.
- Produces SSE event names `message` and `heartbeat`; event data is `BusinessMessageResponse` for `message`.
- All repository mutations require both message ID and trusted recipient user ID.
- Extends `BusinessMessageMapper` with `toResponse(BusinessUserMessage)` and `toPage(List<BusinessUserMessage>, long, BusinessMessageQuery)`.
- Produces a `Clock.systemDefaultZone()` bean guarded by `@ConditionalOnMissingBean`, allowing deterministic fixed clocks in tests.

- [ ] **Step 1: Write failing ownership and SSE tests**

```java
mockMvc.perform(post("/api/messages/message-1/read").session(userSession("user-2")))
        .andExpect(status().isNotFound());
mockMvc.perform(post("/api/messages/message-1/read").session(userSession("user-1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readStatus").value("READ"));
```

```java
mockMvc.perform(get("/api/messages").session(userSession("user-1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records[0].recipientUserId").doesNotExist())
        .andExpect(jsonPath("$.records[0].payload.taskId").value("task-1"));
```

```java
SseEmitter first = hub.connect("user-1");
SseEmitter second = hub.connect("user-2");
hub.publish(messageFor("user-1"));
assertEquals(1, capturedEvents(first, "message").size());
assertEquals(0, capturedEvents(second, "message").size());
hub.heartbeat();
assertEquals(1, capturedEvents(first, "heartbeat").size());
```

- [ ] **Step 2: Run tests and verify the routes are missing**

Run: `mvn -pl business-base/backend -am -Dtest=BusinessMessageControllerTest,MessageEventHubTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because the controller and hub do not exist.

- [ ] **Step 3: Implement trusted-user inbox operations**

Use this service surface:

```java
public BusinessMessagePageResponse query(BusinessMessageQuery query) {
    return mapper.page(repository.query(currentUser().getUserId(), query),
            repository.count(currentUser().getUserId(), query), query);
}

public long unreadCount() {
    return repository.countUnread(currentUser().getUserId());
}

public BusinessMessageResponse markRead(String messageId) {
    String userId = currentUser().getUserId();
    if (repository.markRead(messageId, userId, LocalDateTime.now(clock)) != 1) {
        throw new BusinessResourceNotFoundException("消息不存在");
    }
    return mapper.response(repository.findOwned(messageId, userId));
}

public int markAllRead() {
    return repository.markAllRead(currentUser().getUserId(), LocalDateTime.now(clock));
}
```

Add `BusinessResourceNotFoundException` under `business/common` and map it to HTTP 404 with code `BUSINESS_RESOURCE_NOT_FOUND` in `BusinessExceptionHandler`:

```java
public final class BusinessResourceNotFoundException extends RuntimeException {
    public BusinessResourceNotFoundException(String message) { super(message); }
}
```

```java
@ExceptionHandler(BusinessResourceNotFoundException.class)
public ResponseEntity<ApiErrorResponse> handleNotFound(BusinessResourceNotFoundException exception,
                                                        HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "BUSINESS_RESOURCE_NOT_FOUND", exception.getMessage(), request);
}
```

`BusinessMessageMapper#toResponse` parses `payloadJson` into a `Map<String, Object>`, maps the row ID to response `messageId`, and intentionally omits `recipientUserId`:

```java
public BusinessMessageResponse toResponse(BusinessUserMessage source) {
    BusinessMessageResponse target = new BusinessMessageResponse();
    target.setMessageId(source.getId());
    target.setMessageType(source.getMessageType());
    target.setTitle(source.getTitle());
    target.setContent(source.getContent());
    target.setPayload(readPayload(source.getPayloadJson()));
    target.setSeverity(source.getSeverity());
    target.setReadStatus(source.getReadStatus());
    target.setCreatedAt(source.getCreatedAt());
    target.setReadAt(source.getReadAt());
    return target;
}
```

Provide the shared Business clock used by this service and Task 6:

```java
@Configuration
public class BusinessTimeConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock businessClock() {
        return Clock.systemDefaultZone();
    }
}
```

Controller signatures:

```java
@GetMapping
public BusinessMessagePageResponse query(@Valid @ModelAttribute BusinessMessageQuery query)
@GetMapping("/unread-count")
public Map<String, Long> unreadCount()
@PostMapping("/{messageId}/read")
public BusinessMessageResponse markRead(@PathVariable String messageId)
@PostMapping("/read-all")
public Map<String, Integer> markAllRead()
@GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter events()
```

- [ ] **Step 4: Implement the per-user SSE hub with after-commit delivery**

```java
@Component
public class MessageEventHub implements BusinessMessageEventSink, InitializingBean, DisposableBean {
private final ConcurrentMap<String, Set<SseEmitter>> emitters =
        new ConcurrentHashMap<String, Set<SseEmitter>>();
private final BusinessMessageMapper mapper;
private ScheduledExecutorService heartbeatExecutor;

public MessageEventHub(BusinessMessageMapper mapper) {
    this.mapper = mapper;
}

@Override
public void afterPropertiesSet() {
    heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "business-message-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    heartbeatExecutor.scheduleWithFixedDelay(this::heartbeat, 20L, 20L, TimeUnit.SECONDS);
}

public SseEmitter connect(String userId) {
    SseEmitter emitter = new SseEmitter(0L);
    emitters.computeIfAbsent(userId, key -> new CopyOnWriteArraySet<SseEmitter>()).add(emitter);
    Runnable cleanup = () -> remove(userId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(error -> cleanup.run());
    return emitter;
}

public void publishAfterCommit(BusinessUserMessage message) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override public void afterCommit() { publish(message); }
        });
    } else {
        publish(message);
    }
}

private void publish(BusinessUserMessage message) {
    SseEmitter.SseEventBuilder event = SseEmitter.event()
            .id(message.getId())
            .name("message")
            .data(mapper.toResponse(message));
    sendTo(message.getRecipientUserId(), event);
}

private void sendTo(String userId, SseEmitter.SseEventBuilder event) {
    Set<SseEmitter> userEmitters = emitters.get(userId);
    if (userEmitters == null) return;
    for (SseEmitter emitter : userEmitters) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException error) {
            emitter.complete();
            remove(userId, emitter);
        }
    }
}

private void remove(String userId, SseEmitter emitter) {
    Set<SseEmitter> userEmitters = emitters.get(userId);
    if (userEmitters == null) return;
    userEmitters.remove(emitter);
    if (userEmitters.isEmpty()) emitters.remove(userId, userEmitters);
}

public void heartbeat() {
    for (String userId : new ArrayList<String>(emitters.keySet())) {
        sendTo(userId, SseEmitter.event().name("heartbeat").data("ok"));
    }
}

@Override
public void destroy() {
    if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
    for (Set<SseEmitter> userEmitters : emitters.values()) {
        for (SseEmitter emitter : userEmitters) emitter.complete();
    }
    emitters.clear();
}
}
```

On `IOException` or `IllegalStateException`, complete and remove only that emitter. Never fail the SPI transaction because a browser connection closed. `MessageEventHubTest` calls `afterPropertiesSet()` and `destroy()`, asserts the executor is shut down, and confirms all emitter sets are cleared.

- [ ] **Step 5: Verify APIs and commit**

Run: `mvn -pl business-base/backend -am -Dtest=BusinessMessageControllerTest,MessageEventHubTest,BusinessMessagePublisherTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all selected tests pass, including cross-user 404 and targeted SSE delivery.

```bash
git add business-base/backend/src/main/java/com/flowmind/business/message business-base/backend/src/main/java/com/flowmind/business/common/BusinessResourceNotFoundException.java business-base/backend/src/main/java/com/flowmind/business/common/BusinessExceptionHandler.java business-base/backend/src/main/java/com/flowmind/business/config/BusinessTimeConfiguration.java business-base/backend/src/test/java/com/flowmind/business/message/BusinessMessageControllerTest.java business-base/backend/src/test/java/com/flowmind/business/message/MessageEventHubTest.java
git commit -m "feat(business): expose inbox and notification stream"
```

### Task 5: Starter-Only Remind And Administrator Alert APIs

**Files:**
- Modify: `business-base/backend/src/main/java/com/flowmind/business/platform/PlatformFacade.java`
- Modify: `business-base/backend/src/main/java/com/flowmind/business/workflow/dto/WorkflowActionRequests.java`
- Modify: `business-base/backend/src/main/java/com/flowmind/business/workflow/WorkflowActionController.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/workflow/WorkflowReminderService.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/admin/AdminAlertController.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/admin/AdminAlertService.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/admin/AdminAlertDtos.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/core/runtime/RuntimeErrorCodes.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ReminderRecordRepository.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/DefaultProcessMonitorService.java`
- Modify test: `platform/platform-core/src/test/java/com/flowmind/platform/core/monitor/DefaultProcessMonitorServiceTest.java`
- Test: `business-base/backend/src/test/java/com/flowmind/business/workflow/WorkflowReminderServiceTest.java`
- Test: `business-base/backend/src/test/java/com/flowmind/business/admin/AdminAlertControllerTest.java`

**Interfaces:**
- Produces: `POST /api/workflow/tasks/{taskId}/remind` with JSON `{content, expectedTaskVersion}` and required `Idempotency-Key`.
- Produces: `GET /api/admin/alerts` and `POST /api/admin/alerts/{alertId}/handle`.
- Extends `PlatformFacade` with `remind`, `reminders`, `alerts`, and `handleAlert` wrappers around `ProcessMonitorService`.
- Consumes: `BusinessAuthorizationProvider#isAdministrator(String userId)` for both administrator endpoints.

- [ ] **Step 1: Write failing authorization, cooldown, and admin tests**

```java
@Test
void onlyStarterCanRemindActiveTask() {
    when(currentUser.currentUser()).thenReturn(new BusinessUser("other-user", "dept-1"));
    when(platform.getTask("task-1")).thenReturn(activeTask(3L));
    when(platform.getInstance("instance-1")).thenReturn(instanceStartedBy("starter-1"));
    assertThrows(BusinessAccessDeniedException.class,
            () -> service.remind("task-1", "key-1", request(3L, "请尽快处理")));
}
```

```java
@Test
void rejectsSecondManualReminderInsideFiveMinutes() {
    when(reminderRepository.countSentSince(eq("task-1"), eq("MANUAL"), any(LocalDateTime.class)))
            .thenReturn(1L);
    RuntimeStateException error = assertThrows(RuntimeStateException.class,
            () -> monitorService.remindTask(platformRequest("op-key-2")));
    assertEquals(RuntimeErrorCodes.TASK_REMIND_COOLDOWN, error.getErrorCode());
}
```

```java
mockMvc.perform(get("/api/admin/alerts").session(userSession("sales01")))
        .andExpect(status().isForbidden());
mockMvc.perform(get("/api/admin/alerts").session(userSession("admin01")))
        .andExpect(status().isOk());
```

- [ ] **Step 2: Run tests and verify APIs are absent**

Run: `mvn -pl business-base/backend -am -Dtest=WorkflowReminderServiceTest,AdminAlertControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because reminder and alert services are missing.

- [ ] **Step 3: Add platform wrappers and starter reminder service**

Inject `ProcessMonitorService` into `PlatformFacade` and add:

```java
public ReminderDTO remind(RemindTaskRequest request) { return monitorService.remindTask(request); }
public PageResult<ReminderDTO> reminders(ReminderQuery query) { return monitorService.queryReminders(query); }
public PageResult<AlertDTO> alerts(AlertQuery query) { return monitorService.queryAlerts(query); }
public AlertDTO handleAlert(HandleAlertRequest request) { return monitorService.handleAlert(request); }
```

Add request DTO:

```java
public static class Remind extends Basic {
    @NotBlank
    @Size(max = 500)
    private String content;
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
```

Business Base loads the task and instance only to verify that the trusted current user equals `instance.starterUserId`. It then calls Platform, which atomically validates running instance state, active/claimed task state, and `expectedTaskVersion`. Platform owns cooldown so an exact `Idempotency-Key` replay is resolved before cooldown evaluation.

```java
TaskDTO task = platform.getTask(taskId);
ProcessInstanceDetailDTO instance = platform.getInstance(task.getInstanceId());
String userId = currentUser.currentUser().getUserId();
if (!userId.equals(instance.getStarterUserId())) {
    throw new BusinessAccessDeniedException("仅流程发起人可以催办当前任务");
}
```

```java
RemindTaskRequest target = new RemindTaskRequest();
target.setTaskId(taskId);
target.setExpectedTaskVersion(input.getExpectedTaskVersion());
target.setOperatorUserId(userId);
target.setComment(input.getContent());
target.setOperationId(operationIdFactory.create("workflow", "remind", taskId, userId, key));
return platform.remind(target);
```

Controller addition:

```java
@PostMapping("/remind")
public ReminderDTO remind(@PathVariable String taskId,
        @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody WorkflowActionRequests.Remind body) {
    return reminderService.remind(taskId, key, body);
}
```

- [ ] **Step 4: Implement admin query and handle adapters**

```java
private void requireAdministrator() {
    String userId = currentUser.currentUser().getUserId();
    if (!authorization.isAdministrator(userId)) {
        throw new BusinessAccessDeniedException("仅管理员可以访问异常告警");
    }
}

public PageResult<AlertDTO> query(AlertQuery query) {
    requireAdministrator();
    return platform.alerts(query);
}

public AlertDTO handle(String alertId, HandleRequest body, String key) {
    requireAdministrator();
    String userId = currentUser.currentUser().getUserId();
    HandleAlertRequest request = new HandleAlertRequest();
    request.setAlertId(alertId);
    request.setTargetStatus(body.getTargetStatus());
    request.setComment(body.getComment());
    request.setOperatorUserId(userId);
    request.setOperationId(operationIdFactory.create("admin-alert", "handle", alertId, userId, key));
    return platform.handleAlert(request);
}
```

Add the persistent cooldown query:

```java
public long countSentSince(String taskId, String reminderType, LocalDateTime createdFrom) {
    Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM process_reminder_record "
                    + "WHERE task_id = ? AND reminder_type = ? AND reminder_status = 'SENT' "
                    + "AND created_at >= ?",
            Long.class, taskId, reminderType, DefinitionRowMappers.toDbString(createdFrom));
    return count == null ? 0L : count.longValue();
}
```

Add `RuntimeErrorCodes.TASK_REMIND_COOLDOWN = "FLOW_TASK_REMIND_COOLDOWN"`. In `DefaultProcessMonitorService#remindTask`, put this check inside the transaction after `requireOpenTask` and before inserting a new reminder:

```java
if (reminderRepository.countSentSince(task.getId(), ReminderTypeEnum.MANUAL.name(),
        LocalDateTime.now().minusMinutes(5)) > 0L) {
    throw new RuntimeStateException(RuntimeErrorCodes.TASK_REMIND_COOLDOWN,
            "五分钟内请勿重复催办");
}
```

Because `operationExecutor.begin` and `REPLAY_SUCCESS` run before this transaction, a retry with the same operation ID returns its original result; a different key within five minutes receives the Platform conflict, which Business Base already maps to HTTP 409.

- [ ] **Step 5: Verify security and commit**

Run: `mvn -pl business-base/backend -am -Dtest=WorkflowReminderServiceTest,AdminAlertControllerTest,WorkflowActionControllerTest,PlatformFacadeActionTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all selected tests pass; non-starters and non-admins receive 403, cooldown receives 409.

```bash
git add business-base/backend/src/main/java/com/flowmind/business/platform/PlatformFacade.java business-base/backend/src/main/java/com/flowmind/business/workflow/dto/WorkflowActionRequests.java business-base/backend/src/main/java/com/flowmind/business/workflow/WorkflowActionController.java business-base/backend/src/main/java/com/flowmind/business/workflow/WorkflowReminderService.java business-base/backend/src/main/java/com/flowmind/business/admin platform/platform-core/src/main/java/com/flowmind/platform/core/runtime/RuntimeErrorCodes.java platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ReminderRecordRepository.java platform/platform-core/src/main/java/com/flowmind/platform/core/monitor/DefaultProcessMonitorService.java platform/platform-core/src/test/java/com/flowmind/platform/core/monitor/DefaultProcessMonitorServiceTest.java business-base/backend/src/test/java/com/flowmind/business/workflow/WorkflowReminderServiceTest.java business-base/backend/src/test/java/com/flowmind/business/admin/AdminAlertControllerTest.java
git commit -m "feat(business): add task remind and alert APIs"
```

### Task 6: Server-Owned Task Timing Status

**Files:**
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/api/dto/TaskDTO.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/persistence/entity/TaskQueryEntity.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ActiveTaskRepository.java`
- Modify: `platform/platform-core/src/main/java/com/flowmind/platform/core/query/RuntimeQueryAssembler.java`
- Modify test: `platform/platform-core/src/test/java/com/flowmind/platform/core/query/DefaultTaskQueryServiceTest.java`
- Create: `business-base/backend/src/main/java/com/flowmind/business/workflow/TaskTimingCalculator.java`
- Modify: `business-base/backend/src/main/java/com/flowmind/business/platform/PlatformDtoMapper.java`
- Modify: `business-base/backend/src/main/java/com/flowmind/business/workflow/dto/WorkflowTaskResponse.java`
- Test: `business-base/backend/src/test/java/com/flowmind/business/workflow/TaskTimingCalculatorTest.java`
- Modify test: `business-base/backend/src/test/java/com/flowmind/business/platform/PlatformDtoMapperTest.java`

**Interfaces:**
- Adds `TaskDTO#getBeforeDueMinutes(): Integer`, carrying the same effective node value used by the scanner.
- Produces enum: `TaskTimingCalculator.Status { NORMAL, DUE_SOON, OVERDUE }`.
- Produces: `TaskTimingCalculator#calculate(LocalDateTime dueAt, int beforeDueMinutes): Result`.
- Adds JSON fields `timingStatus` and `remainingMinutes` to `WorkflowTaskResponse`; preserves `dueAt`.

- [ ] **Step 1: Write failing effective-policy and timing boundary tests**

Extend `DefaultTaskQueryServiceTest` so the task row's joined node has `beforeDueMinutes=45` and `durationMinutes=30`, then assert the effective clamped value:

```java
TaskDTO task = service.getTask("task-1");
assertEquals(Integer.valueOf(30), task.getBeforeDueMinutes());
```

Add a second row with `beforeDueMinutes=20` and `durationMinutes=60`:

```java
assertEquals(Integer.valueOf(20), service.getTask("task-custom").getBeforeDueMinutes());
```

Use the same fixed clock for Business timing boundaries:

```java
Clock clock = Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
TaskTimingCalculator calculator = new TaskTimingCalculator(clock);
assertEquals(Status.NORMAL, calculator.calculate(at("10:31"), 30).getStatus());
assertEquals(Status.DUE_SOON, calculator.calculate(at("10:30"), 30).getStatus());
assertEquals(Long.valueOf(30), calculator.calculate(at("10:30"), 30).getRemainingMinutes());
assertEquals(Status.OVERDUE, calculator.calculate(at("10:00"), 30).getStatus());
assertEquals(Status.OVERDUE, calculator.calculate(at("09:59"), 30).getStatus());
assertEquals(Long.valueOf(-1), calculator.calculate(at("09:59"), 30).getRemainingMinutes());
assertEquals(Status.NORMAL, calculator.calculate(null, 30).getStatus());
assertNull(calculator.calculate(null, 30).getRemainingMinutes());
```

- [ ] **Step 2: Run tests and verify timing fields are missing**

Run: `mvn -pl business-base/backend -am -Dtest=DefaultTaskQueryServiceTest,TaskTimingCalculatorTest,PlatformDtoMapperTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: compilation fails because `TaskDTO#getBeforeDueMinutes`, `TaskTimingCalculator`, and response setters do not exist.

- [ ] **Step 3: Carry the effective node reminder threshold through Platform queries**

Add `Integer beforeDueMinutes` to both `TaskQueryEntity` and `TaskDTO`, and copy it in `RuntimeQueryAssembler#toTaskDTO`:

```java
dto.setBeforeDueMinutes(entity.getBeforeDueMinutes());
```

Add this expression to both task select builders in `ActiveTaskRepository`, immediately after `t.due_at`:

```java
private void appendEffectiveBeforeDueMinutes(StringBuilder sql) {
    sql.append(", ").append(effectiveBeforeDueMinutesSql("n"))
            .append(" AS before_due_minutes ");
}
```

Read the alias in `QUERY_ROW_MAPPER`:

```java
entity.setBeforeDueMinutes(Integer.valueOf(resultSet.getInt("before_due_minutes")));
```

All `TaskQueryEntity` selects already join `process_node n`; invoke the helper from `appendTodoSourceSelect`, `appendAdminTaskSelect`, `queryTaskById`, and `queryOpenTasksByInstanceId` so list and detail DTOs agree.

- [ ] **Step 4: Implement deterministic Business timing classification**

```java
public Result calculate(LocalDateTime dueAt, int beforeDueMinutes) {
    if (dueAt == null) return new Result(Status.NORMAL, null);
    LocalDateTime now = LocalDateTime.now(clock);
    long remaining = ChronoUnit.MINUTES.between(now, dueAt);
    if (!dueAt.isAfter(now)) return new Result(Status.OVERDUE, Long.valueOf(remaining));
    if (!dueAt.isAfter(now.plusMinutes(beforeDueMinutes))) {
        return new Result(Status.DUE_SOON, Long.valueOf(remaining));
    }
    return new Result(Status.NORMAL, Long.valueOf(remaining));
}
```

Add to `WorkflowTaskResponse`:

```java
private String timingStatus;
private Long remainingMinutes;
public String getTimingStatus() { return timingStatus; }
public void setTimingStatus(String timingStatus) { this.timingStatus = timingStatus; }
public Long getRemainingMinutes() { return remainingMinutes; }
public void setRemainingMinutes(Long remainingMinutes) { this.remainingMinutes = remainingMinutes; }
```

Inject the calculator into `PlatformDtoMapper` and use the effective value delivered by Platform, falling back to `30` only for legacy callers that construct `TaskDTO` without the new field:

```java
int beforeDueMinutes = source.getBeforeDueMinutes() == null
        ? 30 : source.getBeforeDueMinutes().intValue();
TaskTimingCalculator.Result timing = timingCalculator.calculate(source.getDueAt(), beforeDueMinutes);
target.setTimingStatus(timing.getStatus().name());
target.setRemainingMinutes(timing.getRemainingMinutes());
```

- [ ] **Step 5: Run platform and Business timing tests**

Run: `mvn -pl business-base/backend -am -Dtest=DefaultTaskQueryServiceTest,TaskTimingCalculatorTest,PlatformDtoMapperTest,WorkflowQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all selected tests pass with per-node effective thresholds and stable fixed-clock values.

- [ ] **Step 6: Commit task timing fields**

```bash
git add platform/platform-core/src/main/java/com/flowmind/platform/api/dto/TaskDTO.java platform/platform-core/src/main/java/com/flowmind/platform/persistence/entity/TaskQueryEntity.java platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ActiveTaskRepository.java platform/platform-core/src/main/java/com/flowmind/platform/core/query/RuntimeQueryAssembler.java platform/platform-core/src/test/java/com/flowmind/platform/core/query/DefaultTaskQueryServiceTest.java business-base/backend/src/main/java/com/flowmind/business/workflow/TaskTimingCalculator.java business-base/backend/src/main/java/com/flowmind/business/platform/PlatformDtoMapper.java business-base/backend/src/main/java/com/flowmind/business/workflow/dto/WorkflowTaskResponse.java business-base/backend/src/test/java/com/flowmind/business/workflow/TaskTimingCalculatorTest.java business-base/backend/src/test/java/com/flowmind/business/platform/PlatformDtoMapperTest.java
git commit -m "feat(business): expose task timing status"
```

### Task 7: Frontend Message Contract, Store, And SSE Recovery

**Files:**
- Create: `business-base/frontend/src/types/messages.ts`
- Create: `business-base/frontend/src/api/messages.ts`
- Create: `business-base/frontend/src/stores/messages.ts`
- Test: `business-base/frontend/src/api/messages.spec.ts`
- Test: `business-base/frontend/src/stores/messages.spec.ts`

**Interfaces:**
- Produces: `MessageType = "TASK_DUE_SOON" | "TASK_TIMEOUT" | "TASK_REMIND" | "ALERT"`.
- Produces: `useMessageStore()` with `bootstrap`, `connect`, `disconnect`, `loadPage`, `markRead`, `markAllRead`, `handleLiveMessage`, `nextModal`, and `dismissModal`.
- Store exposes `unreadCount`, `historicalUnreadCount`, `records`, `toastQueue`, `modalQueue`, and `connected`.

- [ ] **Step 1: Write failing API and store tests**

```ts
it("bootstraps history without creating live popups", async () => {
  api.listMessages.mockResolvedValue(page([message("TASK_TIMEOUT")]));
  api.getUnreadCount.mockResolvedValue(1);
  const store = useMessageStore();
  await store.bootstrap();
  expect(store.records).toHaveLength(1);
  expect(store.modalQueue).toHaveLength(0);
  expect(store.historicalUnreadCount).toBe(1);
});

it("routes only new severe SSE messages into the modal queue", () => {
  const store = useMessageStore();
  store.handleLiveMessage(message("TASK_DUE_SOON"));
  store.handleLiveMessage(message("TASK_TIMEOUT"));
  expect(store.toastQueue.map((item) => item.messageType)).toEqual(["TASK_DUE_SOON"]);
  expect(store.modalQueue.map((item) => item.messageType)).toEqual(["TASK_TIMEOUT"]);
});

it("deduplicates a replayed SSE message", () => {
  const store = useMessageStore();
  const item = message("TASK_REMIND", "message-1");
  store.handleLiveMessage(item);
  store.handleLiveMessage(item);
  expect(store.toastQueue).toHaveLength(1);
  expect(store.unreadCount).toBe(1);
});
```

- [ ] **Step 2: Run tests and verify modules are missing**

Run from `business-base/frontend`: `npm test -- src/api/messages.spec.ts src/stores/messages.spec.ts`

Expected: Vitest fails to resolve `./messages` and `../stores/messages`.

- [ ] **Step 3: Implement REST and SSE API helpers**

```ts
export function listMessages(query: MessageQuery): Promise<MessagePage> {
  return requestJson(`/api/messages${buildQuery(query)}`);
}
export function getUnreadCount(): Promise<{ unreadCount: number }> {
  return requestJson("/api/messages/unread-count");
}
export function markMessageRead(id: string): Promise<UserMessage> {
  return requestJson(`/api/messages/${encodeURIComponent(id)}/read`, { method: "POST" });
}
export function markAllMessagesRead(): Promise<{ updated: number }> {
  return requestJson("/api/messages/read-all", { method: "POST" });
}
export function openMessageEvents(): EventSource {
  return new EventSource("/api/messages/events", { withCredentials: true });
}
```

Define `UserMessage` with `messageId`, `messageType`, `title`, `content`, `payload`, `severity`, `readStatus`, `createdAt`, and `readAt`; do not expose `recipientUserId`.

- [ ] **Step 4: Implement store bootstrap, deduplication, and bounded reconnect**

```ts
const reconnectDelays = [1000, 2000, 5000, 10000, 30000];

function handleLiveMessage(message: UserMessage): void {
  if (seenIds.has(message.messageId)) return;
  seenIds.add(message.messageId);
  records.value.unshift(message);
  if (message.readStatus === "UNREAD") unreadCount.value += 1;
  if (message.messageType === "TASK_TIMEOUT" || message.messageType === "ALERT") {
    modalQueue.value.push(message);
  } else {
    toastQueue.value.push(message);
  }
}

function scheduleReconnect(): void {
  if (reconnectTimer !== undefined || manuallyDisconnected) return;
  const delay = reconnectDelays[Math.min(reconnectAttempt, reconnectDelays.length - 1)];
  reconnectAttempt += 1;
  reconnectTimer = window.setTimeout(async () => {
    reconnectTimer = undefined;
    try {
      await syncInbox();
      connect();
    } catch (error) {
      if (error instanceof WorkflowApiError && error.status === 401) {
        disconnect();
        return;
      }
      scheduleReconnect();
    }
  }, delay);
}

function connect(): void {
  manuallyDisconnected = false;
  source?.close();
  source = messageApi.openMessageEvents();
  source.addEventListener("message", (event) => {
    reconnectAttempt = 0;
    connected.value = true;
    handleLiveMessage(JSON.parse((event as MessageEvent<string>).data) as UserMessage);
  });
  source.addEventListener("heartbeat", () => {
    reconnectAttempt = 0;
    connected.value = true;
  });
  source.onerror = () => {
    connected.value = false;
    source?.close();
    source = undefined;
    scheduleReconnect();
  };
}
```

`bootstrap()` loads the first inbox page and unread count, stores the initial unread number in `historicalUnreadCount`, records historical IDs in `seenIds`, and only then calls `connect()`. On `EventSource.onerror`, close the native source before `scheduleReconnect()` so only the bounded retry loop owns reconnection. `syncInbox()` merges records and refreshes unread count without filling either live queue; its REST 401 flows through the existing `requestJson` authentication-required event. `disconnect()` closes `EventSource`, clears the timer, and empties neither persisted records nor unread count.

- [ ] **Step 5: Verify the store and commit**

Run from `business-base/frontend`: `npm test -- src/api/messages.spec.ts src/stores/messages.spec.ts && npm run typecheck`

Expected: selected tests and typecheck pass.

```bash
git add business-base/frontend/src/types/messages.ts business-base/frontend/src/api/messages.ts business-base/frontend/src/stores/messages.ts business-base/frontend/src/api/messages.spec.ts business-base/frontend/src/stores/messages.spec.ts
git commit -m "feat(frontend): add persisted notification store"
```

### Task 8: Message Center, Live Prompts, And Admin Alert View

**Files:**
- Modify: `business-base/frontend/package.json`
- Modify: `business-base/frontend/package-lock.json`
- Create: `business-base/frontend/src/types/alerts.ts`
- Create: `business-base/frontend/src/api/admin-alerts.ts`
- Create: `business-base/frontend/src/components/MessageCenter.vue`
- Create: `business-base/frontend/src/views/AdminAlertView.vue`
- Modify: `business-base/frontend/src/App.vue`
- Modify: `business-base/frontend/src/main.ts`
- Modify: `business-base/frontend/src/router/index.ts`
- Test: `business-base/frontend/src/components/MessageCenter.spec.ts`
- Test: `business-base/frontend/src/api/admin-alerts.spec.ts`
- Test: `business-base/frontend/src/views/AdminAlertView.spec.ts`
- Modify test: `business-base/frontend/src/App.spec.ts`
- Modify test: `business-base/frontend/src/router/index.spec.ts`

**Interfaces:**
- Consumes `useMessageStore()` and existing authenticated `useAuthStore()`.
- Produces a bell icon with unread badge, an inbox drawer, sequential Element Plus notifications/dialogs, and `/admin/alerts`.
- Admin navigation uses the existing `AuthenticatedUser.administrator: boolean` returned by `/api/auth/login` and `/api/auth/me`.

- [ ] **Step 1: Add the icon dependency and failing UI tests**

Run from `business-base/frontend`: `npm install @element-plus/icons-vue@2.3.2 --save-exact`

Then write:

```ts
it("shows unread count and marks a message read when opened", async () => {
  const wrapper = mountMessageCenter({ unreadCount: 3, records: [unreadMessage()] });
  expect(wrapper.get('[data-test="message-unread-count"]').text()).toBe("3");
  await wrapper.get('[data-test="message-item-message-1"]').trigger("click");
  expect(messageStore.markRead).toHaveBeenCalledWith("message-1");
});

it("navigates task and alert messages to their owned detail routes", async () => {
  const wrapper = mountMessageCenter({ records: [taskMessage("task-1"), alertMessage("alert-1")] });
  await wrapper.get('[data-test="message-item-task-message"]').trigger("click");
  expect(router.push).toHaveBeenCalledWith({ name: "workflow-task-detail", params: { taskId: "task-1" } });
  await wrapper.get('[data-test="message-item-alert-message"]').trigger("click");
  expect(router.push).toHaveBeenCalledWith({ name: "admin-alerts", query: { alertId: "alert-1" } });
});
```

```ts
it("shows admin navigation only for administrators", () => {
  expect(mountApp({ administrator: false }).find('[data-test="admin-alert-nav"]').exists()).toBe(false);
  expect(mountApp({ administrator: true }).find('[data-test="admin-alert-nav"]').exists()).toBe(true);
});
```

```ts
it("handles an alert with a required idempotency key", async () => {
  const wrapper = mountAlertView();
  await wrapper.get('[data-test="handle-alert-alert-1"]').trigger("click");
  expect(api.handleAlert).toHaveBeenCalledWith("alert-1", expect.any(String), {
    targetStatus: "HANDLED",
    comment: "已确认",
  });
});
```

- [ ] **Step 2: Run UI tests and verify components/routes are missing**

Run from `business-base/frontend`: `npm test -- src/api/admin-alerts.spec.ts src/components/MessageCenter.spec.ts src/views/AdminAlertView.spec.ts src/App.spec.ts src/router/index.spec.ts`

Expected: tests fail because the components and `/admin/alerts` route do not exist.

- [ ] **Step 3: Implement the message center drawer**

Use Element Plus `Bell`, `ElBadge`, `ElDrawer`, `ElTabs`, and `ElButton`. The trigger must be a stable 36-by-36 icon button with `title="消息中心"` and `aria-label="消息中心"`.

```vue
<el-badge :value="store.unreadCount" :hidden="store.unreadCount === 0" :max="99">
  <button class="icon-button" type="button" title="消息中心" aria-label="消息中心" @click="open = true">
    <Bell :size="18" aria-hidden="true" />
  </button>
</el-badge>
<el-drawer v-model="open" title="消息中心" size="min(420px, 100vw)">
  <el-button :disabled="store.unreadCount === 0" @click="store.markAllRead()">全部已读</el-button>
  <button v-for="message in store.records" :key="message.messageId"
      :data-test="`message-item-${message.messageId}`" class="message-row"
      type="button" @click="openMessage(message)">
    <strong>{{ message.title }}</strong><span>{{ message.content }}</span>
  </button>
</el-drawer>
```

No drawer/card nesting; message rows are unframed list items separated by borders.

Implement owned navigation after marking the row read:

```ts
async function openMessage(message: UserMessage): Promise<void> {
  if (message.readStatus === "UNREAD") await store.markRead(message.messageId);
  if (message.messageType === "ALERT" && typeof message.payload.alertId === "string") {
    await router.push({ name: "admin-alerts", query: { alertId: message.payload.alertId } });
  } else if (typeof message.payload.taskId === "string") {
    await router.push({ name: "workflow-task-detail", params: { taskId: message.payload.taskId } });
  } else if (typeof message.payload.instanceId === "string") {
    await router.push({ name: "workflow-instance-detail", params: { instanceId: message.payload.instanceId } });
  }
  open.value = false;
}
```

- [ ] **Step 4: Wire live prompts and the administrator alert view**

In the authenticated shell lifecycle, call `messageStore.bootstrap()` after auth resolves and `messageStore.disconnect()` on logout/unmount. Consume toast messages with `ElNotification`; consume only `messageStore.nextModal` with one awaited `ElMessageBox.alert` at a time, then call `dismissModal()`.

Immediately after the first successful bootstrap, show one non-modal summary and then clear `historicalUnreadCount` so route changes cannot repeat it:

```ts
if (messageStore.historicalUnreadCount > 0) {
  ElNotification({ title: "未读消息", message: `您有 ${messageStore.historicalUnreadCount} 条未读消息`, type: "info" });
  messageStore.acknowledgeHistoricalSummary();
}
```

```ts
watch(() => messageStore.toastQueue[0], (message) => {
  if (!message) return;
  ElNotification({ title: message.title, message: message.content, type: "warning" });
  messageStore.shiftToast();
});

watch(() => messageStore.nextModal, async (message) => {
  if (!message || modalOpen.value) return;
  modalOpen.value = true;
  try {
    await ElMessageBox.alert(message.content, message.title, { type: "warning" });
  } finally {
    await messageStore.markRead(message.messageId);
    messageStore.dismissModal();
    modalOpen.value = false;
  }
});
```

Add the route:

```ts
{
  path: "/admin/alerts",
  name: "admin-alerts",
  component: () => import("../views/AdminAlertView.vue"),
  meta: { requiresAuth: true, requiresAdmin: true },
}
```

The route guard must redirect authenticated non-admin users to `/workflow/todo`; the backend still enforces authorization.

Define the alert client contract:

```ts
export function listAlerts(query: AlertQuery): Promise<AlertPage> {
  return requestJson(`/api/admin/alerts${buildQuery(query)}`);
}
export function handleAlert(alertId: string, key: string, body: HandleAlertInput): Promise<AlertRecord> {
  return requestJson(`/api/admin/alerts/${encodeURIComponent(alertId)}/handle`, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: JSON.stringify(body),
  });
}
```

`AdminAlertView` uses an unframed `ElTable` with severity, process/instance, node, created time, and status columns plus pagination. After either a successful handle or a 409 replay/conflict, reload the current page; preserve the backend error text in the failure notification.

- [ ] **Step 5: Verify shell UI and commit**

Run from `business-base/frontend`: `npm test -- src/api/admin-alerts.spec.ts src/components/MessageCenter.spec.ts src/views/AdminAlertView.spec.ts src/App.spec.ts src/router/index.spec.ts && npm run typecheck`

Expected: selected tests and typecheck pass.

```bash
git add business-base/frontend/package.json business-base/frontend/package-lock.json business-base/frontend/src/types/alerts.ts business-base/frontend/src/api/admin-alerts.ts business-base/frontend/src/components/MessageCenter.vue business-base/frontend/src/views/AdminAlertView.vue business-base/frontend/src/App.vue business-base/frontend/src/main.ts business-base/frontend/src/router/index.ts business-base/frontend/src/api/admin-alerts.spec.ts business-base/frontend/src/components/MessageCenter.spec.ts business-base/frontend/src/views/AdminAlertView.spec.ts business-base/frontend/src/App.spec.ts business-base/frontend/src/router/index.spec.ts
git commit -m "feat(frontend): add message center and alert view"
```

### Task 9: Timing Badges And Starter Remind Interaction

**Files:**
- Modify: `business-base/frontend/src/types/workflow.ts`
- Modify: `business-base/frontend/src/api/workflow.ts`
- Modify: `business-base/frontend/src/stores/workflow.ts`
- Modify: `business-base/frontend/src/views/WorkflowListView.vue`
- Modify: `business-base/frontend/src/views/WorkflowDetailView.vue`
- Modify test: `business-base/frontend/src/api/workflow.spec.ts`
- Modify test: `business-base/frontend/src/stores/workflow.spec.ts`
- Modify test: `business-base/frontend/src/views/WorkflowListView.spec.ts`
- Modify test: `business-base/frontend/src/views/WorkflowDetailView.spec.ts`

**Interfaces:**
- Extends `WorkflowTask` with `timingStatus: "NORMAL" | "DUE_SOON" | "OVERDUE"` and `remainingMinutes?: number`.
- Produces: `remindTask(taskId, idempotencyKey, { content, expectedTaskVersion })`.
- Shows the remind command only in a starter-owned instance detail with at least one active task.

- [ ] **Step 1: Write failing timing badge and remind tests**

```ts
it("renders server-provided due-soon and overdue states", () => {
  const wrapper = mountList([task({ timingStatus: "DUE_SOON", remainingMinutes: 12 }),
    task({ timingStatus: "OVERDUE", remainingMinutes: -8 })]);
  expect(wrapper.text()).toContain("12 分钟后超时");
  expect(wrapper.text()).toContain("已超时 8 分钟");
});
```

```ts
it("lets the starter remind an active task", async () => {
  const wrapper = mountDetail({ mode: "instance", starterUserId: "starter-1",
    currentUserId: "starter-1", activeTask: task({ taskVersion: 4 }) });
  await wrapper.get('[data-test="remind-task"]').trigger("click");
  await wrapper.get('[data-test="remind-content"]').setValue("请尽快处理");
  await wrapper.get('[data-test="confirm-remind"]').trigger("click");
  expect(workflowStore.remind).toHaveBeenCalledWith("task-1", {
    content: "请尽快处理",
    expectedTaskVersion: 4,
  });
});
```

```ts
it("hides remind for non-starters", () => {
  const wrapper = mountDetail({ starterUserId: "starter-1", currentUserId: "approver-1" });
  expect(wrapper.find('[data-test="remind-task"]').exists()).toBe(false);
});
```

- [ ] **Step 2: Run tests and verify type/API gaps**

Run from `business-base/frontend`: `npm test -- src/api/workflow.spec.ts src/stores/workflow.spec.ts src/views/WorkflowListView.spec.ts src/views/WorkflowDetailView.spec.ts`

Expected: TypeScript compilation fails on `timingStatus`, `remainingMinutes`, and `remind`.

- [ ] **Step 3: Add the workflow API and store action**

```ts
export interface RemindTaskInput {
  content: string;
  expectedTaskVersion: number;
}

export function remindTask(taskId: string, key: string, input: RemindTaskInput): Promise<ReminderResponse> {
  return requestJson(`/api/workflow/tasks/${encodeURIComponent(taskId)}/remind`, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: JSON.stringify(input),
  });
}
```

The store creates one key per click and reuses it only while the same request is in flight:

```ts
async function remind(taskId: string, input: RemindTaskInput): Promise<void> {
  const key = crypto.randomUUID();
  await workflowApi.remindTask(taskId, key, input);
  ElMessage.success("催办消息已发送");
}
```

- [ ] **Step 4: Render stable badges and the starter dialog**

```vue
<el-tag v-if="task.timingStatus === 'DUE_SOON'" type="warning" effect="plain">
  {{ task.remainingMinutes }} 分钟后超时
</el-tag>
<el-tag v-else-if="task.timingStatus === 'OVERDUE'" type="danger" effect="dark">
  已超时 {{ Math.abs(task.remainingMinutes ?? 0) }} 分钟
</el-tag>
```

Use a normal command button with the Element Plus `BellRing` icon in instance detail. The dialog contains a labeled textarea limited to 500 characters and Cancel/Confirm commands. Disable confirm while sending, close only on success, and show the backend 409 message unchanged for cooldown feedback.

```ts
const canRemind = computed(() => detail.value?.instance.starterUserId === auth.user?.userId
  && detail.value.activeTasks.length > 0);

async function confirmRemind(): Promise<void> {
  const task = selectedActiveTask.value;
  if (!task || !remindContent.value.trim()) return;
  try {
    await workflow.remind(task.taskId, {
      content: remindContent.value.trim(),
      expectedTaskVersion: task.taskVersion,
    });
    remindOpen.value = false;
  } catch (error) {
    if (error instanceof WorkflowApiError && error.status === 409) {
      await workflow.loadInstanceDetail(detail.value!.instance.instanceId);
    }
    ElMessage.error(error instanceof Error ? error.message : "催办失败");
  }
}
```

- [ ] **Step 5: Verify task UX and commit**

Run from `business-base/frontend`: `npm test -- src/api/workflow.spec.ts src/stores/workflow.spec.ts src/views/WorkflowListView.spec.ts src/views/WorkflowDetailView.spec.ts && npm run typecheck`

Expected: selected tests and typecheck pass.

```bash
git add business-base/frontend/src/types/workflow.ts business-base/frontend/src/api/workflow.ts business-base/frontend/src/stores/workflow.ts business-base/frontend/src/views/WorkflowListView.vue business-base/frontend/src/views/WorkflowDetailView.vue business-base/frontend/src/api/workflow.spec.ts business-base/frontend/src/stores/workflow.spec.ts business-base/frontend/src/views/WorkflowListView.spec.ts business-base/frontend/src/views/WorkflowDetailView.spec.ts
git commit -m "feat(frontend): show deadlines and starter reminders"
```

### Task 10: Configuration, Integration, And Rollout Verification

**Files:**
- Modify: `business-base/backend/src/main/resources/application.yml`
- Modify: `business-base/backend/src/main/resources/application-local.yml`
- Modify: `business-base/backend/src/main/resources/application-test.yml`
- Modify: `business-base/backend/src/test/java/com/flowmind/business/integration/BusinessBaseApplicationTest.java`
- Create: `business-base/backend/src/test/java/com/flowmind/business/integration/TaskReminderIntegrationTest.java`

**Interfaces:**
- Enables `flow-mind.platform.timeout-scan.enabled=true` for local/runtime Business Base configuration.
- Keeps the test profile scheduler disabled; integration tests invoke the scan service explicitly with a fixed `scanAt`.
- Demonstrates end-to-end flow: scan -> SPI -> inbox -> current-user API and manual remind -> recipient inbox.

- [ ] **Step 1: Write the failing integration scenarios**

Add a local-profile property test before changing YAML:

```java
@Test
void localProfileEnablesTenSecondTimeoutScanning() {
    assertTrue(platformProperties.getTimeoutScan().isEnabled());
    assertEquals(10000L, platformProperties.getTimeoutScan().getFixedDelayMs());
}
```

```java
@Test
void dueSoonScanPersistsOneRecipientMessageAndReplayIsIdempotent() {
    seedActiveTaskDueAt(LocalDateTime.of(2026, 8, 11, 10, 30));
    TimeoutScanRequest request = scanAt(LocalDateTime.of(2026, 8, 11, 10, 0));
    monitorService.scanTimeoutTasks(request);
    monitorService.scanTimeoutTasks(request);
    assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM business_user_message WHERE message_type = 'TASK_DUE_SOON'",
            Integer.class).intValue());
}

@Test
void offlineRecipientReadsPersistedManualReminderAfterLogin() throws Exception {
    postRemindAsStarter("task-1", "remind-key-1", 0L);
    mockMvc.perform(get("/api/messages").session(userSession("approver-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.records[0].messageType").value("TASK_REMIND"));
}
```

- [ ] **Step 2: Run integration tests while scanning is disabled**

Run: `mvn -pl business-base/backend -am -Dtest=BusinessBaseApplicationTest,TaskReminderIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: the local-profile property assertion fails because timeout scanning is still disabled; delivery assertions remain as regression coverage for the completed publisher and schema tasks.

- [ ] **Step 3: Enable local/runtime scanning with conservative scheduler values**

Use this configuration in `application.yml` and `application-local.yml`, preserving existing keys around it:

```yaml
flow-mind:
  platform:
    timeout-scan:
      enabled: true
      initial-delay-ms: 30000
      fixed-delay-ms: 10000
      limit: 200
      operator-user-id: system_timeout
```

Keep `application-test.yml` deterministic:

```yaml
flow-mind:
  platform:
    timeout-scan:
      enabled: false
```

- [ ] **Step 4: Run complete automated verification**

Run from repository root:

```bash
mvn test
```

Expected: all Maven reactor tests pass.

Run from `business-base/frontend`:

```bash
npm test
npm run typecheck
npm run build
```

Expected: all Vitest tests pass, Vue TypeScript reports no errors, and Vite produces `dist` successfully.

- [ ] **Step 5: Perform local browser acceptance**

Install the tested Platform artifacts, then start Business Base backend:

```powershell
mvn -pl platform/platform-starter -am install -DskipTests
mvn -f business-base/backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

In a second terminal start the frontend:

```powershell
Set-Location business-base/frontend
npm run dev -- --port 5174
```

Open `http://127.0.0.1:5174` and verify at desktop `1440x900` and mobile `390x844`:

1. Log in as the process starter and open "我发起的"; a live active task shows the remind command.
2. Send one reminder; the assignee's next login shows one unread persisted message. A second reminder inside five minutes returns the cooldown message.
3. Seed or edit a task due within 30 minutes and run one scan; the assignee receives one toast and the list badge reads `DUE_SOON`.
4. Move the due time into the past and run one scan; the assignee receives one serialized timeout modal and the list badge reads `OVERDUE`.
5. Log in as `admin01`; the alert navigation is visible and timeout alerts can be handled. Log in as a non-admin; the route and API are unavailable.
6. Disconnect SSE, produce a reminder, reconnect, and confirm the inbox recovers it without opening a historical modal.
7. Confirm the bell, unread badge, drawer, task badges, dialogs, and table text do not overlap at either viewport.
8. Mark one message read, restart Business Base, log in again, and confirm both the unread message and the previously read state remain intact.

- [ ] **Step 6: Commit configuration and integration coverage**

```bash
git add business-base/backend/src/main/resources/application.yml business-base/backend/src/main/resources/application-local.yml business-base/backend/src/main/resources/application-test.yml business-base/backend/src/test/java/com/flowmind/business/integration/BusinessBaseApplicationTest.java business-base/backend/src/test/java/com/flowmind/business/integration/TaskReminderIntegrationTest.java
git commit -m "test: verify task reminder delivery end to end"
```
