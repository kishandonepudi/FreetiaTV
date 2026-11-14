package com.freetia.tv.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.filter
import com.freetia.tv.core.safety.ChannelFilter
import com.freetia.tv.data.database.FreetiaDatabase
import com.freetia.tv.data.model.ChannelEntity
import com.freetia.tv.data.model.GroupChannelCrossRef
import com.freetia.tv.data.model.GroupEntity
import com.freetia.tv.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Favourites, user groups, Recently Watched and search (§3.3), all per profile. The profile is the one chosen in
 * settings (`activeProfileId`, profile 1 by default).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepository(private val db: FreetiaDatabase, private val settings: SettingsRepository) {
    private val favourites = db.favouriteDao()
    private val groups = db.groupDao()
    private val recents = db.recentChannelDao()

    private val profileId: Flow<Long> = settings.activeProfileId.flow

    suspend fun currentProfileId(): Long = settings.activeProfileId.get()

    // Favourites
    fun favouriteIds(): Flow<Set<Long>> =
        profileId.flatMapLatest { favourites.observeFavouriteIds(it) }.map { it.toSet() }.distinctUntilChanged()

    /** Returns true if the channel is now a favourite. */
    suspend fun toggleFavourite(channelId: Long): Boolean = favourites.toggle(channelId, currentProfileId())

    fun pagedFavourites(): Flow<PagingData<ChannelEntity>> =
        combine(profileId, settings.childSafeMode.flow) { p, safe -> p to safe }.flatMapLatest { (p, safe) ->
            pager { favourites.pageFavouriteChannels(p, safe) }.filtered(safe, false)
        }

    // Groups
    fun observeGroups(): Flow<List<GroupEntity>> = profileId.flatMapLatest { groups.observeGroups(it) }

    fun observeGroupCount(groupId: Long): Flow<Int> = groups.observeGroupChannelCount(groupId)

    suspend fun createGroup(name: String): Long =
        groups.insertGroup(GroupEntity(name = name.trim(), profileId = currentProfileId()))

    suspend fun renameGroup(groupId: Long, name: String) {
        val group = groups.getGroupById(groupId) ?: return
        groups.updateGroup(group.copy(name = name.trim()))
    }

    suspend fun deleteGroup(groupId: Long) = groups.deleteGroupById(groupId)

    suspend fun group(groupId: Long): GroupEntity? = groups.getGroupById(groupId)

    suspend fun groupsContaining(channelId: Long): List<GroupEntity> =
        groups.getGroupsContainingChannel(channelId, currentProfileId())

    suspend fun setInGroup(groupId: Long, channelId: Long, member: Boolean) {
        if (member) groups.addChannelToGroup(GroupChannelCrossRef(groupId = groupId, channelId = channelId))
        else groups.removeChannelFromGroup(groupId, channelId)
    }

    fun pagedGroup(groupId: Long): Flow<PagingData<ChannelEntity>> =
        settings.childSafeMode.flow.flatMapLatest { safe ->
            pager { groups.pageGroupChannels(groupId, safe) }.filtered(safe, false)
        }

    // Recently Watched (last 20, trimmed by the DAO)
    fun observeRecents(): Flow<List<ChannelEntity>> =
        combine(profileId, settings.childSafeMode.flow) { p, safe -> p to safe }
            .flatMapLatest { (p, safe) -> recents.observeRecentChannels(p, safe) }

    suspend fun clearRecents() = recents.clearAllForProfile(currentProfileId())

    // Search: by channel name and group, across every playlist in priority order; Child Safe Mode applies.
    fun search(query: Flow<String>): Flow<PagingData<ChannelEntity>> =
        combine(query, settings.childSafeMode.flow, settings.hideDeadStreams.flow) { q, safe, dead -> Triple(q, safe, dead) }
            .flatMapLatest { (q, safe, dead) ->
                val term = sanitizeQuery(q)
                if (term.isEmpty()) flowOf(PagingData.empty())
                else pager { db.channelDao().pageSearch(term, 0, safe, dead) }.filtered(safe, dead)
            }

    private fun pager(source: () -> PagingSource<Int, ChannelEntity>): Flow<PagingData<ChannelEntity>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false), pagingSourceFactory = source).flow

    private fun Flow<PagingData<ChannelEntity>>.filtered(childSafe: Boolean, hideDead: Boolean) =
        map { page -> page.filter { !ChannelFilter.isHidden(it, childSafe, hideDead) } }

    companion object {
        const val PAGE_SIZE = 40

        /** LIKE wildcards typed by the user must not widen the search. */
        fun sanitizeQuery(raw: String): String = raw.trim().replace("%", "").replace("_", "").take(60)
    }
}
