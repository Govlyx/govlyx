package com.govlyx.AI.security;

import com.govlyx.AI.security.CustomUserDetailsService;
import com.govlyx.AI.security.JwtAuthenticationEntryPoint;
import com.govlyx.AI.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new org.springframework.security.authentication.ProviderManager(daoAuthenticationProvider());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz

                        // ── CORS preflight: OPTIONS must bypass JWT filter entirely ──────────────
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ── Static uploads (FIX: moved here from WebSecurityCustomizer.ignoring()) ──
                        .requestMatchers("/uploads/**").permitAll()

                        // ── Auth & Public ─────────────────────────────────────────────────────────
                        .requestMatchers("/robots.txt").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/webhooks/**").permitAll() // Industry Standard: Open webhook namespace
                        .requestMatchers("/api/search/posts/anonymous").permitAll()
                        .requestMatchers("/api/districts/**").permitAll()
                        .requestMatchers("/api/media/test").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/copyright-claims").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/copyright-claims/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sidebar").permitAll()

                        // ── WebSocket ─────────────────────────────────────────────────────────────
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/topic/**").permitAll()
                        .requestMatchers("/app/**").permitAll()

                        // ── Public GET endpoints ──────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,  "/api/posts/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/posts/validate-tags").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/users/search").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/users/{userId}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recommendations/posts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/feedback").permitAll()

                        // ── Communities, Social & Polls ───────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,  "/api/communities/me").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/communities/owned").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/communities/count/me").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/communities/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/social-posts/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/polls/**").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/v1/feed/**").permitAll()

                        // ── Admin-only ────────────────────────────────────────────────────────────
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users/active").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users/by-location/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/{userId}/deactivate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/{userId}/activate").hasRole("ADMIN")

                        // ── Authenticated endpoints ───────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/comments/**").permitAll()
                        .requestMatchers("/api/posts").authenticated()
                        .requestMatchers("/api/comments/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers("/api/recommendations/interactions/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/{userId}").authenticated()
                        .requestMatchers("/api/chat/**").authenticated()
                        .requestMatchers("/ws/**").permitAll()

                        // ── Everything else requires login ────────────────────────────────────────
                        .anyRequest().authenticated()
                );

        http.authenticationProvider(daoAuthenticationProvider());
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // ✅ Exact origins instead of "*" with allowCredentials
        configuration.setAllowedOrigins(List.of(
                "https://govlyx.com",
                "https://www.govlyx.com",
                "https://govlyx-io.vercel.app",
                "https://govlyx.vercel.app",
                "https://govlyxpredeploytesting.vercel.app",
                "http://localhost:5173",
                "http://localhost:3000",
                "http://localhost:8080"
        ));

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // FIX: WebSecurityCustomizer with web.ignoring() REMOVED.
    // /uploads/** is now handled by permitAll() in the filter chain above.
    // web.ignoring() bypasses the security filter chain entirely — deprecated
    // in Spring Security 6 and causes the startup WARNING you were seeing.
}
