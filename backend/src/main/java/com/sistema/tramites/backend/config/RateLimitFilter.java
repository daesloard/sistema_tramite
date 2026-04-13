package com.sistema.tramites.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, RateWindow> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong(0L);

    @Value("${app.ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${app.ratelimit.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.ratelimit.default-per-window:120}")
    private int defaultPerWindow;

    @Value("${app.ratelimit.auth-login-per-window:10}")
    private int authLoginPerWindow;

    @Value("${app.ratelimit.verificacion-per-window:30}")
    private int verificacionPerWindow;

    @Value("${app.ratelimit.radicacion-per-window:20}")
    private int radicacionPerWindow;

    @Value("${app.ratelimit.cleanup-interval-seconds:300}")
    private long cleanupIntervalSeconds;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }

        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = resolveLimit(path);
        String routeKey = resolveRouteKey(path);
        String clientIp = resolveClientIp(request);
        long now = System.currentTimeMillis();

        Decision decision = consumeToken(routeKey, clientIp, limit, now);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(decision.remaining(), 0)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));

        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Demasiadas solicitudes. Intenta de nuevo en unos segundos.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Decision consumeToken(String routeKey, String clientIp, int limit, long nowMillis) {
        long windowMillis = Math.max(1L, windowSeconds) * 1000L;
        String key = routeKey + "|" + clientIp;
        RateWindow window = buckets.computeIfAbsent(key, unused -> new RateWindow(nowMillis));

        int remaining;
        long resetEpochSeconds;
        long retryAfterSeconds;
        boolean allowed;

        synchronized (window) {
            if ((nowMillis - window.windowStartMillis) >= windowMillis) {
                window.windowStartMillis = nowMillis;
                window.count.set(0);
            }

            int used = window.count.incrementAndGet();
            window.lastSeenMillis = nowMillis;

            allowed = used <= limit;
            remaining = Math.max(limit - used, 0);
            long millisToReset = Math.max(windowMillis - (nowMillis - window.windowStartMillis), 0);
            retryAfterSeconds = Math.max(1L, (long) Math.ceil(millisToReset / 1000.0));
            resetEpochSeconds = Instant.ofEpochMilli(nowMillis + millisToReset).getEpochSecond();
        }

        cleanupIfNeeded(nowMillis, windowMillis);
        return new Decision(allowed, remaining, retryAfterSeconds, resetEpochSeconds);
    }

    private void cleanupIfNeeded(long nowMillis, long windowMillis) {
        long intervalMillis = Math.max(1L, cleanupIntervalSeconds) * 1000L;
        long previous = lastCleanupMillis.get();
        if ((nowMillis - previous) < intervalMillis || !lastCleanupMillis.compareAndSet(previous, nowMillis)) {
            return;
        }

        long maxIdle = windowMillis * 3;
        buckets.entrySet().removeIf(entry -> (nowMillis - entry.getValue().lastSeenMillis) > maxIdle);
    }

    private int resolveLimit(String path) {
        if (path == null) {
            return defaultPerWindow;
        }

        if ("/api/auth/login".equals(path)) {
            return authLoginPerWindow;
        }

        if (path.startsWith("/api/tramites/verificacion")) {
            return verificacionPerWindow;
        }

        if ("/api/tramites/solicitud-residencia".equals(path)) {
            return radicacionPerWindow;
        }

        return defaultPerWindow;
    }

    private String resolveRouteKey(String path) {
        if (path == null) {
            return "default";
        }

        if ("/api/auth/login".equals(path)) {
            return "auth-login";
        }

        if (path.startsWith("/api/tramites/verificacion")) {
            return "certificado-verificacion";
        }

        if ("/api/tramites/solicitud-residencia".equals(path)) {
            return "tramite-radicacion";
        }

        return "default";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String cfConnectingIp = cleanIp(request.getHeader("CF-Connecting-IP"));
        if (cfConnectingIp != null) {
            return cfConnectingIp;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String first = xForwardedFor.split(",")[0];
            String clean = cleanIp(first);
            if (clean != null) {
                return clean;
            }
        }

        String xRealIp = cleanIp(request.getHeader("X-Real-IP"));
        if (xRealIp != null) {
            return xRealIp;
        }

        String remoteAddr = cleanIp(request.getRemoteAddr());
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private String cleanIp(String value) {
        if (value == null) {
            return null;
        }

        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private static final class RateWindow {
        private long windowStartMillis;
        private final AtomicInteger count;
        private long lastSeenMillis;

        private RateWindow(long nowMillis) {
            this.windowStartMillis = nowMillis;
            this.lastSeenMillis = nowMillis;
            this.count = new AtomicInteger(0);
        }
    }

    private record Decision(boolean allowed, int remaining, long retryAfterSeconds, long resetEpochSeconds) {
    }
}