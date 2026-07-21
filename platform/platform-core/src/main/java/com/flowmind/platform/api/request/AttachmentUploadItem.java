package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import lombok.Data;

/**
 * 单个附件上传项，作为附件保存请求或任务提交请求的嵌套输入。
 *
 * @author FlowMind
 * @since 1.0.0
 */
@Data
public class AttachmentUploadItem {

    /** 附件模板编码，在实例绑定的附件配置范围内唯一。 */
    private String attachmentCode;
    /** 附件归属范围，用于区分实例级与任务级附件。 */
    private AttachmentOwnerTypeEnum ownerType;
    /** 调用方上传时提供的原始文件名。 */
    private String fileName;
    /** 文件的 MIME 内容类型。 */
    private String contentType;
    /** 文件内容的字节数。 */
    private Long sizeBytes;
    /** 上传文件的二进制内容；后续由文件存储 SPI 保存。 */
    private byte[] content;

}
