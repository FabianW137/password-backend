package com.example.pwm.security;

import com.example.pwm.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    /** Endpunkte/Methoden, für die der Filter nicht laufen soll (z. B. /api/auth/** und OPTIONS) */
    // JwtAuthFilter.java
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        String m = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(m)) return true; // CORS Preflight

        // Nur die öffentlichen Auth-Endpunkte vom Filter ausnehmen
        return p.equals("/api/auth/register")
                || p.equals("/api/auth/login")
                || p.equals("/api/auth/totp-verify")
                || p.equals("/api/auth/ping")
                || p.startsWith("/oauth2/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String uri = request.getRequestURI();

        // Spiegelbildlich dieselbe Whitelist – NICHT pauschal /api/auth/**
        if (uri.equals("/api/auth/register")
                || uri.equals("/api/auth/login")
                || uri.equals("/api/auth/totp-verify")
                || uri.equals("/api/auth/ping")
                || uri.startsWith("/oauth2/")) {
            chain.doFilter(request, response);
            return;
        }

        final String header = request.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            final String token = header.substring("Bearer ".length()).trim();
            final java.util.UUID uid = jwt.parseUserId(token); // prüft Signatur & exp
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(uid, null, java.util.List.of());
            auth.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (Exception ex) {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
        }
    }


}
