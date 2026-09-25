package com.beomsoo.fakepg

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class FakePgApplication

fun main(args: Array<String>) {
    runApplication<FakePgApplication>(*args)
}
