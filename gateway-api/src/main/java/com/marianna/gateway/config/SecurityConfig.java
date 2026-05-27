package com.marianna.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.marianna.gateway.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtFilter;

        public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
            this.jwtFilter = jwtFilter;
        }

    @Bean
    public SecurityFilterChain securityWebFilterChain(HttpSecurity http) throws Exception {
        
        http
        //Disable CSRF (stateless JWT API — no sessions)
        .csrf(csrf -> csrf.disable())
        //stateless session (no HTTP session stored)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // authorization rules
        .authorizeHttpRequests(auth -> auth.
            //Swagger - OpenApi
            requestMatchers(
                "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/api-docs/**")
                    .permitAll()
            // auth endpoints (login/register/refresh)
            .requestMatchers("/api/v1/auth/**")
            .permitAll()
            // everything else requires authentication
            .anyRequest().authenticated()
            
        )

        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        // if you use JWT, you'll later replace this with JWT filter
        .httpBasic(Customizer.withDefaults());

        return http.build();

    }
    
}
