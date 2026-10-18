package com.hermeh.android.core.network

import com.hermeh.android.core.model.CronJob
import com.hermeh.android.core.model.CronRun
import com.hermeh.android.core.model.CronSchedule
import com.hermeh.android.core.model.DeliveryTarget
import com.hermeh.android.core.model.Skill
import com.hermeh.android.core.model.SkillFile
import com.hermeh.android.core.model.UsageDay
import com.hermeh.android.core.model.UsageModel
import com.hermeh.android.core.model.UsageReport
import com.hermeh.android.core.model.UsageTool
import com.hermeh.android.core.model.UsageTotals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class HermesRefusal(val detail: String) : Exception("The server rejected the request: $detail")

class HermesHttpError(val code: Int) : Exception("Hermes answered with HTTP $code.")

class HermesRunUnavailable : Exception("This run is no longer on the server.")

class HermesHistoryUnreadable :
    Exception("Hermes couldn\u2019t read this Profile\u2019s session history. Run hermes doctor on the host, then try again.")

data class RestReply(val code: Int, val body: JsonElement?) {
    val detail: String?
        get() = (body as? JsonObject)?.let { it.str("detail") }?.trim()?.takeIf { it.isNotEmpty() }
}

class HermesPanelsRest(private val rest: HermesRest, private val client: OkHttpClient) {
    private val longClient = client.newBuilder().readTimeout(10, TimeUnit.MINUTES).callTimeout(10, TimeUnit.MINUTES).build()

    suspend fun cronJobs(): List<CronJob> =
        accepted(reply("GET", "api/cron/jobs")).arr()?.mapNotNull { it.obj()?.let(::parseJob) }
            ?: throw HermesHttpError(200)

    suspend fun deliveryTargets(profile: String): List<DeliveryTarget> =
        accepted(reply("GET", "api/cron/delivery-targets", listOf("profile" to profile))).obj()
            .list("targets").mapNotNull { element ->
                val row = element.obj() ?: return@mapNotNull null
                val id = row.str("id")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                DeliveryTarget(id, row.str("name") ?: id)
            }

    suspend fun createCron(profile: String, fields: JsonObject): Pair<CronJob?, String?> {
        val answer = reply("POST", "api/cron/jobs", listOf("profile" to profile), fields)
        if (answer.code == 424) {
            val warning = (answer.body as? JsonObject)?.child("detail").str("error") ?: answer.detail
            return null to warning
        }
        return accepted(answer).obj()?.let(::parseJob) to null
    }

    suspend fun updateCron(id: String, profile: String, updates: JsonObject): CronJob? =
        accepted(reply("PUT", "api/cron/jobs/$id", listOf("profile" to profile), buildJsonObject { put("updates", updates) }))
            .obj()?.let(::parseJob)

    suspend fun pauseCron(id: String, profile: String): CronJob? =
        accepted(reply("POST", "api/cron/jobs/$id/pause", listOf("profile" to profile), JsonObject(emptyMap()))).obj()?.let(::parseJob)

    suspend fun resumeCron(id: String, profile: String): CronJob? =
        accepted(reply("POST", "api/cron/jobs/$id/resume", listOf("profile" to profile), JsonObject(emptyMap()))).obj()?.let(::parseJob)

    suspend fun triggerCron(id: String, profile: String): CronJob? =
        accepted(reply("POST", "api/cron/jobs/$id/trigger", listOf("profile" to profile), JsonObject(emptyMap()), long = true))
            .obj()?.let(::parseJob)

    suspend fun deleteCron(id: String, profile: String) {
        accepted(reply("DELETE", "api/cron/jobs/$id", listOf("profile" to profile)))
    }

