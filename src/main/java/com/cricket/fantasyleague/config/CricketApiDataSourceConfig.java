package com.cricket.fantasyleague.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Configures read-only JDBC access to the Cricket API database.
 *
 * The cricketapi DataSource is intentionally NOT exposed as a Spring bean --
 * only the JdbcTemplate is. This prevents Hibernate/JPA from discovering it
 * and running DDL (ddl-auto=update) against the cricketapi database.
 */
@Configuration
@EnableConfigurationProperties(CricketApiDataSourceProperties.class)
public class CricketApiDataSourceConfig {

    @Bean(name = "cricketApiJdbcTemplate")
    public NamedParameterJdbcTemplate cricketApiJdbcTemplate(CricketApiDataSourceProperties props) {
        HikariDataSource ds = DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .url(props.getUrl())
            .username(props.getUsername())
            .password(props.getPassword())
            .driverClassName(props.getDriverClassName())
            .build();

        ds.setReadOnly(true);
        ds.setPoolName("cricketapi-readonly");

        return new NamedParameterJdbcTemplate(ds);
    }
}
