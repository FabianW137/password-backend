package com.example.pwm.security;

import com.example.pwm.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {

        String path = req.getRequestURI();
        String method = req.getMethod();

        // 1) Öffentliche Routen & Preflight NICHT anfassen
        if ("OPTIONS".equalsIgnoreCase(method)
                || path.startsWith("/api/auth/")
                || "/".equals(path)
                || path.startsWith("/actuator")) {
            chain.doFilter(req, res);
            return;
        }

        // 2) Kein/anderer Auth-Header -> einfach weiter (wird durch Security später 401/403)
        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (h == null || !h.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        // 3) Token prüfen, bei Fehler 401 zurückgeben
        String token = h.substring(7);
        try {
            UUID uid = jwt.requireUid(token);  // wirft bei ungültig/abgelaufen
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(uid.toString(), null, List.of());
            auth.setDetails(uid); // du liest die UUID später aus getDetails()
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(req, res);
        } catch (Exception ex) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            // optional: res.getWriter().write("Invalid token");
        }
    }
}



