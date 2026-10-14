package com.nanzhufeng.ai.app

/** Only code locations and exception types; never exception messages or local user content. */
internal fun startupFailureDiagnostic(stage: String, error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(6).toList()
    return "stage=$stage; " + causes.joinToString(" <- ") { cause ->
        cause.javaClass.name + cause.stackTrace
            .filter { it.className.startsWith("com.nanzhufeng.ai.") || it.className.startsWith("androidx.room.") }
            .take(6).joinToString(prefix = " [", postfix = "]") { "${it.className}.${it.methodName}:${it.lineNumber}" }
    }
}
