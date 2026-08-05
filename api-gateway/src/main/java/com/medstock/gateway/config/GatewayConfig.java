package com.medstock.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("branch-service", r -> r.path("/api/branch/**")
                        .uri("lb://branch-service"))
                .route("auth-service", r -> r.path("/api/auth/**")
                        .uri("lb://auth-service"))
                .route("inventory-service", r -> r.path("/api/inventory/**")
                        .uri("lb://inventory-service"))
                .route("transfer-service", r -> r.path("/api/transfer/**")
                        .uri("lb://transfer-service"))
                .route("alert-service", r -> r.path("/api/alert/**")
                        .uri("lb://alert-service"))
                .build();
    }
}
