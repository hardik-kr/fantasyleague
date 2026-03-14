package com.cricket.fantasyleague.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@EnableConfigurationProperties(CricketApiDataSourceProperties.class)
public class CricketApiDataSourceConfig {

    @Bean(name = "cricketApiDataSource")
    public DataSource cricketApiDataSource(CricketApiDataSourceProperties props) {
        return DataSourceBuilder.create()
            .url(props.getUrl())
            .username(props.getUsername())
            .password(props.getPassword())
            .driverClassName(props.getDriverClassName())
            .build();
    }

    @Bean(name = "cricketApiJdbcTemplate")
    public NamedParameterJdbcTemplate cricketApiJdbcTemplate(
            @Qualifier("cricketApiDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
