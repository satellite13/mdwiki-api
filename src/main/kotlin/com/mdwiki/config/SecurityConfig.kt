package com.mdwiki.config

import com.mdwiki.security.ApiKeyAuthenticationFilter
import com.mdwiki.security.JwtAuthenticationFilter
import com.mdwiki.security.McpAcceptHeaderFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val mcpAcceptHeaderFilter: McpAcceptHeaderFilter,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/events/tree").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/uploads/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/uploads/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/pages/**", "/api/tags/**", "/api/search/**").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/pages/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/pages/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/pages/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/folders/**").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/folders/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/folders/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/folders/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers("/api/sync/**").hasRole("ADMIN")
                    .requestMatchers("/api/users/**").hasRole("ADMIN")
                    .requestMatchers("/api/api-keys/**").authenticated()
                    .requestMatchers("/mcp/**").authenticated()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(mcpAcceptHeaderFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
