package com.cricket.fantasyleague.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Read-only access to the config database. Only JdbcTemplate is exposed so JPA
 * never runs schema updates against this database.
 */
@Configuration
@EnableConfigurationProperties(FantasyConfigDataSourceProperties.class)
public class FantasyConfigDataSourceConfig {

    @Bean(name = "fantasyConfigJdbcTemplate")
    NamedParameterJdbcTemplate fantasyConfigJdbcTemplate(FantasyConfigDataSourceProperties props) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(props.getUrl())
                .username(props.getUsername())
                .password(props.getPassword())
                .driverClassName(props.getDriverClassName())
                .build();

        ds.setReadOnly(true);
        ds.setPoolName("fantasy-config-readonly");

        return new NamedParameterJdbcTemplate(ds);
    }
}
