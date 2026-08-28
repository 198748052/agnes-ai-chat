package com.agnesai.chat.ui.announcement

import com.agnesai.chat.data.announcement.Announcement

data class AnnouncementUiState(
    val announcements: List<Announcement> = emptyList(),
    val loading: Boolean = false
)
