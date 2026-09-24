package com.beomsoo.shop.shared.domain

import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 시간순으로 정렬되는 UUID v7을 만든다 (ADR-0007).
 * DB가 아니라 애플리케이션이 ID를 만들기 때문에, 저장하기 전에도 ID를 알 수 있다.
 */
fun newId(): UUID = Uuid.generateV7().toJavaUuid()
