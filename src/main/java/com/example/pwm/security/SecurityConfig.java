package com.example.pwm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
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
@EnableWebSecurity
public class SecurityConfig {

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

    /**
     * Wandelt ein JWT in ein Authentication-Objekt:
     * - Principal: bevorzugt "email", sonst "sub"
     * - Authorities: aus "scope"/"scp" zu "SCOPE_*"
     */
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

    /**
     * CORS für Browser-Calls (Render, verschiedene Origins).
     * Wenn du feste Domains hast, trage sie statt "*" in setAllowedOriginPatterns(...) ein.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*")); // für Produktion besser spezifische Origins setzen
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cfg.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
