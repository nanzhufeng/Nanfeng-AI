package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTaskError
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object OpenRouterErrorMapper {
    fun fromHttpStatus(statusCode: Int): AiTaskError = when (statusCode) {
        401, 403 -> AiTaskError.ProviderAuthenticationFailed
        402 -> AiTaskError.ProviderBalanceInsufficient
        408, 504 -> AiTaskError.ProviderTimedOut
        413 -> AiTaskError.ProviderContextOverflow
        422 -> AiTaskError.ProviderSchemaValidationFailed
        429 -> AiTaskError.ProviderRateLimited
        in 500..599 -> AiTaskError.ProviderUnavailable
        else -> AiTaskError.ProviderFailure
    }

    fun fromThrowable(error: Throwable): AiTaskError = when (error) {
        is SocketTimeoutException -> AiTaskError.ProviderTimedOut
        is UnknownHostException -> AiTaskError.ProviderNetworkUnavailable
        else -> AiTaskError.ProviderFailure
    }
}
