package com.cloud.filter;

import com.cloud.config.JwtUtil;
import com.cloud.config.TokenHasher;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ReactiveStringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (path.startsWith("/auth")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = jwtUtil.extractAllClaims(token);
        } catch (JwtException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Signature/expiry are valid at this point, but the token may have been explicitly revoked
        // via /auth/logout - checked against the same Redis blocklist entry, matched by the
        // identical TokenHasher logic duplicated in user-service (which writes it).
        return redisTemplate.hasKey(TokenHasher.revokedTokenKey(token))
                .flatMap(revoked -> {
                    if (Boolean.TRUE.equals(revoked)) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                            .header("X-User-Id", String.valueOf(claims.get("userId")))
                            .header("X-User-Email", claims.getSubject())
                            .header("X-User-Roles", String.valueOf(claims.get("role")));

                    // Optional - a user who signed up without a phone number has no "phoneNumber"
                    // claim at all, so this header is only forwarded when one genuinely exists,
                    // matching the same optionality already established for the entity/DTO field.
                    Object phoneClaim = claims.get("phoneNumber");
                    if (phoneClaim != null) {
                        requestBuilder.header("X-User-Phone", String.valueOf(phoneClaim));
                    }

                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }
}