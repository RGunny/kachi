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
                    // 사용자 등록 API만 공개한다.
                    .requestMatchers(HttpMethod.POST, ApiPaths.V1_USERS).permitAll()
                    // 그 외 API는 access token 인증을 요구한다.
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
