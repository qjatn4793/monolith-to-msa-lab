package com.beomsoo.shop.catalog.application.port.out

import com.beomsoo.shop.catalog.domain.Product
import com.beomsoo.shop.shared.application.PageQuery
import com.beomsoo.shop.shared.application.PageResult

/**
 * 아웃바운드 포트: 목록 조회.
 *
 * 페이지 조회는 도메인 규칙이 아니라 화면의 요구다. 그래서 도메인 저장소(ProductRepository)에 두지 않고
 * 애플리케이션 계층의 포트로 분리했다. 나중에 조회 성능 때문에 도메인 객체 대신 조회 전용 모델을
 * 돌려주도록 바꾸더라도 도메인은 영향을 받지 않는다.
 */
interface ProductListPort {

    fun findPage(query: PageQuery): PageResult<Product>
}
