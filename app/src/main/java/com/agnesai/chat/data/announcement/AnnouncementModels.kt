package com.agnesai.chat.data.announcement

data class Announcement(
    val id: String,
    val title: String,
    val content: String,
    val priority: AnnouncementPriority = AnnouncementPriority.NORMAL,
    val publishAt: Long = 0L
)

enum class AnnouncementPriority {
    NORMAL,
    IMPORTANT
}
