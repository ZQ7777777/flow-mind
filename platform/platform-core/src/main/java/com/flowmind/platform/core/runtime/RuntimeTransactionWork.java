package com.flowmind.platform.core.runtime;

/**
 * 运行时主事务中的业务回调。
 *
 * @param <T> 业务返回类型
 */
@FunctionalInterface
public interface RuntimeTransactionWork<T> {

    /** 执行需要与运行时状态原子提交的业务写入。 */
    T execute();
}
