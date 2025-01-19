package dev.shubham.labs.ecomm.config;

import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.instrumentation.jdbc.datasource.OpenTelemetryDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

import java.sql.SQLException;
import java.util.Map;

@Configuration
@Slf4j
class VaultRefresher {

    @Bean
    public ApplicationListener<ApplicationReadyEvent> vaultLeaseListener(SecretLeaseContainer leaseContainer,
                                                                         ApplicationContext applicationContext,
                                                                         ContextRefresher contextRefresher,
                                                                         @Value("${spring.cloud.vault.postgresql.role}")
                                                                         String databaseRole) {
        return applicationReadyEvent -> {
            {
                String path = "database/creds/" + databaseRole;
                leaseContainer.addLeaseListener(event -> {
                    log.info("==> Received event: {}", event);
                    if (!path.equals(event.getSource().getPath())) {
                        return;
                    }

                    if (event instanceof SecretLeaseExpiredEvent) {
                        log.info("Replace RENEW for expired credential with ROTATE");
                        contextRefresher.refresh();
                    }

                    if (event instanceof SecretLeaseCreatedEvent secretLeaseCreatedEvent) {

                        Map<String, Object> secrets = secretLeaseCreatedEvent.getSecrets();
                        String username = (String) secrets.get("username");
                        String password = (String) secrets.get("password");
                        log.info("XXXXX New username = {}", username);
                        HikariDataSource hikariDataSource = null;
                        var dataSourceBean = applicationContext.getBean("dataSource");
                        if(dataSourceBean instanceof OpenTelemetryDataSource dataSource) {
                            try {
                                hikariDataSource = dataSource.unwrap(HikariDataSource.class);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        } else if (dataSourceBean instanceof HikariDataSource dataSource) {
                            hikariDataSource = dataSource;
                        }
                        if(hikariDataSource != null) {
                            hikariDataSource.getHikariConfigMXBean().setUsername(username);
                            hikariDataSource.getHikariConfigMXBean().setPassword(password);
                            log.info("Soft evicting db connections...");
                            hikariDataSource.getHikariPoolMXBean().softEvictConnections();
                        }

                    }
                });
            }
        };
    }
}
