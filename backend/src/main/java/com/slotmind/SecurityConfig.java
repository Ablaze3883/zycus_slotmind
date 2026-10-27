package com.slotmind;

import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.SuggestionRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final String issuerUri;
    private final String audience;
    private final String rolesClaim;

    public SecurityConfig(
            @Value("${slotmind.auth0.issuer-uri:}") String issuerUri,
            @Value("${slotmind.auth0.audience:}") String audience,
            @Value("${slotmind.auth0.roles-claim:https://slotmind.dev/roles}") String rolesClaim) {
        this.issuerUri = issuerUri;
        this.audience = audience;
        this.rolesClaim = rolesClaim;
    }

    @Bean
    @Profile("!test")
    JwtDecoder auth0JwtDecoder() {
        if (issuerUri.isBlank()) return token -> { throw new JwtException("Auth0 is not configured."); };
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                !audience.isBlank() && jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new org.springframework.security.oauth2.core.OAuth2Error(
                                        "invalid_token", "Required audience is missing.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    @Profile("!test")
    JwtAuthenticationConverter auth0JwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object raw = jwt.getClaims().get(rolesClaim);
            if (raw instanceof String role) return List.of(new SimpleGrantedAuthority(normalizeRole(role)));
            if (raw instanceof Collection<?> roles) {
                return roles.stream().filter(String.class::isInstance)
                        .map(String.class::cast).map(SecurityConfig::normalizeRole)
                        .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
            }
            return List.<GrantedAuthority>of();
        });
        return converter;
    }

    @Bean
    @Profile("test")
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Profile("!test")
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, PatientAuthService authService, AppointmentRepository appointments,
            SuggestionRepository suggestions, JwtDecoder auth0JwtDecoder,
            JwtAuthenticationConverter auth0JwtAuthenticationConverter) throws Exception {
        JwtAuthenticationProvider auth0Provider = new JwtAuthenticationProvider(auth0JwtDecoder);
        auth0Provider.setJwtAuthenticationConverter(auth0JwtAuthenticationConverter);
        AuthenticationManager auth0Manager = auth0Provider::authenticate;
        BearerTokenResolver resolver = request -> {
            String token = new DefaultBearerTokenResolver().resolve(request);
            return token != null && (token.startsWith("pt_") || token.startsWith("demo_")) ? null : token;
        };
        return http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/config", "/auth/patient/login", "/auth/demo/login").permitAll()
                        .requestMatchers("/auth/me", "/auth/patient/logout").hasAnyRole("PATIENT", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/portal/appointments/*").hasAnyRole("PATIENT", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/portal/appointments/*/reschedule").hasAnyRole("PATIENT", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/suggestions/*").hasAnyRole("PATIENT", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/preferences/*").hasAnyRole("PATIENT", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/preferences/*").hasAnyRole("PATIENT", "STAFF", "ADMIN")
                        .requestMatchers("/appointments/*/notes", "/appointments/*/notes/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/config/strategy").hasRole("ADMIN")
                        .requestMatchers("/**").hasAnyRole("STAFF", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(resolver)
                        .authenticationManagerResolver(request -> auth0Manager))
                .addFilterBefore(new PatientSessionFilter(authService, appointments, suggestions),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }

    private static String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) return normalized;
        if (!List.of("STAFF", "ADMIN").contains(normalized)) {
            throw new BadCredentialsException("The identity provider issued an unsupported role.");
        }
        return "ROLE_" + normalized;
    }
}
