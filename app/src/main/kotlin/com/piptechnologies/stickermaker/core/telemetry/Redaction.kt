package com.piptechnologies.stickermaker.core.telemetry

/**
 * This failure as crash reporting may see it: the same types and stack traces, every message
 * removed. Messages can hold a pack's name or its id (a name pack's id is a hash of the names),
 * and a failed WhatsApp query quotes its URI, id included.
 */
internal fun Throwable.redacted(): Throwable =
    RedactedFailure(this::class.java.name, cause?.redacted()).also { it.stackTrace = stackTrace }

/** Stands in for a redacted failure; its message is only the original type's name. */
internal class RedactedFailure(type: String, cause: Throwable?) : Exception(type, cause)
