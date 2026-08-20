package com.flowmind.platform.core.query;

import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeStateException;
import com.flowmind.platform.persistence.entity.HistoryTaskQueryEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import com.flowmind.platform.persistence.entity.TaskQueryEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M3 任务查询服务实现，提供待办、已办、我发起、活动任务、历史和意见查询。
 */
@Service
public class DefaultTaskQueryService implements TaskQueryService {

    private final ProcessHistoryTaskRepository historyTaskRepository;
    private final ActiveTaskRepository activeTaskRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final ProcessTraceAssembler traceAssembler;
    private final RuntimeQueryAssembler queryAssembler;
    private final CurrentUserProvider currentUserProvider;
    private final ReadRecordManager readRecordManager;
    private final ProcessNodeRepository nodeRepository;

    @Autowired
    public DefaultTaskQueryService(ProcessHistoryTaskRepository historyTaskRepository,
                                   ActiveTaskRepository activeTaskRepository,
                                   ProcessInstanceRepository instanceRepository,
                                   ProcessTraceAssembler traceAssembler,
                                   RuntimeQueryAssembler queryAssembler,
                                   CurrentUserProvider currentUserProvider,
                                   ReadRecordManager readRecordManager,
                                   ProcessNodeRepository nodeRepository) {
        this.historyTaskRepository = historyTaskRepository;
        this.activeTaskRepository = activeTaskRepository;
        this.instanceRepository = instanceRepository;
        this.traceAssembler = traceAssembler;
        this.queryAssembler = queryAssembler;
        this.currentUserProvider = currentUserProvider;
        this.readRecordManager = readRecordManager;
        this.nodeRepository = nodeRepository;
    }

    public DefaultTaskQueryService(ProcessHistoryTaskRepository historyTaskRepository,
                                   ActiveTaskRepository activeTaskRepository,
                                   ProcessInstanceRepository instanceRepository,
                                   ProcessTraceAssembler traceAssembler,
                                   RuntimeQueryAssembler queryAssembler,
                                   CurrentUserProvider currentUserProvider,
                                   ReadRecordManager readRecordManager) {
        this(historyTaskRepository, activeTaskRepository, instanceRepository, traceAssembler, queryAssembler,
                currentUserProvider, readRecordManager, null);
    }

    public DefaultTaskQueryService(ProcessHistoryTaskRepository historyTaskRepository,
                                   ActiveTaskRepository activeTaskRepository,
                                   ProcessInstanceRepository instanceRepository,
                                   ProcessTraceAssembler traceAssembler,
                                   RuntimeQueryAssembler queryAssembler,
                                   CurrentUserProvider currentUserProvider) {
        this(historyTaskRepository, activeTaskRepository, instanceRepository, traceAssembler, queryAssembler,
                currentUserProvider, null, null);
    }

