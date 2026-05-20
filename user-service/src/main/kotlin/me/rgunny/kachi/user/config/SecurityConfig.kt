package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.`in`.web.ApiPaths
import me.rgunny.kachi.user.adapter.`in`.web.security.JwtAuthenticationFilter
import me.rgunny.kachi.user.adapter.`in`.web.security.JwtTokenProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtTokenProvider: JwtTokenProvider
    ): SecurityFilterChain {
        http
            // JWT 기반 stateless API로 갈 예정이므로 서버 세션과 CSRF 토큰을 사용하지 않는다.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // API 서버에서는 브라우저 기본 로그인 화면과 Basic 인증을 노출하지 않는다.
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { requests ->
                requests
                    // 현재 구현된 API는 인증 흐름이 붙기 전까지 공개 API로 열어둔다.
                    .requestMatchers(HttpMethod.POST, ApiPaths.V1_USERS).permitAll()
                    .requestMatchers(HttpMethod.POST, ApiPaths.V1_USER_KEYWORDS).permitAll()
                    .requestMatchers(HttpMethod.PATCH, ApiPaths.V1_KEYWORDS).permitAll()
                    // 새 API는 의도적으로 허용하기 전까지 인증을 요구한다.
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                // Bearer token을 SecurityContext 인증 정보로 변환한다.
                JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }
}
