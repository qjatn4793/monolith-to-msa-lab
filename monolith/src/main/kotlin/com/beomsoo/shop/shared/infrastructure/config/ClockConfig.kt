package com.beomsoo.shop.shared.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 도메인은 현재 시각을 직접 구하지 않고 인자로 받는다. 서비스는 이 Clock에서 시각을 얻는다.
 * 테스트에서는 고정된 Clock으로 바꿔 끼울 수 있다.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
