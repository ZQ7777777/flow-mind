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

    /**
     * 查询指定代理人在指定时刻可代办的委托关系。
     *
     * <p>默认实现兼容早期本地 Mock；生产宿主应覆盖该方法，按代理人用户 ID 返回有效委托人列表。</p>
     *
     * @param delegateUserId 代理人用户 ID
     * @param at 查询时刻
     * @return 委托关系列表
     */
    default List<DelegateRelationDTO> findPrincipals(String delegateUserId, LocalDateTime at) {
        return findDelegates(delegateUserId, at);
    }
}
