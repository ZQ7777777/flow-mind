# Flow Mind platform-starter 0.1.0 generation API

This file is authoritative for generated Java imports and calls. Do not infer alternate packages or method names.

## Exact types

- `com.flowmind.platform.api.service.ProcessRuntimeService`
- `com.flowmind.platform.api.request.StartProcessRequest`
- `com.flowmind.platform.api.request.AttachmentUploadItem`
- `com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum`
- `com.flowmind.platform.api.dto.ProcessInstanceDTO`
- `com.flowmind.platform.api.dto.TaskDTO`

`ProcessRuntimeService.startAndSubmit(StartProcessRequest)` returns `ProcessInstanceDTO`.

## Exact request and response methods

- `StartProcessRequest`: `setProcessCode`, `setStarterUserId`, `setStarterDeptId`, `setOperationId`, `setVariables`, `setAttachments`
- `AttachmentUploadItem`: `setAttachmentCode`, `setOwnerType`, `setFileName`, `setContentType`, `setSizeBytes`, `setContent`
- Use `AttachmentOwnerTypeEnum.INSTANCE` for initiation attachments.
- `ProcessInstanceDTO`: `getInstanceId`, `getInstanceStatus`, `getCreatedTasks`
- `TaskDTO`: `getTaskId`, `getNodeCode`, `getNodeName`

## Minimal compiling usage

```java
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.service.ProcessRuntimeService;

StartProcessRequest request = new StartProcessRequest();
request.setProcessCode("confirmed_process_code");
request.setStarterUserId(user.getUserId());
request.setStarterDeptId(user.getDepartmentId());
request.setOperationId(operationId);
request.setVariables(variables);
request.setAttachments(attachments);
ProcessInstanceDTO result = runtimeService.startAndSubmit(request);
result.getInstanceId();
result.getInstanceStatus();
result.getCreatedTasks();

for (TaskDTO task : result.getCreatedTasks()) {
    String taskId = task.getTaskId();
    String nodeCode = task.getNodeCode();
    String taskName = task.getNodeName();
    // Map these values into the generated response task summary.
}
```
