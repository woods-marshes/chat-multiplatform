package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.support.TestDb
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Profile-visibility semantics on a real database: the email of a FRIENDS or
 * PRIVATE profile must never reach a viewer whose relationship row is BLOCKED
 * or DELETED — only an actual FRIEND row counts as friendship.
 */
class UserProfileVisibilityDatabaseTest {

    private val userRepository = UserDataSourceImpl()
    private val contactRepository = ContactSourceImpl()
    private val settingsRepository = UserSettingDataSourceImpl()

    @BeforeTest
    fun freshDatabase() {
        TestDb.reset()
    }

    private suspend fun userWithVisibility(name: String, visibility: ProfileVisibility): Uuid {
        val userId = TestDb.user(name)
        settingsRepository.initSettings(userId)
        settingsRepository.updateSettings(userId, profileVisibility = visibility)
        return userId
    }

    @Test
    fun privateProfileHidesEmailEvenFromFriends() = runBlocking {
        val owner = userWithVisibility("owner-private", ProfileVisibility.PRIVATE)
        val friend = TestDb.user("friend")
        TestDb.contacts(owner, friend)

        val profile = userRepository.getUserProfileForViewer(owner, friend)

        assertNull(profile!!.email)
    }

    @Test
    fun friendsVisibilityShowsEmailOnlyOverAnActualFriendRow() = runBlocking {
        val owner = userWithVisibility("owner-friends", ProfileVisibility.FRIENDS)
        val friend = TestDb.user("friend")
        val stranger = TestDb.user("stranger")
        val blocked = TestDb.user("blocked")
        TestDb.contacts(owner, friend)
        // The blocker's own row is BLOCKED, as after blocking a former friend.
        contactRepository.upsertContactStatus(owner, blocked, ContactStatus.BLOCKED)

        assertNotNull(userRepository.getUserProfileForViewer(owner, friend)!!.email)
        assertNull(userRepository.getUserProfileForViewer(owner, stranger)!!.email)
        assertNull(userRepository.getUserProfileForViewer(owner, blocked)!!.email)
    }

    @Test
    fun publicProfileShowsEmailToAnyone(): Unit = runBlocking {
        val owner = userWithVisibility("owner-public", ProfileVisibility.PUBLIC)
        val stranger = TestDb.user("stranger")

        val profile = userRepository.getUserProfileForViewer(owner, stranger)

        assertEquals(owner, profile!!.id)
        assertNotNull(profile.email)
    }

    @Test
    fun contactListFriendsVisibilityHidesBlockedRowsEmail() = runBlocking {
        val viewer = TestDb.user("viewer")
        val blockedContact = userWithVisibility("blocked-contact", ProfileVisibility.FRIENDS)
        val friendContact = userWithVisibility("friend-contact", ProfileVisibility.FRIENDS)
        contactRepository.upsertContactStatus(viewer, blockedContact, ContactStatus.BLOCKED)
        // Mutual friendship: both mirrored rows are FRIEND.
        TestDb.contacts(viewer, friendContact)

        val contacts = contactRepository.getContactsWithUser(viewer)

        val emailByContact = contacts.associate { it.first.contactId to it.second.email }
        assertNotNull(emailByContact[friendContact])
        assertNull(emailByContact[blockedContact])
    }

    @Test
    fun contactListFriendsVisibilityHidesEmailOfContactsWhoBlockedTheViewer() = runBlocking {
        // B blocked A: only B→A is BLOCKED, A→B stays FRIEND. Authorization
        // follows the profile owner's row, so A must not read B's email —
        // same direction as getUserProfileForViewer.
        val viewer = TestDb.user("viewer")
        val owner = userWithVisibility("owner", ProfileVisibility.FRIENDS)
        TestDb.contacts(viewer, owner)
        contactRepository.upsertContactStatus(owner, viewer, ContactStatus.BLOCKED)

        val contacts = contactRepository.getContactsWithUser(viewer)

        val emailByContact = contacts.associate { it.first.contactId to it.second.email }
        assertNull(emailByContact[owner])
        assertNull(userRepository.getUserProfileForViewer(owner, viewer)!!.email)
    }

    @Test
    fun contactListFriendsVisibilityShowsEmailForMutualFriendsOnly() = runBlocking {
        val viewer = TestDb.user("viewer")
        val owner = userWithVisibility("owner", ProfileVisibility.FRIENDS)
        // Only the viewer's row exists (one-sided, e.g. after a mirror was
        // removed): the owner never authorized the viewer.
        contactRepository.upsertContactStatus(viewer, owner, ContactStatus.FRIEND)

        val contacts = contactRepository.getContactsWithUser(viewer)

        assertNull(contacts.single().second.email)
    }
}
