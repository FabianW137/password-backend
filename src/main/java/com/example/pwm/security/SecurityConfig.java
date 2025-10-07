package com.example.pwm.security;

import com.example.pwm.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())                          // CORS aktivieren (siehe Bean unten)
                .csrf(csrf -> csrf.disable())                             // stateless API
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // Preflight
                        .requestMatchers("/api/auth/**").permitAll()              // Login/Registrierung frei
                        .requestMatchers("/actuator/**", "/error").permitAll()    // optional
                        .anyRequest().authenticated()
                )
                // JWT-Filter vor UsernamePasswordAuthenticationFilter einschleifen
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** CORS-Quelle – hier die erlaubten Origins/Headers/Methoden definieren */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Deine public Frontend-URL:
        String frontend = "https://passwortmanager.onrender.com";

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
                frontend,
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
