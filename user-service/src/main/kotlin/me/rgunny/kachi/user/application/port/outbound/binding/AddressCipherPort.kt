package me.rgunny.kachi.user.application.port.outbound.binding

/**
 * 채널 주소 암복호화 출력 포트.
 *
 * 같은 평문을 두 번 암호화해도 결과가 달라야 한다.
 * [keyVersion]은 암호문이 어느 키로 만들어졌는지 행에 남기기 위한 값이다.
 */
interface AddressCipherPort {
    val keyVersion: Int

    fun encrypt(plaintext: String): ByteArray

    fun decrypt(ciphertext: ByteArray): String
}
