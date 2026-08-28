package com.agnesai.chat.ui.myworks

import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.works.MyWork

/** 作品筛选类型。 */
enum class WorkFilter(val label: String) {
    ALL("全部"),
    IMAGE("图片"),
    VIDEO("视频")
}

data class MyWorksUiState(
    val loading: Boolean = true,
    val works: List<MyWork> = emptyList(),
    val filter: WorkFilter = WorkFilter.ALL,
    val error: String? = null,
    val pendingDeleteWork: MyWork? = null,
    val detailWork: MyWork? = null,
    val deleting: Boolean = false
) {
    /** 当前筛选条件下应展示的作品。 */
    val visibleWorks: List<MyWork>
        get() = when (filter) {
            WorkFilter.ALL -> works
            WorkFilter.IMAGE -> works.filter { it.type == SessionType.IMAGE }
            WorkFilter.VIDEO -> works.filter { it.type == SessionType.VIDEO }
        }
}
