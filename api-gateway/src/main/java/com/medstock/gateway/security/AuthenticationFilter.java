package com.medstock.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtValidator jwtValidator;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> OPEN_ENDPOINTS = List.of("/api/auth/login", "/api/auth/register");

    private static final String ADMIN_ROLE = "ADMIN";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isOpenEndpoint(request.getMethod(), path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = jwtValidator.validateAndExtractClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }

        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        Object branchIdClaim = claims.get("branchId");
        String branchId = branchIdClaim != null ? branchIdClaim.toString() : "";

        if (isAdminOnlyRoute(request.getMethod(), path) && !ADMIN_ROLE.equals(role)) {
            return onError(exchange, "Access denied: ADMIN role required", HttpStatus.FORBIDDEN);
        }

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Auth-Username", username)
                .header("X-Auth-Role", role)
                .header("X-Auth-BranchId", branchId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isOpenEndpoint(HttpMethod method, String path) {
        if (OPEN_ENDPOINTS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && pathMatcher.match("/api/branch/branches", path)) {
            return true;
        }
        return false;
    }

    private boolean isAdminOnlyRoute(HttpMethod method, String path) {
        boolean branchAdminRoute = pathMatcher.match("/api/branch/branches/**", path)
                && (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.DELETE.equals(method));

        boolean warehouseCodeRoute = pathMatcher.match("/api/auth/warehouse-codes", path)
                && HttpMethod.POST.equals(method);

        return branchAdminRoute || warehouseCodeRoute;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), message);

        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}