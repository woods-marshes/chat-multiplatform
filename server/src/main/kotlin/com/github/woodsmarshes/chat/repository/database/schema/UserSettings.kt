package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.FriendRequestPolicy
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object UserSettings : Table("user_settings") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)

    val allowSearch = bool("privacy_allow_search").default(true)
    val friendRequestPolicy = enumerationByName(
        "privacy_friend_req_policy",
        32,
        FriendRequestPolicy::class
    )
        .default(FriendRequestPolicy.NEED_APPROVAL)
    val showOnlineStatus = bool("privacy_online_status").default(true)
    val allowStrangerChat = bool("privacy_allow_stranger_chat").default(true)
    val profileVisibility = enumerationByName(
        "privacy_profile_visibility",
        20,
        ProfileVisibility::class
    )
        .default(ProfileVisibility.PUBLIC)

    val preferences = jsonb<UserPreference>("preferences", ProjectJson).default(UserPreference())
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId)
}