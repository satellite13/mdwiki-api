package com.mdwiki.config

import com.mdwiki.security.ApiKeyAuthenticationFilter
import com.mdwiki.security.JwtAuthenticationFilter
import com.mdwiki.security.McpAcceptHeaderFilter
import com.mdwiki.security.ScopedJwtAuthorizationFilter
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
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val mcpAcceptHeaderFilter: McpAcceptHeaderFilter,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val scopedJwtAuthorizationFilter: ScopedJwtAuthorizationFilter,
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
    private val conciseAccessDeniedHandler: ConciseAccessDeniedHandler,
    private val conciseAuthenticationEntryPoint: ConciseAuthenticationEntryPoint
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Persist SecurityContext on the request so ASYNC dispatches (SseEmitter, MCP /mcp/sse)
            // still see authentication after the initial thread finishes; STATELESS has no session.
            .securityContext { it.securityContextRepository(RequestAttributeSecurityContextRepository()) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/version").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/events/tree").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/uploads/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/graph/**").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/tasks/open").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/tasks/complete").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/pages/deleted").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/pages/*/restore").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/pages/**", "/api/tags/**", "/api/search/**").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/pages/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/pages/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/pages/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/attachments/**").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/attachments/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/attachments/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/folders/**").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/links/**").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/links/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/folders/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/folders/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/folders/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/annotations/**").hasAnyRole("READER", "EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/annotations/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/annotations/**").hasAnyRole("EDITOR", "ADMIN")
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/sync/**").hasRole("ADMIN")
                    .requestMatchers("/api/users/**").hasRole("ADMIN")
                    .requestMatchers("/api/api-keys/**").authenticated()
                    .requestMatchers("/mcp/**").authenticated()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { ex ->
                ex.accessDeniedHandler(conciseAccessDeniedHandler)
                ex.authenticationEntryPoint(conciseAuthenticationEntryPoint)
            }
            .addFilterBefore(mcpAcceptHeaderFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(scopedJwtAuthorizationFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
