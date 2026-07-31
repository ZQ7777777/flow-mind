package com.flowmind.platform.api.service;

import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.request.CheckAttachmentRequest;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.DownloadAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.request.ReplaceInstanceAttachmentRequest;

import java.util.List;

/**
 * 附件服务。
 */
public interface AttachmentService {
    /**
     * 保存实例级附件。
     *
     * @param request 保存请求
     * @return 附件元数据
     */
    AttachmentDTO saveInstanceAttachment(SaveInstanceAttachmentRequest request);

    /**
     * 保存任务级附件。
     *
     * @param request 保存请求
     * @return 附件元数据
     */
    AttachmentDTO saveTaskAttachment(SaveTaskAttachmentRequest request);

    /**
     * 使用当前申请任务替换已有实例级附件。
     *
     * @param request 替换请求
     * @return 新附件元数据
     */
    AttachmentDTO replaceInstanceAttachment(ReplaceInstanceAttachmentRequest request);

    /**
     * 下载附件。
     *
     * @param request 下载请求
     * @return 附件下载结果
     */
    AttachmentDownloadDTO downloadAttachment(DownloadAttachmentRequest request);

    /**
     * 查询附件列表。
     *
     * @param query 查询条件
     * @return 附件元数据列表
     */
    List<AttachmentDTO> queryAttachments(AttachmentQuery query);

    /**
     * 删除附件。
     *
     * @param request 删除请求
     */
    void deleteAttachment(DeleteAttachmentRequest request);

    /**
     * 校验指定节点的必传附件。
     *
     * @param request 校验请求
     * @return 附件模板校验结果
     */
    AttachmentTemplateCheckResult checkRequiredAttachments(CheckAttachmentRequest request);
}
