package com.seabattle.server.config;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Workaround for Flyway not recognizing PostgreSQL 17.8.
 * Wraps the DataSource so that {@link DatabaseMetaData#getDatabaseProductVersion()}
 * returns "17.0" instead of "17.8". All other behaviour is delegated.
 */
public final class FlywayPostgresVersionWorkaround {

    private static final String MASKED_VERSION = "17.0";

    private FlywayPostgresVersionWorkaround() {}

    public static DataSource wrap(DataSource target) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                (proxy, method, args) -> {
                    Object result = method.invoke(target, args);
                    if ("getConnection".equals(method.getName()) && result instanceof Connection) {
                        return wrapConnection((Connection) result);
                    }
                    return result;
                });
    }

    private static Connection wrapConnection(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    Object result = method.invoke(target, args);
                    if ("getMetaData".equals(method.getName()) && result instanceof DatabaseMetaData) {
                        return wrapDatabaseMetaData((DatabaseMetaData) result);
                    }
                    return result;
                });
    }

    private static DatabaseMetaData wrapDatabaseMetaData(DatabaseMetaData target) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class },
                (proxy, method, args) -> {
                    Object result = method.invoke(target, args);
                    if ("getDatabaseProductVersion".equals(method.getName()) && result instanceof String) {
                        String v = (String) result;
                        if (v != null && v.startsWith("17.")) return MASKED_VERSION;
                    }
                    return result;
                });
    }
}
