package com.bankops.portal.client.fluxguard;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP Basic authentication entry point that reports failed {@code /whoami} login
 * attempts to fluxguard (best-effort) before delegating to the standard
 * {@link BasicAuthenticationEntryPoint} so the exact 401 + {@code WWW-Authenticate}
 * response is preserved.
 *
 * <p>Only failures on the {@code /whoami} servlet path are reported; the failure report
 * lets fluxguard count bad-credential attempts toward its LOGIN policy.
 */
@Component
public class LoginFailureAuthEntryPoint implements AuthenticationEntryPoint {

    private final FluxguardRateLimitClient client;
    private final BasicAuthenticationEntryPoint delegate;

    public LoginFailureAuthEntryPoint(FluxguardRateLimitClient client) {
        this.client = client;
        this.delegate = new BasicAuthenticationEntryPoint();
        this.delegate.setRealmName("bankops");
        this.delegate.afterPropertiesSet();
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        if ("/whoami".equals(request.getServletPath())) {
            client.reportLoginFailure(UUID.randomUUID().toString(), request.getRemoteAddr());
        }
        delegate.commence(request, response, authException);
    }
}
