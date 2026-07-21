package com.flowmind.platform.testsupport;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Test DataSource that reuses one existing connection and ignores close calls
 * made by JdbcTemplate.
 */
public class ExistingConnectionDataSource implements DataSource {

    private final Connection connection;
    private final Connection closeSuppressingConnection;
    private PrintWriter logWriter;
    private int loginTimeout;

    public ExistingConnectionDataSource(Connection connection) {
        this.connection = connection;
        this.closeSuppressingConnection = createCloseSuppressingConnection(connection);
    }

    @Override
    public Connection getConnection() {
        return closeSuppressingConnection;
    }

    @Override
    public Connection getConnection(String username, String password) {
        return closeSuppressingConnection;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter logWriter) {
        this.logWriter = logWriter;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Parent logger is not supported.");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        if (iface.isInstance(connection)) {
            return iface.cast(connection);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this) || iface.isInstance(connection);
    }

    private Connection createCloseSuppressingConnection(Connection target) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    return null;
                }
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getTargetException();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler);
    }
}
