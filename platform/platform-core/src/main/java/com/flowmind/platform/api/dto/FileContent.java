package com.flowmind.platform.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件存储 SPI 返回的文件元数据与二进制内容。
 *
 * @author FlowMind
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileContent {

    /** 文件存储键。 */
    private String storageKey;
    /** 原始文件名。 */
    private String fileName;
    /** 文件 MIME 内容类型。 */
    private String contentType;
    /** 文件大小，单位字节。 */
    private Long sizeBytes;
    /** 文件二进制内容。 */
    private byte[] content;

}
