package me.rgunny.kachi.user.adapter.inbound.web.fake

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class WebMvcFakeUseCaseConfig {

    @Bean
    fun registerUserUseCase(): FakeRegisterUserUseCase = FakeRegisterUserUseCase()

    @Bean
    fun deactivateUserUseCase(): FakeDeactivateUserUseCase = FakeDeactivateUserUseCase()

    @Bean
    fun getUserUseCase(): FakeGetUserUseCase = FakeGetUserUseCase()

    @Bean
    fun refreshTokenUseCase(): FakeRefreshTokenUseCase = FakeRefreshTokenUseCase()

    @Bean
    fun logoutUseCase(): FakeLogoutUseCase = FakeLogoutUseCase()

    @Bean
    fun registerKeywordUseCase(): FakeRegisterKeywordUseCase = FakeRegisterKeywordUseCase()

    @Bean
    fun updateKeywordUseCase(): FakeUpdateKeywordUseCase = FakeUpdateKeywordUseCase()

    @Bean
    fun listKeywordsUseCase(): FakeListKeywordsUseCase = FakeListKeywordsUseCase()
}
