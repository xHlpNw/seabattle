package com.seabattle.server.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Exposes a DataSource that reports PostgreSQL version as 17.0 to Flyway,
 * so Flyway accepts PostgreSQL 17.8 (which it otherwise rejects).
 * Creates the real pool from spring.datasource.* and wraps it as @Primary.
 */
@Configuration
public class DataSourceConfig {

    @Bean("flywayRealDataSource")
    public DataSource flywayRealDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.driver-class-name:}") String driverClassName) {
        var builder = DataSourceBuilder.create();
        builder.url(url);
        if (username != null && !username.isEmpty()) builder.username(username);
        if (password != null && !password.isEmpty()) builder.password(password);
        if (driverClassName != null && !driverClassName.isEmpty()) builder.driverClassName(driverClassName);
        return builder.build();
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("flywayRealDataSource") DataSource flywayRealDataSource) {
        return FlywayPostgresVersionWorkaround.wrap(flywayRealDataSource);
    }
}
