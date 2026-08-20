package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.BuildConfig
import com.nanzhufeng.ai.domain.P7CCloudGateway
import com.nanzhufeng.ai.domain.P7CDisabledCloudGateway
import com.nanzhufeng.ai.domain.P7CServiceAvailability
import com.nanzhufeng.ai.domain.P7CServiceConfiguration

/** No UI or network is enabled when local/private injection is absent. */
object P7CAndroidCloudGateway {
    fun availability(): P7CServiceAvailability = P7CServiceConfiguration.resolve(
        BuildConfig.NANFENG_SUPABASE_URL,
        BuildConfig.NANFENG_SUPABASE_PUBLISHABLE_KEY,
        BuildConfig.NANFENG_GOOGLE_WEB_CLIENT_ID,
    )

    fun offlineSafeGateway(): P7CCloudGateway = P7CDisabledCloudGateway
}
