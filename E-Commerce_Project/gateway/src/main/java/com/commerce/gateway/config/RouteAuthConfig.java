package com.commerce.gateway.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import com.commerce.gateway.security.RouteRule;

@Configuration
public class RouteAuthConfig {

    @Bean
    public List<RouteRule> routeRules() {
        return List.of(

            RouteRule.adminOnly("/admin/**"),
            RouteRule.adminOnly("/users/manage/**"),
            
            // Product routes
            RouteRule.withPermission(
                "/products/**", HttpMethod.POST,
                "product:create", "ADMIN","EMPLOYEE"),

            RouteRule.withPermission(
                "/products/**", HttpMethod.PUT,
                "product:update", "ADMIN","EMPLOYEE"),

            RouteRule.withPermission(
                "/products/**", HttpMethod.DELETE,
                "product:delete", "ADMIN","EMPLOYEE"),

            RouteRule.withPermission(
                "/products/**", HttpMethod.GET,
                "product:read", "USER", "ADMIN"),

            RouteRule.forMethod("/products/**", HttpMethod.GET,    "USER", "EMPLOYEE"),
            RouteRule.forMethod("/auth/refresh",   HttpMethod.POST,    "USER", "EMPLOYEE"),

            RouteRule.forMethod("/products/**", HttpMethod.DELETE, "ADMIN"),
            RouteRule.forMethod("/products/**", HttpMethod.POST,   "ADMIN"),
            RouteRule.forMethod("/products/**", HttpMethod.PUT,    "ADMIN"),
            
            // RouteRule.forRoles("/products/**", "USER", "EMPLOYEE"),
            RouteRule.forRoles("/orders/**",   "USER", "EMPLOYEE"),
            RouteRule.forRoles("/payments/**", "USER", "EMPLOYEE"),
            RouteRule.forRoles("/auth/**",  "USER", "EMPLOYEE"),

            RouteRule.adminOnly("/**")   
        );
    }
}