package dev.shubham.labs.ecomm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

//    @Bean
//    @RefreshScope
//    DataSource dataSource(DataSourceProperties properties) {
//        var db = DataSourceBuilder
//                .create()
//                .url(properties.getUrl())
//                .username(properties.getUsername())
//                .password(properties.getPassword())
//                .build();
//        log.info(
//                "rebuild data source: " +
//                        properties.getUsername() +
//                        ',' + properties.getPassword()
//        );
//        return db;
//    }

//    @Bean
//    @RefreshScope
//    public HikariDataSource createNewDataSource(DataSourceProperties properties) {
//        HikariConfig config = new HikariConfig();
//        config.setJdbcUrl(properties.getUrl());
//        config.setUsername(properties.getUsername());
//        config.setPassword(properties.getPassword());
//        log.info(
//                "rebuild data source: " +
//                        properties.getUsername() +
//                        ',' + properties.getPassword()
//        );
//        return new HikariDataSource(config);
//    }


}
