package com.celpip.practice.imports;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.*;

@Configuration
class DatabaseConfiguration {
    @Bean
    HikariDataSource dataSource(LocalStorage storage) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:file:" + storage.root.resolve("database/celpip"));
        ds.setUsername("sa"); ds.setPassword(""); ds.setMaximumPoolSize(4);
        return ds;
    }
}
