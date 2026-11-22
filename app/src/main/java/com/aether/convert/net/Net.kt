package com.aether.convert.net

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aether.convert.AppDb
import com.aether.convert.Config
import com.aether.convert.Fix
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Supabase {
    private val mt = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun ok() = Config.url.startsWith("http") && Config.key.isNotBlank()

    fun push(rows: List<Fix>): Boolean {
        if (rows.isEmpty() || !ok()) return false
        val arr = JSONArray()
        for (r in rows) arr.put(JSONObject().apply {
            put("client_id", r.id)
            put("lat", r.lat)
            put("lon", r.lon)
            put("accuracy", r.accuracy.toDouble())
            put("altitude", r.altitude)
            put("vertical_accuracy", r.verticalAccuracy.toDouble())
            put("speed", r.speed.toDouble())
            put("bearing", r.bearing.toDouble())
            put("provider", r.provider)
            put("is_mock", r.isMock)
            put("activity", r.activity)
            put("battery_pct", r.batteryPct)
            put("charging", r.charging)
            put("source", r.source)
            put("captured_at", r.capturedAt)
            put("elapsed_nanos", r.elapsedNanos)
            put("device", r.device)
        })
        val req = Request.Builder()
            .url(Config.url + "/rest/v1/" + Config.TABLE)
            .addHeader("apikey", Config.key)
            .addHeader("Authorization", "Bearer " + Config.key)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .post(arr.toString().toRequestBody(mt))
            .build()
        return try {
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Throwable) {
            false
        }
    }
}

class SyncWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val dao = AppDb.get(applicationContext).fixes()
        val b = dao.pending(Config.BATCH)
        if (b.isEmpty()) return Result.success()
        if (!Supabase.ok()) return Result.retry()
        return if (Supabase.push(b)) {
            dao.markSynced(b.map { it.id })
            if (dao.pendingCount() > 0) Sync.nudge(applicationContext)
            Result.success()
        } else {
            Result.retry()
        }
    }
}

object Sync {

    private const val NOW = "sync-now"
    private const val PERIODIC = "sync-periodic"

    fun nudge(ctx: Context) {
        val r: OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, r)
    }

    fun periodic(ctx: Context) {
        val r = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, r)
    }
}