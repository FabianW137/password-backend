package com.example.pwm.security;

import com.example.pwm.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
    @Bean public JwtService jwtService(){ return new JwtService(); }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/health").permitAll()
                .anyRequest().authenticated()
            )
            .cors(Customizer.withDefaults());
        http.addFilterBefore(new JwtAuthFilter(jwtService()), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    static class JwtAuthFilter extends org.springframework.web.filter.OncePerRequestFilter {
        private final JwtService jwt;
        JwtAuthFilter(JwtService jwt){ this.jwt = jwt; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                try {
                    Claims c = jwt.parse(token);
                    if ("access".equals(c.get("type"))) {
                        var principal = new User(c.get("email", String.class), "", List.of());
                        var authToken = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                        authToken.setDetails(Long.valueOf(c.getSubject()));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } catch (Exception ignored){}
            }
            chain.doFilter(request, response);
        }
    }
}
