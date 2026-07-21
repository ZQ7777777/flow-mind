package com.flowmind.platform.api.dto;

import lombok.Data;

/**
 * 分页查询公共参数。
 *
 * @author Yuxin Xu
 * @since 2026-07-20
 */
@Data
public class PageQuery {

    /**
     * 当前页码，从 1 开始；为空或小于 1 时按默认第一页处理。
     */
    private Integer pageNo;

    /**
     * 每页条数；为空或小于 1 时按默认页大小处理，超过平台上限时截断到上限。
     */
    private Integer pageSize;

    /**
     * 分页基类只承载公共字段，保留各业务查询对象原有的对象标识相等语义。
     */
    @Override
    public final boolean equals(Object other) {
        return super.equals(other);
    }

    /**
     * 分页基类只承载公共字段，保留各业务查询对象原有的对象标识哈希语义。
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }
}
