package me.rgunny.kachi.notification.worker.support

import java.nio.file.Files
import java.nio.file.Path

/**
 * 테스트에서만 사용하는 secret lookup helper.
 *
 * CI에서는 secret manager가 주입한 환경변수를 우선 사용하고,
 * 로컬 IDE 실행에서는 gitignore된 .env.local/.env를 프로젝트 상위 경로에서 찾아 읽는다.
 */
object TestSecretEnvironment {

    fun value(name: String): String? {
        return System.getenv(name)
            ?.takeIf { it.isNotBlank() }
            ?: dotenvValue(".env.local", name)
            ?: dotenvValue(".env", name)
    }

    private fun dotenvValue(fileName: String, key: String): String? {
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
