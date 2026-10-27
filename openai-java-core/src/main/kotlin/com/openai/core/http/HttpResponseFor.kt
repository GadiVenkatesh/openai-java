package com.openai.core.http

import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface HttpResponseFor<T> : HttpResponse {

    fun parse(): T
}

private class ParsedValue<T>(val value: T)

@JvmSynthetic
internal fun <T> HttpResponse.parseable(parser: () -> T): HttpResponseFor<T> =
    object : HttpResponseFor<T> {

        private val parseLock = ReentrantLock()

        @Volatile private var parsed: ParsedValue<T>? = null

        override fun parse(): T {
            parsed?.let { return it.value }

            return parseLock.withLock {
                val current = parsed
                if (current != null) {
                    current.value
                } else {
                    parser().also { parsed = ParsedValue(it) }
                }
            }
        }

        override fun statusCode(): Int = this@parseable.statusCode()

        override fun headers(): Headers = this@parseable.headers()

        override fun body(): InputStream = this@parseable.body()

        override fun close() {
            val stream = parsed?.value as? StreamResponse<*>
            if (stream != null) {
                stream.close()
            } else {
                this@parseable.close()
            }
        }
    }
