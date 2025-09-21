package com.dauducbach.notification_service.configuration;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class TestConnect {
    private final ConnectionFactory connectionFactory;

    public TestConnect(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }


    @PostConstruct
    public void testConnection() {
        Mono.from(connectionFactory.create())
                .map(Connection::close)
                .doOnSuccess(unused -> log.info("✅ Kết nối thành công với MySQL qua R2DBC!"))
                .doOnError(error -> log.info("❌ Lỗi kết nối MySQL: " + error.getMessage()))
                .subscribe();
    }
}
