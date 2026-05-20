package me.rgunny.kachi.user.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kachi.oauth2")
data class OAuth2Properties(
    val successRedirectUri: String
)
