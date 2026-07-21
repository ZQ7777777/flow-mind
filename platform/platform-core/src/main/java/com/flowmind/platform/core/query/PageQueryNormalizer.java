package com.flowmind.platform.core.query;

import com.flowmind.platform.api.dto.PageQuery;

/**
 * 分页查询参数归一化工具。
 *
 * @author Yuxin Xu
 * @since 2026-07-20
 */
public final class PageQueryNormalizer {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PageQueryNormalizer() {
    }

    /**
     * 归一化分页对象本身。
     *
     * @param query 调用方传入的分页查询对象；为 null 时创建只包含默认分页参数的新对象
     * @return 原对象或新建对象，且 pageNo/pageSize 已被修正到平台允许范围内
     */
    public static PageQuery normalize(PageQuery query) {
        PageQuery normalized = query == null ? new PageQuery() : query;
        normalized.setPageNo(Integer.valueOf(normalizePageNo(normalized.getPageNo())));
        normalized.setPageSize(Integer.valueOf(normalizePageSize(normalized.getPageSize())));
        return normalized;
    }

    /**
     * 归一化页码。
     *
     * @param pageNo 请求页码；为空或小于 1 时按第一页处理
     * @return 可直接用于分页查询的页码
     */
    public static int normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo.intValue() < 1) {
            return DEFAULT_PAGE_NO;
        }
        return pageNo.intValue();
    }

    /**
     * 归一化页大小。
     *
     * @param pageSize 请求页大小；为空或小于 1 时使用默认值，超过上限时截断
     * @return 可直接用于分页查询的页大小
     */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize.intValue() < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize.intValue(), MAX_PAGE_SIZE);
    }
}
