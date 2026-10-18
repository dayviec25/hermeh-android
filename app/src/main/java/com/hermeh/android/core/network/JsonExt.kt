package com.hermeh.android.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

val HermesJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonElement?.arr(): JsonArray? = this as? JsonArray

fun JsonObject?.str(key: String): String? {
    val value = this?.get(key) as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    return if (value.isString) value.content else value.content.takeIf { it != "null" }
}

fun JsonObject?.bool(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.let {
    it.booleanOrNull ?: it.longOrNull?.let { n -> n != 0L }
}

fun JsonObject?.long(key: String): Long? = (this?.get(key) as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

fun JsonObject?.double(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull

fun JsonObject?.child(key: String): JsonObject? = this?.get(key) as? JsonObject

fun JsonObject?.list(key: String): List<JsonElement> = (this?.get(key) as? JsonArray).orEmpty()

fun JsonElement.textOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
