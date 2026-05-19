package me.rgunny.kachi.user.adapter.`in`.web.fake

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class WebMvcFakeUseCaseConfig {

    @Bean
    fun registerUserUseCase(): FakeRegisterUserUseCase = FakeRegisterUserUseCase()

    @Bean
    fun registerKeywordUseCase(): FakeRegisterKeywordUseCase = FakeRegisterKeywordUseCase()

    @Bean
    fun updateKeywordUseCase(): FakeUpdateKeywordUseCase = FakeUpdateKeywordUseCase()
}
