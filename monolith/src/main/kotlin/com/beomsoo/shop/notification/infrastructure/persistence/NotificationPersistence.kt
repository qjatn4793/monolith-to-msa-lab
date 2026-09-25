package com.beomsoo.shop.notification.infrastructure.persistence

import com.beomsoo.shop.notification.application.port.out.NotificationListPort
import com.beomsoo.shop.notification.domain.Notification
import com.beomsoo.shop.notification.domain.NotificationId
import com.beomsoo.shop.notification.domain.NotificationRepository
import com.beomsoo.shop.notification.domain.NotificationType
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** 알림은 한 번 보내면 바뀌지 않는 기록이라 update가 없다. */
@Entity
@Table(name = "notification")
class NotificationJpaEntity(
    @Id
    val id: UUID,
    val memberId: UUID,
    val orderId: UUID,
    @Enumerated(EnumType.STRING)
    val type: NotificationType,
    val recipient: String,
    val message: String,
    val sentAt: Instant,
) {
    @Version
    var version: Long? = null

    fun toDomain() = Notification(NotificationId(id), memberId, orderId, type, recipient, message, sentAt)

    companion object {
        fun from(n: Notification) =
            NotificationJpaEntity(n.id.value, n.memberId, n.orderId, n.type, n.recipient, n.message, n.sentAt)
    }
}

interface NotificationJpaRepository : JpaRepository<NotificationJpaEntity, UUID> {

    fun existsByOrderIdAndType(orderId: UUID, type: NotificationType): Boolean

    fun findByMemberId(memberId: UUID, pageable: Pageable): Page<NotificationJpaEntity>
}

@Component
class NotificationPersistenceAdapter(
    private val jpaRepository: NotificationJpaRepository,
) : NotificationRepository, NotificationListPort {

    override fun save(notification: Notification) {
        jpaRepository.save(NotificationJpaEntity.from(notification))
    }

    override fun existsByOrderIdAndType(orderId: UUID, type: NotificationType): Boolean =
        jpaRepository.existsByOrderIdAndType(orderId, type)

    override fun findByMemberId(memberId: UUID, query: PageQuery): PageResult<Notification> {
        val page = jpaRepository.findByMemberId(memberId, PageRequest.of(query.page, query.size, Sort.by(Sort.Direction.DESC, "id")))
        return PageResult(page.content.map { it.toDomain() }, query.page, query.size, page.totalElements)
    }
}
