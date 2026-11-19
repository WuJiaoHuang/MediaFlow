package com.mediaflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediaflow.common.ApiResponse;
import com.mediaflow.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  FilterRegistrationBean<JwtAuthenticationFilter> disableContainerRegistration(
      JwtAuthenticationFilter filter) {
    var b = new FilterRegistrationBean<>(filter);
    b.setEnabled(false);
    return b;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, JwtAuthenticationFilter filter, ObjectMapper json)
      throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/media/categories",
                        "/api/media/assets/*/archive")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.PUT, "/api/media/categories/*")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.DELETE, "/api/media/categories/*")
                    .hasRole("ADMIN")
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/media/assets")
                    .hasAnyRole("ADMIN", "EDITOR")
                    .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/media/assets/*")
                    .hasAnyRole("ADMIN", "EDITOR")
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, x) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          json.writeValue(res.getOutputStream(), ApiResponse.error(40100, "请先登录"));
                        })
                    .accessDeniedHandler(
                        (req, res, x) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          json.writeValue(res.getOutputStream(), ApiResponse.error(40300, "权限不足"));
                        }))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
