package com.example.pwm.security;

import com.example.pwm.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    private final JwtService jwt;  // per Constructor-Injection

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            // optional, schadet aber nicht:
            res.setStatus(HttpServletResponse.SC_OK);
            chain.doFilter(req, res);
            return;
        }
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            try {
                UUID uid = jwt.requireUid(token);            // liest/prüft Subject (UUID)
                var auth = new UsernamePasswordAuthenticationToken(uid.toString(), null, List.of());
                auth.setDetails(uid);                        // optional
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (RuntimeException e) {                   // z.B. JwtException, IllegalArgumentException
                SecurityContextHolder.clearContext();        // ungültiges Token -> kein Login
            }
        }

        chain.doFilter(req, res);
    }
}