    /**
     * 按任务 ID 查询活动任务，并在不存在时按平台统一错误契约抛出任务不存在。
     */
    @Override
    public TaskDTO getTask(String taskId) {
        if (isBlank(taskId)) {
            throw new IllegalArgumentException("taskId must not be empty");
        }
        com.flowmind.platform.persistence.entity.TaskQueryEntity entity =
                activeTaskRepository.queryTaskById(taskId.trim());
        if (entity == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_NOT_FOUND, "active task does not exist");
        }
        return queryAssembler.toTaskDTO(entity);
    }

    @Override
    public PageResult<TaskDTO> queryTodoTasks(TodoTaskQuery query) {
        TodoTaskQuery normalized = query == null ? new TodoTaskQuery() : query;
        UserContext currentUser = currentUser();
        applyTrustedTodoUser(normalized, currentUser);
        if (isBlank(normalized.getTodoSource())) {
            normalized.setTodoSource("OWN");
        }
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<TaskDTO> records = new ArrayList<TaskDTO>();
        for (TaskQueryEntity entity : activeTaskRepository.queryTodoTasks(normalized,
                currentUser.getUserId())) {
            records.add(queryAssembler.toTaskDTO(entity));
        }
        return page(records, pageNo, pageSize,
                activeTaskRepository.countTodoTasks(normalized, currentUser.getUserId()));
    }

    @Override
    public PageResult<HistoryTaskDTO> queryCompletedTasks(CompletedTaskQuery query) {
        CompletedTaskQuery normalized = query == null ? new CompletedTaskQuery() : query;
        UserContext currentUser = currentUser();
        applyTrustedCompletedUser(normalized, currentUser);
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<HistoryTaskDTO> records = new ArrayList<HistoryTaskDTO>();
        for (HistoryTaskQueryEntity entity : historyTaskRepository.queryCompletedTaskRows(normalized)) {
            records.add(queryAssembler.toHistoryTaskDTO(entity));
        }
        return page(records, pageNo, pageSize, historyTaskRepository.countCompletedTaskRows(normalized));
    }

    @Override
    public PageResult<ProcessInstanceDTO> queryStartedInstances(StartedInstanceQuery query) {
        StartedInstanceQuery normalized = query == null ? new StartedInstanceQuery() : query;
        UserContext currentUser = currentUser();
        applyTrustedStarterUser(normalized, currentUser);
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<ProcessInstanceDTO> records = new ArrayList<ProcessInstanceDTO>();
        Map<String, Map<String, String>> nodeNamesByDefinition = new LinkedHashMap<String, Map<String, String>>();
        for (ProcessInstanceEntity entity : instanceRepository.queryStartedInstances(normalized,
                currentUser.getUserId())) {
            ProcessInstanceDTO dto = queryAssembler.toProcessInstanceDTO(entity);
            dto.setCurrentNodeNames(resolveCurrentNodeNames(dto, nodeNamesByDefinition));
            records.add(dto);
        }
        return page(records, pageNo, pageSize,
                instanceRepository.countStartedInstances(normalized, currentUser.getUserId()));
    }

    @Override
    public List<TaskDTO> queryActiveTasks(String instanceId) {
        validateInstanceId(instanceId);
        List<TaskDTO> results = new ArrayList<TaskDTO>();
        for (TaskQueryEntity entity : activeTaskRepository.queryOpenTasksByInstanceId(instanceId)) {
            results.add(queryAssembler.toTaskDTO(entity));
        }
        return results;
    }

    @Override
    public List<HistoryTaskDTO> queryHistoryTasks(String instanceId) {
        validateInstanceId(instanceId);
        List<HistoryTaskDTO> results = new ArrayList<HistoryTaskDTO>();
        for (ProcessHistoryTaskEntity entity : historyTaskRepository.findVisibleByInstanceId(instanceId)) {
            results.add(traceAssembler.toHistoryTaskDTO(entity));
        }
        return results;
    }

    @Override
    public List<ProcessCommentDTO> queryComments(String instanceId) {
        validateInstanceId(instanceId);
        List<ProcessCommentDTO> results = new ArrayList<ProcessCommentDTO>();
        for (ProcessHistoryTaskEntity entity : historyTaskRepository.findVisibleByInstanceId(instanceId)) {
            ProcessCommentDTO dto = traceAssembler.toCommentDTO(entity);
            if (dto != null) {
                results.add(dto);
            }
        }
        return results;
    }

    @Override
    public PageResult<ReadRecordDTO> queryReadRecords(ReadRecordQuery query) {
        if (readRecordManager == null) {
            throw unsupported("queryReadRecords");
        }
        ReadRecordQuery normalized = query == null ? new ReadRecordQuery() : query;
        normalized.setUserId(currentUser().getUserId());
        return readRecordManager.query(normalized);
    }

    private List<String> resolveCurrentNodeNames(ProcessInstanceDTO instance,
                                                 Map<String, Map<String, String>> nodeNamesByDefinition) {
        List<String> names = new ArrayList<String>();
        if (instance == null || instance.getCurrentNodeCodes() == null || instance.getCurrentNodeCodes().isEmpty()) {
            return names;
        }
        Map<String, String> nodeNames = nodeNamesByCode(instance.getDefinitionId(), nodeNamesByDefinition);
        for (String code : instance.getCurrentNodeCodes()) {
            if (!isBlank(code)) {
                String name = nodeNames.get(code);
                names.add(isBlank(name) ? code : name);
            }
        }
        return names;
    }

    private Map<String, String> nodeNamesByCode(String definitionId,
                                                Map<String, Map<String, String>> nodeNamesByDefinition) {
        if (nodeNamesByDefinition.containsKey(definitionId)) {
            return nodeNamesByDefinition.get(definitionId);
        }
        Map<String, String> nodeNames = new LinkedHashMap<String, String>();
        if (nodeRepository != null && !isBlank(definitionId)) {
            for (ProcessNodeEntity node : nodeRepository.findByDefinitionId(definitionId)) {
                if (!isBlank(node.getNodeCode())) {
                    nodeNames.put(node.getNodeCode(), node.getNodeName());
                }
            }
        }
        nodeNamesByDefinition.put(definitionId, nodeNames);
        return nodeNames;
    }

    /**
     * 校验实例 ID，实例详情相关查询必须显式指定目标实例。
     */
    private void validateInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId must not be empty");
        }
    }

    /**
     * 取得平台可信当前用户，查询接口不接受业务端伪造用户身份。
     */
    private UserContext currentUser() {
        UserContext currentUser = currentUserProvider.getCurrentUser();
        if (currentUser == null || isBlank(currentUser.getUserId())) {
            throw new IllegalStateException("current user is required");
        }
        return currentUser;
    }

    /**
     * 将待办查询用户固定为当前用户，并拒绝与当前用户不一致的客户端用户参数。
     */
    private void applyTrustedTodoUser(TodoTaskQuery query, UserContext currentUser) {
        if (!isBlank(query.getUserId()) && !currentUser.getUserId().equals(query.getUserId())) {
            throw new IllegalArgumentException("query userId must match current user");
        }
        query.setUserId(currentUser.getUserId());
    }

    /**
     * 将已办查询用户固定为当前用户，并拒绝与当前用户不一致的客户端用户参数。
     */
    private void applyTrustedCompletedUser(CompletedTaskQuery query, UserContext currentUser) {
        if (!isBlank(query.getUserId()) && !currentUser.getUserId().equals(query.getUserId())) {
            throw new IllegalArgumentException("query userId must match current user");
        }
        query.setUserId(currentUser.getUserId());
    }

    /**
     * 将我发起查询的发起人固定为当前用户，防止横向查询他人实例。
     */
    private void applyTrustedStarterUser(StartedInstanceQuery query, UserContext currentUser) {
        if (!isBlank(query.getStarterUserId()) && !currentUser.getUserId().equals(query.getStarterUserId())) {
            throw new IllegalArgumentException("query starterUserId must match current user");
        }
        query.setStarterUserId(currentUser.getUserId());
    }

    /**
     * 组装分页响应，分页参数已在调用点完成归一化。
     */
    private <T> PageResult<T> page(List<T> records, int pageNo, int pageSize, long total) {
        PageResult<T> result = new PageResult<T>();
        result.setRecords(records);
        result.setPageNo(Integer.valueOf(pageNo));
        result.setPageSize(Integer.valueOf(pageSize));
        result.setTotal(Long.valueOf(total));
        result.setTotalPages(Integer.valueOf((int) ((total + pageSize - 1) / pageSize)));
        return result;
    }

    /**
     * 在旧构造路径未注入可选能力时返回明确的未支持异常。
     */
    private UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(methodName + " is not implemented in C M3");
    }

    /**
     * 判断字符串是否为空白。
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
