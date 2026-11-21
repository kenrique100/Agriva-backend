package com.agriva.shared.config;

import com.agriva.shared.security.IpBlacklistFilter;
import com.agriva.shared.security.JwtAuthFilter;
import com.agriva.shared.security.RateLimitFilter;
import com.agriva.shared.security.RequestIdFilter;
import com.agriva.shared.security.TrustedProxyResolver;
import com.agriva.shared.security.blacklist.CaffeineIpBlacklistService;
import com.agriva.shared.security.blacklist.IpBlacklistService;
import com.agriva.shared.security.blacklist.RedisIpBlacklistService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestIdFilter requestIdFilter;
    private final TrustedProxyResolver proxyResolver;

    // Inject both implementations
    private final RedisIpBlacklistService redisIpBlacklistService;
    private final CaffeineIpBlacklistService caffeineIpBlacklistService;

    @Value("${agriva.security.ip-blacklist.backend:redis}")
    private String blacklistBackend;

    @Value("${agriva.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter,
                          RequestIdFilter requestIdFilter,
                          TrustedProxyResolver proxyResolver,
                          RedisIpBlacklistService redisIpBlacklistService,
                          CaffeineIpBlacklistService caffeineIpBlacklistService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.requestIdFilter = requestIdFilter;
        this.proxyResolver = proxyResolver;
        this.redisIpBlacklistService = redisIpBlacklistService;
        this.caffeineIpBlacklistService = caffeineIpBlacklistService;
    }

    /**
     * Manually select the correct bean based on the property.
     * This prevents Spring startup failures if the property is missing.
     */
    @Bean
    @Primary
    public IpBlacklistService ipBlacklistService() {
        if ("memory".equalsIgnoreCase(blacklistBackend)) {
            return caffeineIpBlacklistService;
        }
        return redisIpBlacklistService;
    }

    @Bean
    public IpBlacklistFilter ipBlacklistFilter(IpBlacklistService ipBlacklistService) {
        return new IpBlacklistFilter(ipBlacklistService, proxyResolver);
    }

    @Bean public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter f) {
        FilterRegistrationBean<RequestIdFilter> r = new FilterRegistrationBean<>(f); r.setEnabled(false); return r;
    }
    @Bean public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter f) {
        FilterRegistrationBean<RateLimitFilter> r = new FilterRegistrationBean<>(f); r.setEnabled(false); return r;
    }
    @Bean public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter f) {
        FilterRegistrationBean<JwtAuthFilter> r = new FilterRegistrationBean<>(f); r.setEnabled(false); return r;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, IpBlacklistFilter ipBlacklistFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(this::configureHeaders)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write(
                                    "{\"status\":401,\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write(
                                    "{\"status\":403,\"message\":\"Access denied\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/users/login",
                                "/api/v1/users/refresh",
                                "/api/v1/users/register/master",
                                "/api/v1/users/forgot-password",
                                "/api/v1/users/reset-password",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/swagger-resources/**", "/webjars/**",
                                "/actuator/health", "/actuator/health/**", "/actuator/info"
                        ).permitAll()
                        .requestMatchers("/api/v1/users/register/sub-user")
                        .hasAnyRole("MASTER", "ADMIN")
                        .anyRequest().authenticated()
                )
                // Chain order: RequestId → IpBlacklist → RateLimit → JwtAuth
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(ipBlacklistFilter, RequestIdFilter.class)
                .addFilterAfter(rateLimitFilter, IpBlacklistFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void configureHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data: blob:; " +
                                "script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                                "connect-src 'self'; frame-ancestors 'none'"))
                .referrerPolicy(rp -> rp.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000))
                .contentTypeOptions(ct -> {})
                .addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        String encodingId = "bcrypt";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        encoders.put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder(encodingId, encoders);
        delegating.setDefaultPasswordEncoderForMatches(encoders.get(encodingId));
        return delegating;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "agriva.cors.allowed-origins is empty — set CORS_ALLOWED_ORIGINS");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Request-Id", "X-Idempotency-Key",
                "Accept", "Origin"));
        config.setExposedHeaders(List.of(
                "Authorization", "X-Request-Id", "X-RateLimit-Remaining", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}