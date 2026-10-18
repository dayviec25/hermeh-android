package com.hermex.android.core.data

import com.hermex.android.core.model.UsageReport
import com.hermex.android.core.network.HermesPanelsRest

class UsageRepository(private val auth: AuthRepository) {
    suspend fun usage(days: Int, profile: String): UsageReport =
        auth.authorized { rest -> HermesPanelsRest(rest, auth.httpClient).usage(days, profile) }
}
