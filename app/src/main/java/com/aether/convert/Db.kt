package com.aether.convert

import android.content.Context
import androidx.room.*
import com.aether.convert.net.Sync

@Entity(tableName = "fixes", indices = [Index("synced"), Index("capturedAt")])
data class Fix(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double, val lon: Double, val accuracy: Float, val altitude: Double,
    val verticalAccuracy: Float, val speed: Float, val bearing: Float,
    val provider: String, val isMock: Boolean, val activity: String,
    val batteryPct: Int, val charging: Boolean, val source: String,
    val capturedAt: Long, val elapsedNanos: Long, val device: String,
    val synced: Boolean = false
)

@Dao
interface FixDao {
    @Insert suspend fun insert(f: Fix): Long
    @Query("SELECT * FROM fixes WHERE synced = 0 ORDER BY id ASC LIMIT :n")
    suspend fun pending(n: Int): List<Fix>
    @Query("UPDATE fixes SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
    @Query("SELECT COUNT(*) FROM fixes WHERE synced = 0")
    suspend fun pendingCount(): Int
    @Query("DELETE FROM fixes WHERE synced = 1 AND capturedAt < :cut")
    suspend fun prune(cut: Long)
}

@Database(entities = [Fix::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun fixes(): FixDao
    companion object {
        @Volatile private var i: AppDb? = null
        fun get(c: Context): AppDb = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "uc.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration().build().also { i = it }
        }
    }
}

object Repo {
    suspend fun save(ctx: Context, f: Fix) {
        AppDb.get(ctx).fixes().insert(f)
        val p = Prefs(ctx)
        p.lastLat = f.lat; p.lastLon = f.lon; p.lastAt = f.capturedAt
        Sync.nudge(ctx)
        AppDb.get(ctx).fixes().prune(System.currentTimeMillis() - Config.KEEP_DAYS * 86400000L)
    }
    suspend fun beat(ctx: Context) {
        val p = Prefs(ctx)
        val (b, c) = DeviceInfo.battery(ctx)
        save(ctx, Fix(lat = p.lastLat, lon = p.lastLon, accuracy = -1f, altitude = 0.0,
            verticalAccuracy = -1f, speed = -1f, bearing = -1f, provider = "heartbeat",
            isMock = false, activity = p.activity, batteryPct = b, charging = c,
            source = "beat", capturedAt = System.currentTimeMillis(),
            elapsedNanos = System.nanoTime(), device = DeviceInfo.tag()))
    }
}