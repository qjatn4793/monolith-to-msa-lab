package com.beomsoo.shop

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    // docker-compose.yml과 같은 버전을 쓴다.
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer = MySQLContainer(DockerImageName.parse("mysql:8.4"))
}
