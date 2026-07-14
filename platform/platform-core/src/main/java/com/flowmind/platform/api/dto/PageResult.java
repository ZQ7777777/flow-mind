package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用分页返回对象，用于所有需要分页的查询接口。
 *
 * @param <T> 分页记录类型
 * @author Yuxin Xu
 * @created 2026-07-14
 */
@Data
public class PageResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 当前页记录列表。
     */
    private List<T> records = new ArrayList<T>();
    /**
     * 当前页码，从 1 开始。
     */
    private Integer pageNo;
    /**
     * 每页记录数。
     */
    private Integer pageSize;
    /**
     * 符合条件的总记录数。
     */
    private Long total;
    /**
     * 总页数。
     */
    private Integer totalPages;
}
