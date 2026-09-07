package me.rgunny.kachi.ai.support

import java.nio.file.Files
import java.nio.file.Path

/**
 * 실호출 테스트가 secret을 찾는 순서를 정한다. 환경변수 → `.env.<profile>` → `.env`.
 *
 * 기동 스크립트가 env 파일을 읽는 순서와 같다. env 파일은 작업 디렉토리에서 상위로 올라가며 찾으므로
 * Gradle과 IDE의 작업 디렉토리가 달라도 저장소 루트에 닿는다. 없는 값은 null이다.
 */
class TestSecretEnvironment(
    private val profile: String
) {

    fun value(name: String): String? {
        return System.getenv(name)
            ?.takeIf { it.isNotBlank() }
            ?: dotenvValue(".env.$profile", name)
            ?: dotenvValue(".env", name)
    }

    private fun dotenvValue(
        fileName: String,
        key: String
    ): String? {
        val path = dotenvPath(fileName) ?: return null

        return Files.readAllLines(path)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line -> dotenvEntry(line) }
            .firstOrNull { (name, value) -> name == key && value.isNotBlank() }
            ?.second
    }

    private fun dotenvPath(fileName: String): Path? {
        var current: Path? = Path.of("").toAbsolutePath()

        while (current != null) {
            val candidate = current.resolve(fileName)
            if (Files.exists(candidate)) {
                return candidate
            }
            current = current.parent
        }

        return null
    }

    private fun dotenvEntry(line: String): Pair<String, String>? {
        val delimiterIndex = line.indexOf('=')
        if (delimiterIndex <= 0) {
            return null
        }

        return line.substring(0, delimiterIndex).trim() to line.substring(delimiterIndex + 1).trim()
    }
}
