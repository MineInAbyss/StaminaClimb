package com.mineinabyss.staminaclimb.rope

import com.mineinabyss.deeperworld.datastructures.Section
import com.mineinabyss.deeperworld.sections.correspondingLocation
import com.mineinabyss.deeperworld.sections.inSectionTransition
import com.mineinabyss.deeperworld.sections.section
import com.mineinabyss.deeperworld.sections.withSection
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector

/** [source] and [target] are block bounds, so a location is inside one when its block is */
data class SectionLink(val world: World, val delta: Vector, val source: BoundingBox, val target: BoundingBox) {
    fun toTarget(location: Location): Location = location.clone().add(delta).also { it.world = world }
}

object RopeSections {
    fun regionAt(location: Location): BoundingBox? = location.section?.bounds

    fun inTransition(location: Location) = location.inSectionTransition

    fun linkAt(location: Location): SectionLink? {
        val at = location.withSection ?: return null
        return link(at.section, at.linkedSection ?: return null, location, at.linkedLocation ?: return null)
    }

    fun linkBelow(location: Location): SectionLink? {
        val section = location.section ?: return null
        val below = section.below?.takeIf { section.overlapWith(it) != null } ?: return null
        // DeeperWorld only translates locations inside an overlap, and a section's bottom always lies in the one below
        val inOverlap = location.clone().apply { y = section.region.min.y.toDouble() }
        return link(section, below, inOverlap, inOverlap.correspondingLocation ?: return null)
    }

    fun linkedLocation(location: Location): Location? = location.correspondingLocation

    /** The bottom of an overlap has nothing under it in its own section, so items dropped there land in the section below */
    fun dropLocation(location: Location): Location {
        val at = location.withSection ?: return location
        if (at.linkedSection != at.section.below) return location
        return at.linkedLocation ?: location
    }

    private fun link(section: Section, linked: Section, inOverlap: Location, corresponding: Location) =
        SectionLink(linked.world, corresponding.toVector().subtract(inOverlap.toVector()), section.bounds, linked.bounds)

    private val Section.bounds
        get() = region.run { BoundingBox(min.x.toDouble(), min.y.toDouble(), min.z.toDouble(), max.x + 1.0, max.y + 1.0, max.z + 1.0) }
}
