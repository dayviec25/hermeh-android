package com.hermex.android.core.data

import com.hermex.android.core.model.CronDraft
import com.hermex.android.core.model.CronJob
import com.hermex.android.core.model.CronRun
import com.hermex.android.core.model.CronSaveResult
import com.hermex.android.core.model.DeliveryTarget
import com.hermex.android.core.model.ProfileName
import com.hermex.android.core.model.Skill
import com.hermex.android.core.network.HermesPanelsRest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TasksRepository(
    private val auth: AuthRepository,
    private val chat: ChatRepository,
) {
    private suspend fun <T> panels(block: suspend (HermesPanelsRest) -> T): T =
        auth.authorized { rest -> block(HermesPanelsRest(rest, auth.httpClient)) }

    suspend fun jobs(): List<CronJob> = panels { it.cronJobs() }

    suspend fun job(id: String): CronJob? = jobs().firstOrNull { it.id == id }

    suspend fun deliveryTargets(profile: String): List<DeliveryTarget> = panels { it.deliveryTargets(profile) }

    suspend fun skills(profile: String): List<Skill> = panels { it.skills(profile) }

    suspend fun models(profile: String): ModelCatalog = chat.modelOptions(null, ProfileName(profile))

    suspend fun create(profile: String, draft: CronDraft): CronSaveResult {
        val fields = buildJsonObject {
            put("schedule", draft.schedule.trim())
            put("prompt", draft.prompt.trim())
            put("name", draft.name.trim())
            put("deliver", draft.deliver.trim().ifEmpty { "local" })
            put("skills", JsonArray(draft.skills.map(::JsonPrimitive)))
            draft.model?.let { put("model", it) }
            draft.provider?.let { put("provider", it) }
        }
        val (job, warning) = panels { it.createCron(profile, fields) }
        return if (job != null && warning == null) CronSaveResult.Saved(job) else CronSaveResult.SavedWithWarning(warning)
    }

    suspend fun update(job: CronJob, draft: CronDraft): CronJob? {
        val updates = buildJsonObject {
            put("prompt", draft.prompt.trim())
            put("schedule", draft.schedule.trim())
            put("name", draft.name.trim())
            put("deliver", draft.deliver.trim().ifEmpty { "local" })
            put("skills", JsonArray(draft.skills.map(::JsonPrimitive)))
            draft.model?.let { put("model", it) }
            draft.provider?.let { put("provider", it) }
        }
        return panels { it.updateCron(job.id, job.profileOrDefault, updates) }
    }

    suspend fun pause(job: CronJob): CronJob? = panels { it.pauseCron(job.id, job.profileOrDefault) }

    suspend fun resume(job: CronJob): CronJob? = panels { it.resumeCron(job.id, job.profileOrDefault) }

    suspend fun trigger(job: CronJob): CronJob? = panels { it.triggerCron(job.id, job.profileOrDefault) }

    suspend fun delete(job: CronJob) = panels { it.deleteCron(job.id, job.profileOrDefault) }

    suspend fun runs(job: CronJob): List<CronRun> = panels { it.cronRuns(job.id, job.profileOrDefault) }

    suspend fun runOutput(job: CronJob, run: CronRun): String? = panels { it.runOutput(run.id, job.profileOrDefault) }

    private val CronJob.profileOrDefault: String get() = profile?.takeIf { it.isNotBlank() } ?: auth.activeProfile.value
}
