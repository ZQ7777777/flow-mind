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
import com.flowmind.platform.persistence.entity.HistoryTaskQueryEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.TaskQueryEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    @Autowired
    public DefaultTaskQueryService(ProcessHistoryTaskRepository historyTaskRepository,
                                   ActiveTaskRepository activeTaskRepository,
                                   ProcessInstanceRepository instanceRepository,
                                   ProcessTraceAssembler traceAssembler,
                                   RuntimeQueryAssembler queryAssembler,
                                   CurrentUserProvider currentUserProvider,
                                   ReadRecordManager readRecordManager) {
        this.historyTaskRepository = historyTaskRepository;
        this.activeTaskRepository = activeTaskRepository;
        this.instanceRepository = instanceRepository;
        this.traceAssembler = traceAssembler;
        this.queryAssembler = queryAssembler;
        this.currentUserProvider = currentUserProvider;
        this.readRecordManager = readRecordManager;
    }

    public DefaultTaskQueryService(ProcessHistoryTaskRepository historyTaskRepository,
                                   ActiveTaskRepository activeTaskRepository,
                                   ProcessInstanceRepository instanceRepository,
                                   ProcessTraceAssembler traceAssembler,
                                   RuntimeQueryAssembler queryAssembler,
                                   CurrentUserProvider currentUserProvider) {
        this(historyTaskRepository, activeTaskRepository, instanceRepository, traceAssembler, queryAssembler,
                currentUserProvider, null);
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
        for (ProcessInstanceEntity entity : instanceRepository.queryStartedInstances(normalized,
                currentUser.getUserId())) {
            records.add(queryAssembler.toProcessInstanceDTO(entity));
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
        for (ProcessHistoryTaskEntity entity : historyTaskRepository.findByInstanceId(instanceId)) {
            results.add(traceAssembler.toHistoryTaskDTO(entity));
        }
        return results;
    }

    @Override
    public List<ProcessCommentDTO> queryComments(String instanceId) {
        validateInstanceId(instanceId);
        List<ProcessCommentDTO> results = new ArrayList<ProcessCommentDTO>();
        for (ProcessHistoryTaskEntity entity : historyTaskRepository.findByInstanceId(instanceId)) {
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

    private void validateInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId must not be empty");
        }
    }

    private UserContext currentUser() {
        UserContext currentUser = currentUserProvider.getCurrentUser();
        if (currentUser == null || isBlank(currentUser.getUserId())) {
            throw new IllegalStateException("current user is required");
        }
        return currentUser;
    }

    private void applyTrustedTodoUser(TodoTaskQuery query, UserContext currentUser) {
        if (!isBlank(query.getUserId()) && !currentUser.getUserId().equals(query.getUserId())) {
            throw new IllegalArgumentException("query userId must match current user");
        }
        query.setUserId(currentUser.getUserId());
    }

    private void applyTrustedCompletedUser(CompletedTaskQuery query, UserContext currentUser) {
        if (!isBlank(query.getUserId()) && !currentUser.getUserId().equals(query.getUserId())) {
            throw new IllegalArgumentException("query userId must match current user");
        }
        query.setUserId(currentUser.getUserId());
    }

    private void applyTrustedStarterUser(StartedInstanceQuery query, UserContext currentUser) {
        if (!isBlank(query.getStarterUserId()) && !currentUser.getUserId().equals(query.getStarterUserId())) {
            throw new IllegalArgumentException("query starterUserId must match current user");
        }
        query.setStarterUserId(currentUser.getUserId());
    }

    private <T> PageResult<T> page(List<T> records, int pageNo, int pageSize, long total) {
        PageResult<T> result = new PageResult<T>();
        result.setRecords(records);
        result.setPageNo(Integer.valueOf(pageNo));
        result.setPageSize(Integer.valueOf(pageSize));
        result.setTotal(Long.valueOf(total));
        result.setTotalPages(Integer.valueOf((int) ((total + pageSize - 1) / pageSize)));
        return result;
    }

    private UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(methodName + " is not implemented in C M3");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
