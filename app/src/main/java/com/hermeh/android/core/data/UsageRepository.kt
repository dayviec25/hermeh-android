package com.hermeh.android.core.data

import com.hermeh.android.core.model.UsageReport
import com.hermeh.android.core.network.HermesPanelsRest

class UsageRepository(private val auth: AuthRepository) {
    suspend fun usage(days: Int, profile: String): UsageReport =
        auth.authorized { rest -> HermesPanelsRest(rest, auth.httpClient).usage(days, profile) }
}
