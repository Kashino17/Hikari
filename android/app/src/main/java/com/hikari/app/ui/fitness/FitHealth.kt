package com.hikari.app.ui.fitness

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gesundheitsdaten aus Health Connect (Handy, Uhr, Samsung Health …): je Tag Schritte, Schlaf der
 * Nacht davor, Ruhepuls und aktive Kalorien. Nur lesen. Der Coach nutzt Schlaf und Ruhepuls für die
 * Tagesform (kurze Nacht oder erhöhter Puls → leichter).
 */
object FitHealth {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    /** unavailable | update | needs_permission | partial | granted */
    suspend fun status(ctx: Context): String {
        return when (HealthConnectClient.getSdkStatus(ctx)) {
            HealthConnectClient.SDK_UNAVAILABLE -> "unavailable"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update"
            else -> {
                val granted = HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
                when {
                    granted.containsAll(PERMISSIONS) -> "granted"
                    granted.any { it in PERMISSIONS } -> "partial"
                    else -> "needs_permission"
                }
            }
        }
    }

    /** Die letzten [days] Tage (heute eingeschlossen) als JSON-Liste. Fehlende Werte = null. */
    suspend fun read(ctx: Context, days: Int): String {
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = client.permissionController.getGrantedPermissions()
        val zone = ZoneId.systemDefault()
        val out = JSONArray()
        val today = LocalDate.now(zone)
        for (i in (days - 1) downTo 0) {
            val d = today.minusDays(i.toLong())
            val start = d.atStartOfDay(zone).toInstant()
            val end = d.plusDays(1).atStartOfDay(zone).toInstant()
            val o = JSONObject().put("day", d.toString())
            runCatching {
                if (HealthPermission.getReadPermission(StepsRecord::class) in granted || HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) {
                    val metrics = buildSet {
                        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) add(StepsRecord.COUNT_TOTAL)
                        if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                    }
                    val agg = client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(start, end)))
                    agg[StepsRecord.COUNT_TOTAL]?.let { o.put("steps", it) }
                    agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { o.put("activeKcal", Math.round(it.inKilocalories)) }
                }
            }
            runCatching {
                if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
                    // Schlaf der Nacht, die an diesem Tag endet: Beginn ab 18 Uhr am Vortag, Ende bis 14 Uhr.
                    val from = d.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
                    val to = d.atTime(14, 0).atZone(zone).toInstant()
                    val recs = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from, to))).records
                    // Mehrere Quellen (Uhr + Samsung Health) zeichnen dieselbe Nacht auf → Zeiträume
                    // zusammenlegen statt addieren, sonst zählt der Schlaf doppelt.
                    var min = 0L
                    var curS: java.time.Instant? = null
                    var curE: java.time.Instant? = null
                    for (r in recs.sortedBy { it.startTime }) {
                        if (curE == null || r.startTime.isAfter(curE)) {
                            if (curS != null && curE != null) min += java.time.Duration.between(curS, curE).toMinutes()
                            curS = r.startTime; curE = r.endTime
                        } else if (r.endTime.isAfter(curE)) curE = r.endTime
                    }
                    if (curS != null && curE != null) min += java.time.Duration.between(curS, curE).toMinutes()
                    if (min > 0) o.put("sleepMin", min)
                }
            }
            runCatching {
                if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
                    val recs = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(start, end))).records
                    if (recs.isNotEmpty()) o.put("restingHr", recs.map { it.beatsPerMinute }.average().let { Math.round(it * 10) / 10.0 })
                }
            }
            out.put(o)
        }
        return out.toString()
    }
}
