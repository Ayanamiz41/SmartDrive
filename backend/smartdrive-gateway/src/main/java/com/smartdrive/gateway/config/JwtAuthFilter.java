package com.smartdrive.gateway.config;

import com.smartdrive.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BLACKLIST_PREFIX = "smartdrive:jwt:blacklist:";
    private static final String SESSION_PREFIX = "smartdrive:session:";

    // 无需认证的公开路径
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
        "/api/auth/checkCode",
        "/api/auth/sendEmailCode",
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/resetPwd",
        "/api/auth/refresh",
        "/api/auth/getAvatar",
        "/api/file/getImage/",
        "/api/file/download/",
        "/api/file/ts/",
        "/api/admin/download/",
        "/api/showShare/getShareInfo",
        "/api/showShare/getShareLoginInfo",
        "/api/showShare/checkShareCode",
        "/api/showShare/loadFileList",
        "/api/showShare/getFolderInfo",
        "/api/showShare/getFile/",
        "/api/showShare/ts/",
        "/api/showShare/createDownloadUrl/",
        "/api/showShare/download/"
    );

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthFilter(JwtUtil jwtUtil, StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 公开路径：不强制认证，但携带有效 token 时仍注入用户头（可选登录态）
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return chain.filter(exchange.mutate().request(buildPublicRequest(exchange)).build());
            }
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.parseToken(token);

            // JWT 黑名单：封禁/改密码后，签发时间早于黑名单时间戳的 token 全部拒绝
            String blacklistKey = BLACKLIST_PREFIX + claims.getSubject();
            String bannedAt = redisTemplate.opsForValue().get(blacklistKey);
            if (bannedAt != null) {
                long banSeconds = Long.parseLong(bannedAt);
                long iatSeconds = claims.getIssuedAt().toInstant().getEpochSecond();
                if (iatSeconds < banSeconds) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }

            // 会话校验：防多地登录互踢（存量无 sessionId 的 token 放行）
            String sessionId = claims.get("sessionId", String.class);
            if (sessionId != null && !sessionId.isEmpty()) {
                String currentSessionId = redisTemplate.opsForValue().get(SESSION_PREFIX + claims.getSubject());
                if (currentSessionId != null && !currentSessionId.equals(sessionId)) {
                    return writeKickedOutResponse(exchange);
                }
            }

            ServerHttpRequest modifiedRequest = injectUserHeaders(exchange.getRequest().mutate(), claims).build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    /** 公开路径请求：剥离伪造的 X-User-* 头；token 有效则注入真实用户头，无效/缺失按游客放行 */
    private ServerHttpRequest buildPublicRequest(ServerWebExchange exchange) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
            .headers(h -> {
                h.remove("X-User-Id");
                h.remove("X-User-Role");
                h.remove("X-User-NickName");
                h.remove("X-User-DepartmentId");
            });
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                injectUserHeaders(builder, jwtUtil.parseToken(authHeader.substring(7)));
            } catch (Exception ignored) {
                // token 无效按未登录处理，公开路径不拦截
            }
        }
        return builder.build();
    }

    private ServerHttpRequest.Builder injectUserHeaders(ServerHttpRequest.Builder builder, Claims claims) {
        String nickName = claims.get("nickName", String.class);
        String departmentId = claims.get("departmentId", String.class);
        return builder
            .header("X-User-Id", claims.getSubject())
            .header("X-User-Role", Boolean.TRUE.equals(claims.get("admin", Boolean.class)) ? "admin" : "user")
            .header("X-User-NickName", nickName != null ? nickName : "")
            .header("X-User-DepartmentId", departmentId != null ? departmentId : "");
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private Mono<Void> writeKickedOutResponse(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":902,\"info\":\"账号已在别处登录\"}";
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }
}
