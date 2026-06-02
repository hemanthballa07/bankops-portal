package com.bankops.portal.client.fluxguard;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-request fluxguard LOGIN rate-limit gate for the {@code /whoami} auth path. Runs
 * before Spring Security's HTTP Basic filter so a denied client is rejected with HTTP
 * 429 before any credential check happens.
 *
 * <p>Only the {@code /whoami} servlet path is gated; every other request passes straight
 * through. The gate is <b>fail-open</b>: {@code Allow}, {@code Unavailable} (fluxguard
 * outage), and {@code Disabled} all proceed — only an explicit {@code Denied} short
 * circuits with 429.
 */
@Component
public class WhoamiRateLimitFilter extends OncePerRequestFilter {

    private final FluxguardRateLimitClient client;

    public WhoamiRateLimitFilter(FluxguardRateLimitClient client) {
        this.client = client;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!"/whoami".equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Behind a proxy, derive the client IP from a trusted X-Forwarded-For header —
        // out of scope here, so we use the direct remote address.
        String clientIp = request.getRemoteAddr();
        FluxguardRateLimitOutcome outcome =
                client.checkLogin(UUID.randomUUID().toString(), clientIp);

        if (outcome instanceof FluxguardRateLimitOutcome.Denied d) {
            response.setStatus(429);
            response.setHeader("Retry-After",
                    String.valueOf((long) Math.ceil(d.retryAfterMs() / 1000.0)));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
