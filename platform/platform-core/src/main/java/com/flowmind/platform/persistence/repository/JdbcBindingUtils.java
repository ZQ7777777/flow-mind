package com.flowmind.platform.persistence.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * JDBC 参数绑定工具，统一处理 SQLite 数字型布尔值和可空数值字段。
 *
 * @author Yuxin Xu
 * @since 2026-07-17
 */
final class JdbcBindingUtils {

    private JdbcBindingUtils() {
    }

    /**
     * 绑定可空布尔参数。
     *
     * @param ps    当前批处理或单条 SQL 使用的 PreparedStatement
     * @param index JDBC 参数序号，从 1 开始，对应 SQL 中第 index 个占位符
     * @param value 业务布尔值；null 交给 SQL COALESCE 或数据库空值语义处理，true/false 写入 SQLite 数字 1/0
     */
    static void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setInt(index, Boolean.TRUE.equals(value) ? 1 : 0);
        }
    }

    /**
     * 绑定可空整数参数。
     *
     * @param ps    当前批处理或单条 SQL 使用的 PreparedStatement
     * @param index JDBC 参数序号，从 1 开始，对应 SQL 中第 index 个占位符
     * @param value 业务整数值；null 交给 SQL COALESCE 或数据库空值语义处理
     */
    static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setInt(index, value.intValue());
        }
    }

    /**
     * 绑定可空浮点参数。
     *
     * @param ps    当前批处理或单条 SQL 使用的 PreparedStatement
     * @param index JDBC 参数序号，从 1 开始，对应 SQL 中第 index 个占位符
     * @param value 业务浮点值；null 保持为空，非空时按 double 写入
     */
    static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setDouble(index, value.doubleValue());
        }
    }
}
