package com.beomsoo.shop

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readLines
import kotlin.test.assertTrue

/**
 * 소스 코드의 import 문으로 모듈 경계를 검사한다 (ADR-0003).
 *
 * Spring Modulith와 ArchUnit은 바이트코드를 분석한다. 그런데 Kotlin의 value class(MemberId, Email 등)는
 * 바이트코드에서 감싼 타입(UUID, String)으로 지워진다. 그래서 다른 모듈의 값 클래스를 필드나 파라미터
 * 타입으로 선언만 하면 두 도구 모두 의존을 보지 못한다. import 문에는 그 흔적이 남으므로 여기서 잡는다.
 *
 * 한계: import 없이 전체 이름(com.beomsoo.shop.member.domain.MemberId)을 직접 쓰면 잡지 못한다.
 */
class SourceImportRulesTests {

    private val root: Path = Path.of("src/main/kotlin/com/beomsoo/shop")
    private val base = "com.beomsoo.shop."

    private data class Import(val file: String, val module: String, val target: String) {
        val targetModule: String get() = target.removePrefix("com.beomsoo.shop.").substringBefore('.')
        override fun toString() = "$file → $target"
    }

    private val imports: List<Import> = Files.walk(root).use { paths ->
        paths.filter { it.extension == "kt" }.toList()
    }.flatMap { file ->
        val relative = root.relativize(file)
        if (relative.nameCount < 2) return@flatMap emptyList()
        file.readLines()
            .map { it.trim() }
            .filter { it.startsWith("import $base") }
            .map { Import(relative.invariantSeparatorsPathString, relative.getName(0).toString(), it.removePrefix("import ").replace("`", "")) }
    }

    @Test
    fun `다른 모듈은 api 패키지만 import 한다`() {
        val violations = imports.filter {
            it.targetModule != it.module &&
                it.targetModule != "shared" &&
                !it.target.startsWith("$base${it.targetModule}.api.")
        }
        assertTrue(imports.isNotEmpty(), "검사할 import가 없다. 경로 설정을 확인할 것: $root")
        assertTrue(violations.isEmpty(), "다른 모듈의 내부 패키지를 import 했다:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `api 패키지는 domain, application, infrastructure를 import 하지 않는다`() {
        val violations = imports.filter {
            it.file.split('/').getOrNull(1) == "api" &&
                listOf(".domain.", ".application.", ".infrastructure.").any { layer -> layer in it.target }
        }
        assertTrue(violations.isEmpty(), "공개 계약(api)이 내부 타입을 import 했다:\n" + violations.joinToString("\n"))
    }
}
