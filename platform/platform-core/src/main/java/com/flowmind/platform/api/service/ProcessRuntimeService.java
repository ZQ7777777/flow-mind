package com.flowmind.platform.api.service;

import com.flowmind.platform.api.entity.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.entity.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.entity.result.TaskActionResult;
import com.flowmind.platform.api.entity.request.AddSignRequest;
import com.flowmind.platform.api.entity.request.ApproveTaskRequest;
import com.flowmind.platform.api.entity.request.ClaimTaskRequest;
import com.flowmind.platform.api.entity.request.DeleteProcessInstanceRequest;
import com.flowmind.platform.api.entity.request.DirectSendRequest;
import com.flowmind.platform.api.entity.request.RejectTaskRequest;
import com.flowmind.platform.api.entity.request.ReturnTaskRequest;
import com.flowmind.platform.api.entity.request.StartProcessRequest;
import com.flowmind.platform.api.entity.request.SubmitTaskRequest;
import com.flowmind.platform.api.entity.request.TerminateProcessRequest;
import com.flowmind.platform.api.entity.request.TransferTaskRequest;
import com.flowmind.platform.api.entity.request.UnclaimTaskRequest;
import com.flowmind.platform.api.entity.request.UpdateVariablesRequest;
import com.flowmind.platform.api.entity.request.WithdrawTaskRequest;

/**
 * 流程运行时服务。
 */
public interface ProcessRuntimeService {
    /**
     * 启动流程实例。
     *
     * @param request 启动请求
     * @return 流程实例概要
     */
    ProcessInstanceDTO startProcess(StartProcessRequest request);

    /**
     * 启动流程实例并提交首个任务。
     *
     * @param request 启动请求
     * @return 流程实例概要
     */
    ProcessInstanceDTO startAndSubmit(StartProcessRequest request);

    /**
     * 提交任务。
     *
     * @param request 提交请求
     * @return 任务动作结果
     */
    TaskActionResult submitTask(SubmitTaskRequest request);

    /**
     * 审批通过任务。
     *
     * @param request 审批请求
     * @return 任务动作结果
     */
    TaskActionResult approve(ApproveTaskRequest request);

    /**
     * 驳回任务。
     *
     * @param request 驳回请求
     * @return 任务动作结果
     */
    TaskActionResult reject(RejectTaskRequest request);

    /**
     * 退回发起人或首节点。
     *
     * @param request 退回请求
     * @return 任务动作结果
     */
    TaskActionResult returnToStarter(ReturnTaskRequest request);

    /**
     * 撤回任务。
     *
     * @param request 撤回请求
     * @return 任务动作结果
     */
    TaskActionResult withdraw(WithdrawTaskRequest request);

    /**
     * 直送到指定节点。
     *
     * @param request 直送请求
     * @return 任务动作结果
     */
    TaskActionResult directSend(DirectSendRequest request);

    /**
     * 转办任务。
     *
     * @param request 转办请求
     * @return 任务动作结果
     */
    TaskActionResult transfer(TransferTaskRequest request);

    /**
     * 加签任务。
     *
     * @param request 加签请求
     * @return 任务动作结果
     */
    TaskActionResult addSign(AddSignRequest request);

    /**
     * 认领任务。
     *
     * @param request 认领请求
     * @return 任务动作结果
     */
    TaskActionResult claim(ClaimTaskRequest request);

    /**
     * 取消认领任务。
     *
     * @param request 取消认领请求
     * @return 任务动作结果
     */
    TaskActionResult unclaim(UnclaimTaskRequest request);

    /**
     * 终止流程实例。
     *
     * @param request 终止请求
     * @return 流程实例概要
     */
    ProcessInstanceDTO terminate(TerminateProcessRequest request);

    /**
     * 删除流程实例。
     *
     * @param request 删除请求
     */
    void deleteInstance(DeleteProcessInstanceRequest request);

    /**
     * 更新流程变量。
     *
     * @param request 更新请求
     * @return 流程实例概要
     */
    ProcessInstanceDTO updateVariables(UpdateVariablesRequest request);

    /**
     * 查询流程实例详情。
     *
     * @param instanceId 流程实例 ID
     * @return 流程实例详情
     */
    ProcessInstanceDetailDTO getInstance(String instanceId);
}
