package com.r0ine.auraforge.arcane

import com.r0ine.auraforge.core.AltarRecord
import com.r0ine.auraforge.forge.BlockPos
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class Altar(
    val id: UUID,
    val owner: UUID,
    ownerName: String,
    val pos: BlockPos,
    combines: Int,
) {
    @Volatile var ownerName = ownerName
    @Volatile var combines = combines
        private set

    val activeUser = AtomicReference<UUID?>(null)
    @Volatile internal var visuals: AltarVisuals? = null

    val busy: Boolean get() = activeUser.get() != null

    fun recordCombine() { combines++ }

    fun record(): AltarRecord = AltarRecord(
        id = id.toString(),
        owner = owner.toString(),
        ownerName = ownerName,
        world = pos.world,
        x = pos.x,
        y = pos.y,
        z = pos.z,
        combines = combines,
    )
}