    suspend fun cronRuns(id: String, profile: String): List<CronRun> {
        val root = accepted(reply("GET", "api/cron/jobs/$id/runs", listOf("profile" to profile, "limit" to "100"))).obj()
        val rows = root?.get("runs").arr() ?: throw HermesHttpError(200)
        return rows.mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            val runId = row.str("id")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CronRun(
                id = runId,
                startedAt = instant(row["started_at"]),
                endedAt = instant(row["ended_at"]),
                isActive = row.bool("is_active") ?: false,
                model = row.str("model"),
                inputTokens = row.long("input_tokens"),
                outputTokens = row.long("output_tokens"),
                costUsd = row.double("actual_cost_usd") ?: row.double("estimated_cost_usd"),
            )
        }
    }

    suspend fun runOutput(runId: String, profile: String): String? {
        val answer = reply("GET", "api/sessions/$runId/messages", listOf("profile" to profile))
        if (answer.code == 404) throw HermesRunUnavailable()
        val messages = accepted(answer).obj().list("messages").mapNotNull { it.obj() }
        return messages.lastOrNull { row ->
            row.str("role") == "assistant" && (row["tool_calls"].arr()?.isEmpty() ?: true) &&
                !(row.str("content") ?: row.str("text")).isNullOrBlank()
        }?.let { it.str("content") ?: it.str("text") }
    }

    suspend fun skills(profile: String): List<Skill> {
        val rows = accepted(reply("GET", "api/skills", listOf("profile" to profile))).arr() ?: throw HermesHttpError(200)
        return rows.mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            val name = row.str("name")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Skill(name, row.str("description"), row.str("category"), row.bool("enabled") ?: true)
        }
    }

    suspend fun toggleSkill(name: String, enabled: Boolean, profile: String): Boolean {
        val body = buildJsonObject {
            put("name", name)
            put("enabled", enabled)
            put("profile", profile)
        }
        return accepted(reply("PUT", "api/skills/toggle", body = body)).obj().bool("enabled") ?: enabled
    }

    suspend fun skillContent(name: String, profile: String): Pair<String, String?> {
        val root = accepted(reply("GET", "api/skills/content", listOf("name" to name, "profile" to profile))).obj()
        return root.str("content").orEmpty() to root.str("path")
    }

    data class FsEntry(val name: String, val isDirectory: Boolean)

    suspend fun fsList(path: String): List<FsEntry> =
        accepted(reply("GET", "api/fs/list", listOf("path" to path))).obj().list("entries").mapNotNull { element ->
            val row = element.obj() ?: return@mapNotNull null
            FsEntry(row.str("name") ?: return@mapNotNull null, row.bool("isDirectory") ?: false)
        }

    suspend fun fsReadText(path: String): SkillFile? {
        val answer = reply("GET", "api/fs/read-text", listOf("path" to path))
        if (answer.code == 404) return null
        val root = accepted(answer).obj()
        return SkillFile(
            name = path.substringAfterLast('/'),
            text = root.str("text"),
            binary = root.bool("binary") ?: false,
            truncated = root.bool("truncated") ?: false,
        )
    }

    suspend fun fsWriteText(path: String, content: String): RestReply =
        reply("POST", "api/fs/write-text", body = buildJsonObject {
            put("path", path)
            put("content", content)
        })

    suspend fun mkdir(path: String) {
        accepted(reply("POST", "api/files/mkdir", body = buildJsonObject { put("path", path) }))
    }

    data class MemoryConfig(val memoryEnabled: Boolean, val userEnabled: Boolean, val memoryLimit: Int, val userLimit: Int)

    suspend fun memoryConfig(profile: String): MemoryConfig {
        val memory = accepted(reply("GET", "api/config", listOf("profile" to profile))).obj().child("memory")
        fun flag(key: String): Boolean {
            val value = memory?.get(key) as? JsonPrimitive ?: return true
            if (value is JsonNull) return true
            if (value.isString) return value.content.trim().lowercase() in setOf("1", "true", "yes", "on")
            return memory.bool(key) ?: true
        }
        return MemoryConfig(
            memoryEnabled = flag("memory_enabled"),
            userEnabled = flag("user_profile_enabled"),
            memoryLimit = memory.long("memory_char_limit")?.toInt() ?: 2200,
            userLimit = memory.long("user_char_limit")?.toInt() ?: 1375,
        )
    }

    suspend fun profilePath(profile: String): String? =
        accepted(reply("GET", "api/profiles")).obj().list("profiles").mapNotNull { it.obj() }
            .firstOrNull { it.str("name") == profile }?.str("path")?.takeIf { it.isNotEmpty() }

    suspend fun soul(profile: String): String =
        accepted(reply("GET", "api/profiles/$profile/soul")).obj().str("content").orEmpty()

    suspend fun setSoul(profile: String, content: String) {
        accepted(reply("PUT", "api/profiles/$profile/soul", body = buildJsonObject { put("content", content) }))
    }

    suspend fun usage(days: Int, profile: String): UsageReport {
        val answer = reply("GET", "api/analytics/usage", listOf("days" to days.toString(), "profile" to profile))
        if (answer.code == 503) {
            val error = (answer.body as? JsonObject)?.child("detail").str("error") ?: answer.detail.orEmpty()
            if (error.startsWith("state_db")) throw HermesHistoryUnreadable()
        }
        val root = accepted(answer).obj() ?: throw HermesHttpError(answer.code)
        val totals = root.child("totals")
        return UsageReport(
            periodDays = root.long("period_days")?.toInt() ?: days,
            daily = root.list("daily").mapNotNull { element ->
                val row = element.obj() ?: return@mapNotNull null
                UsageDay(
                    day = row.str("day") ?: return@mapNotNull null,
                    inputTokens = row.long("input_tokens") ?: 0,
                    outputTokens = row.long("output_tokens") ?: 0,
                    cacheReadTokens = row.long("cache_read_tokens") ?: 0,
                    reasoningTokens = row.long("reasoning_tokens") ?: 0,
                    estimatedCost = row.double("estimated_cost") ?: 0.0,
                    actualCost = row.double("actual_cost") ?: 0.0,
                    sessions = row.long("sessions")?.toInt() ?: 0,
                    apiCalls = row.long("api_calls")?.toInt() ?: 0,
                )
            },
            models = root.list("by_model").mapNotNull { element ->
                val row = element.obj() ?: return@mapNotNull null
                UsageModel(
                    model = row.str("model") ?: "Unknown Model",
                    provider = row.str("provider"),
                    inputTokens = row.long("input_tokens") ?: 0,
                    outputTokens = row.long("output_tokens") ?: 0,
                    cacheReadTokens = row.long("cache_read_tokens") ?: 0,
                    estimatedCost = row.double("estimated_cost") ?: 0.0,
                    sessions = row.long("sessions")?.toInt() ?: 0,
                    apiCalls = row.long("api_calls")?.toInt() ?: 0,
                )
            },
            totals = UsageTotals(
                input = totals.long("total_input") ?: 0,
                output = totals.long("total_output") ?: 0,
                cacheRead = totals.long("total_cache_read") ?: 0,
                reasoning = totals.long("total_reasoning") ?: 0,
                estimatedCost = totals.double("total_estimated_cost") ?: 0.0,
                actualCost = totals.double("total_actual_cost") ?: 0.0,
                sessions = totals.long("total_sessions")?.toInt() ?: 0,
                apiCalls = totals.long("total_api_calls")?.toInt() ?: 0,
            ),
            tools = root.list("tools").mapNotNull { element ->
                val row = element.obj() ?: return@mapNotNull null
                UsageTool(row.str("tool") ?: return@mapNotNull null, row.long("count")?.toInt() ?: 0, row.double("percentage") ?: 0.0)
            },
        )
    }

    suspend fun reply(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JsonObject? = null,
        long: Boolean = false,
    ): RestReply = withContext(Dispatchers.IO) {
        val url: HttpUrl = rest.url(path, query)
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        rest.headers.forEach { (name, value) -> builder.header(name, value) }
        val requestBody = body?.toString()?.toRequestBody(JSON)
        builder.method(method, requestBody ?: if (method == "POST" || method == "PUT") "{}".toRequestBody(JSON) else null)
        val response = try {
            (if (long) longClient else client).newCall(builder.build()).execute()
        } catch (error: IOException) {
            throw HermesFailure.Transport(rest.host, error)
        }
        response.use {
            if (it.request.url.host != rest.host || it.code in 300..399) throw HermesFailure.Blocked()
            val text = it.body?.string().orEmpty()
            RestReply(it.code, runCatching { HermesJson.parseToJsonElement(text) }.getOrNull())
        }
    }

    fun accepted(reply: RestReply): JsonElement? {
        val code = reply.code
        return when {
            code in 200..299 -> reply.body
            code == 401 -> throw HermesFailure.Rejected(401, rest.host)
            code == 403 || code in 502..504 || code in 520..530 -> throw HermesFailure.Rejected(code, rest.host)
            code in 400..499 && reply.detail != null -> throw HermesRefusal(requireNotNull(reply.detail))
            else -> throw HermesHttpError(code)
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        fun instant(element: JsonElement?): Instant? {
            val primitive = element as? JsonPrimitive ?: return null
            if (primitive is JsonNull) return null
            if (!primitive.isString) {
                val number = primitive.doubleOrNull ?: return null
                if (!number.isFinite()) return null
                val seconds = if (number > 1e12) number / 1000 else number
                return Instant.ofEpochMilli((seconds * 1000).toLong())
            }
            val text = primitive.content.trim()
            if (text.isEmpty()) return null
            text.toDoubleOrNull()?.let { return instant(JsonPrimitive(it)) }
            return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
                ?: runCatching { Instant.parse(text) }.getOrNull()
                ?: runCatching { LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
        }

        fun parseJob(row: JsonObject): CronJob? {
            val id = row.str("id") ?: row.str("job_id") ?: return null
            val scheduleElement = row["schedule"]
            val schedule = when (scheduleElement) {
                is JsonObject -> CronSchedule(
                    kind = scheduleElement.str("kind"),
                    expression = scheduleElement.str("expression") ?: scheduleElement.str("expr"),
                    runAt = scheduleElement.str("run_at"),
                    every = scheduleElement.str("every")
                        ?: scheduleElement.long("minutes")?.let { "every ${it}m" }
                        ?: scheduleElement.str("display"),
                )
                is JsonPrimitive -> scheduleElement.textOrNull()?.let { CronSchedule(null, it, null, null) }
                else -> null
            }
            val repeat = row.child("repeat")
            val fireClaim = row["fire_claim"]
            return CronJob(
                id = id,
                name = row.str("name"),
                prompt = row.str("prompt"),
                schedule = schedule,
                scheduleDisplay = row.str("schedule_display"),
                enabled = row.bool("enabled"),
                state = row.str("state"),
                nextRunAt = instant(row["next_run_at"]),
                lastRunAt = instant(row["last_run_at"]),
                lastStatus = row.str("last_status"),
                lastError = row.str("last_error"),
                lastDeliveryError = row.str("last_delivery_error"),
                repeatTimes = repeat.long("times")?.toInt(),
                repeatCompleted = repeat.long("completed")?.toInt(),
                deliver = row.str("deliver"),
                skills = (row["skills"] as? JsonArray)?.mapNotNull { it.textOrNull() }.orEmpty(),
                model = row.str("model"),
                provider = row.str("provider"),
                profile = row.str("profile"),
                fireClaimAt = (fireClaim as? JsonObject)?.let { instant(it["at"]) },
                hasFireClaim = fireClaim is JsonObject,
                latestExecutionStatus = row.child("latest_execution").str("status"),
                schedulerHeartbeatAge = row.double("scheduler_heartbeat_age_s"),
            )
        }
    }
}
