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
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * M2 任务查询服务实现。当前阶段实现历史轨迹、审批意见和已办分页。
 */
@Service
public class DefaultTaskQueryService implements TaskQueryService {

    private final ProcessHistoryTaskRepository historyTaskRepository;
    private final ProcessTraceAssembler traceAssembler;

    public DefaultTaskQueryService(ProcessHistoryTaskRepository historyTaskRepository,
                                   ProcessTraceAssembler traceAssembler) {
        this.historyTaskRepository = historyTaskRepository;
        this.traceAssembler = traceAssembler;
    }

    @Override
    public PageResult<TaskDTO> queryTodoTasks(TodoTaskQuery query) {
        throw unsupported("queryTodoTasks");
    }

    @Override
    public PageResult<HistoryTaskDTO> queryCompletedTasks(CompletedTaskQuery query) {
        CompletedTaskQuery normalized = query == null ? new CompletedTaskQuery() : query;
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<HistoryTaskDTO> records = new ArrayList<HistoryTaskDTO>();
        for (ProcessHistoryTaskEntity entity : historyTaskRepository.queryCompletedTasks(normalized)) {
            records.add(traceAssembler.toHistoryTaskDTO(entity));
        }
        return page(records, pageNo, pageSize, historyTaskRepository.countCompletedTasks(normalized));
    }

    @Override
    public PageResult<ProcessInstanceDTO> queryStartedInstances(StartedInstanceQuery query) {
        throw unsupported("queryStartedInstances");
    }

    @Override
    public List<TaskDTO> queryActiveTasks(String instanceId) {
        throw unsupported("queryActiveTasks");
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
        throw unsupported("queryReadRecords");
    }

    private void validateInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId must not be empty");
        }
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
        return new UnsupportedOperationException(methodName + " is not implemented in C M2");
    }
}
