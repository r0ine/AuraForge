package com.r0ine.auraforge.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * Coroutine'i bir chunk'ın sahibi olan bölge thread'inde çalıştırır.
 * Paper'da bu ana thread'e, Folia'da ilgili bölgeye denk gelir.
 */
class RegionDispatcher(
    private val plugin: Plugin,
    private val world: World,
    private val chunkX: Int,
    private val chunkZ: Int,
) : CoroutineDispatcher() {

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // Eklenti kapanırken zamanlayıcı görev kabul etmez; scope zaten iptal edilmiş olur.
        if (!plugin.isEnabled) return
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, block)
    }

    suspend fun sleep(ticks: Long) = suspendCancellableCoroutine<Unit> { cont ->
        val task = Bukkit.getRegionScheduler()
            .runDelayed(plugin, world, chunkX, chunkZ, { cont.resume(Unit) }, ticks.coerceAtLeast(1))
        cont.invokeOnCancellation { task.cancel() }
    }
}

/**
 * Coroutine'i bir varlığın (çoğunlukla oyuncunun) thread'ine bağlar.
 * Varlık dünyadan ayrılırsa coroutine iptal edilir, böylece askıda kalan iş birikmez.
 */
class EntityDispatcher(
    private val plugin: Plugin,
    private val entity: Entity,
) : CoroutineDispatcher() {

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !Bukkit.isOwnedByCurrentRegion(entity)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!plugin.isEnabled) return
        val retired = Runnable {
            context.cancel()
            if (plugin.isEnabled) Bukkit.getGlobalRegionScheduler().execute(plugin, block)
        }
        if (!entity.scheduler.execute(plugin, block, retired, 1L)) retired.run()
    }

    suspend fun sleep(ticks: Long) = suspendCancellableCoroutine<Unit> { cont ->
        val task = entity.scheduler.runDelayed(
            plugin,
            { cont.resume(Unit) },
            { cont.cancel() },
            ticks.coerceAtLeast(1),
        )
        if (task == null) cont.cancel() else cont.invokeOnCancellation { task.cancel() }
    }
}

fun Plugin.runAt(location: Location, task: () -> Unit) {
    if (!isEnabled) return
    if (Bukkit.isOwnedByCurrentRegion(location)) {
        task()
    } else {
        Bukkit.getRegionScheduler().execute(this, location, Runnable { task() })
    }
}

fun Plugin.runFor(entity: Entity, task: () -> Unit) {
    if (!isEnabled) return
    if (Bukkit.isOwnedByCurrentRegion(entity)) {
        task()
    } else {
        entity.scheduler.execute(this, Runnable { task() }, null, 1L)
    }
}
