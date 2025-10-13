package com.example.pwm.security;


import com.example.pwm.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Configuration
public class SecurityConfig {

    /** BCrypt für Passwörter */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Unser JWT-Filter */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService);
    }

    /** Security-HTTP-Konfiguration */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Token-basierte API -> kein CSRF, keine Session
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Autorisierung: Vault-API braucht Auth, Rest frei
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/vault/**").authenticated()
                        .anyRequest().permitAll()
                )

                // JWT Resource Server mit benutzerdefiniertem Principal (email -> sub Fallback)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(this::jwtToAuthToken))
                );

        return http.build();

    }
@Bean
    private JwtAuthenticationToken jwtToAuthToken(Jwt jwt) {
        // Authorities aus Standard-Scopes
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("SCOPE_");           // z. B. SCOPE_read
        scopes.setAuthoritiesClaimName("scope");       // nutzt intern auch "scp" als Fallback
        Collection<GrantedAuthority> authorities = scopes.convert(jwt);

        // Principal: email bevorzugt, sonst sub
        String principal = Optional.ofNullable(jwt.getClaimAsString("email"))
                .filter(s -> !s.isBlank())
                .orElseGet(jwt::getSubject);

        return new JwtAuthenticationToken(jwt, Objects.requireNonNullElse(authorities, List.of()), principal);
    }

        /** CORS-Quelle – hier die erlaubten Origins/Headers/Methoden definieren */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Deine public Frontend-URL:
        String frontend = "https://passwortmanager.onrender.com";
        String gateway = "https://password-graphql.onrender.com";

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
                frontend,
                gateway,
                "http://localhost:5173",
                "http://localhost:4200"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"
        ));
        // Wir verwenden Bearer-Token im Header, keine Cookies:
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        // Optional: falls das Frontend Header zurücklesen muss (meist nicht nötig)
        // cfg.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Für alle Pfade anwenden (alternativ "/api/**")
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
