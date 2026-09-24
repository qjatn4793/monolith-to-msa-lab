// 플러그인 버전은 여기서 한 번만 선언한다.
// 이후 services/* 모듈이 추가되어도 같은 버전을 쓰도록 하기 위함이다.
plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    kotlin("plugin.jpa") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.beomsoo"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
