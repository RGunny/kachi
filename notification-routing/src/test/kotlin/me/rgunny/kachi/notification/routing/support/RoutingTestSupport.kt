package me.rgunny.kachi.notification.routing.support

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * fake만 쓰는 suspend 테스트를 동기적으로 실행한다. 실제로 일시 중단되면 실패한다.
 */
fun <T> runSuspend(block: suspend () -> T): T {
    var completed = false
    var value: T? = null
    var failure: Throwable? = null

    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                result
                    .onSuccess { value = it }
                    .onFailure { failure = it }
                completed = true
            }
        }
    )

    check(completed) { "suspend block did not complete synchronously" }
    failure?.let { throw it }

    @Suppress("UNCHECKED_CAST")
    return value as T
}
