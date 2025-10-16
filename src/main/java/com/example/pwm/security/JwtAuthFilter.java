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
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;     // Preflight
        return path.startsWith("/api/auth/")
                || path.startsWith("/actuator")
                || path.startsWith("/error")
                || "/".equals(path);
    }

    // JwtAuthFilter.java
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String uri = request.getRequestURI();
        // Auth-Endpoints nie "vorab" per JWT blockieren
        if (uri.equals("/api/auth") || uri.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            final String token = header.substring("Bearer ".length()).trim();
            final java.util.UUID uid = jwt.parseUserId(token);
            var auth = new UsernamePasswordAuthenticationToken(uid, null, List.of());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (Exception ex) {
            // WICHTIG: nicht hart 401 schicken – weiterlaufen lassen,
            // damit die Security-Konfiguration entscheidet (und für public Endpoints nicht bricht)
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
        }
    }

}
