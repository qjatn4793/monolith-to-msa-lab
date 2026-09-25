package com.beomsoo.shop.payment.infrastructure.adapter

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("payment.pg")
data class PgProperties(
    val baseUrl: String,
    /** 연결을 맺는 데 기다리는 최대 시간 */
    val connectTimeout: Duration = Duration.ofSeconds(1),
    /** 응답을 기다리는 최대 시간. PG가 이보다 느리면 결과를 모르는 채로 실패 처리한다 */
    val readTimeout: Duration = Duration.ofSeconds(3),
)
