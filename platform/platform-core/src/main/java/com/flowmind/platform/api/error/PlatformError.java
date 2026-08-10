package com.flowmind.platform.api.error;

/**
 * 平台同进程调用向宿主公开的稳定错误契约。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
public interface PlatformError {

    /**
     * 获取稳定错误码。
     *
     * @return 错误码
     */
    String getErrorCode();

    /**
     * 获取与传输协议无关的错误类别。
     *
     * @return 错误类别
     */
    PlatformErrorCategory getErrorCategory();
}
