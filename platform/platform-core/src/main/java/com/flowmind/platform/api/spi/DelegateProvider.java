package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.dto.DelegateRelationDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 委托关系 SPI。
 */
public interface DelegateProvider {
    /**
     * 查询指定时刻有效的委托关系。
     *
     * @param principalUserId 委托人用户 ID
     * @param at 查询时刻
     * @return 委托关系列表
     */
    List<DelegateRelationDTO> findDelegates(String principalUserId, LocalDateTime at);
}
