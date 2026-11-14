package com.freetia.tv.core.safety

import com.freetia.tv.data.database.ChannelRef
import com.freetia.tv.data.model.ChannelEntity
import java.util.Locale

/**
 * The one place that decides which channels are adult (§3.7) and which are hidden or skipped. Used by the
 * playlist sync (detection), the channel list Flow, search and channel Up/Down.
 */
object ChannelFilter {

    /**
     * Whole-word matches only, so ordinary names that merely contain the letters ("Adulting", "XXL Sports",
     * "118+") stay visible. Wrongly hiding a family channel is the worse failure for a remote-only audience.
     */
    private val ADULT_WORDS = Regex(
        """(?<![\p{L}\p{N}])(adult|adults|xxx|porn|porno|erotic|erotica|nsfw|18\+|\+18)(?![\p{L}\p{N}+])""",
    )

    fun isAdultText(groupTitle: String, name: String): Boolean {
        val haystack = "$groupTitle | $name".lowercase(Locale.ROOT)
        return ADULT_WORDS.containsMatchIn(haystack)
    }

    /** Detected at sync time or marked by the user. */
    fun isAdult(channel: ChannelEntity): Boolean = channel.isAdult || channel.isMarkedAdult

    fun isHidden(channel: ChannelEntity, childSafe: Boolean, hideDead: Boolean = false): Boolean =
        (childSafe && isAdult(channel)) || (hideDead && channel.isDead)

    /** Every list and search result goes through this. */
    fun visible(channels: List<ChannelEntity>, childSafe: Boolean, hideDead: Boolean = false): List<ChannelEntity> =
        if (!childSafe && !hideDead) channels else channels.filterNot { isHidden(it, childSafe, hideDead) }

    /** Channel Up/Down skips adult channels under Child Safe Mode, and locked ones unless the session is unlocked. */
    fun skipWhenStepping(
        channel: ChannelEntity,
        childSafe: Boolean,
        lockedIds: Set<Long>,
        sessionUnlocked: Boolean,
    ): Boolean = (childSafe && isAdult(channel)) || (!sessionUnlocked && channel.id in lockedIds)

    // ChannelRef twins of the rules above, for the slim playlist list (AppSession, player Up/Down, guide).
    fun isAdult(channel: ChannelRef): Boolean = channel.isAdult || channel.isMarkedAdult

    fun isHidden(channel: ChannelRef, childSafe: Boolean, hideDead: Boolean = false): Boolean =
        (childSafe && isAdult(channel)) || (hideDead && channel.isDead)

    @JvmName("visibleRefs")
    fun visible(channels: List<ChannelRef>, childSafe: Boolean, hideDead: Boolean = false): List<ChannelRef> =
        if (!childSafe && !hideDead) channels else channels.filterNot { isHidden(it, childSafe, hideDead) }

    fun skipWhenStepping(
        channel: ChannelRef,
        childSafe: Boolean,
        lockedIds: Set<Long>,
        sessionUnlocked: Boolean,
    ): Boolean = (childSafe && isAdult(channel)) || (!sessionUnlocked && channel.id in lockedIds)
}
