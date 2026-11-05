package com.stonefive.chalkak.core.analytics

/** 홈 A/B 테스트에서 비교하는 사용자 행동. position은 1부터 센다. */
sealed interface HomeInteraction {
    val eventName: String
    val parameters: Map<String, Any>
        get() = emptyMap()

    data class Issue(
        val bannerId: String,
        val position: Int,
    ) : HomeInteraction {
        override val eventName = "home_issue_clicked"
        override val parameters = mapOf("banner_id" to bannerId, "position" to position)
    }

    data class IssueSwipe(val position: Int) : HomeInteraction {
        override val eventName = "home_issue_swiped"
        override val parameters = mapOf("position" to position)
    }

    data class Ranking(
        val section: RankingSection,
        val action: RankingAction,
    ) : HomeInteraction {
        override val eventName = "home_ranking_clicked"
        override val parameters = mapOf("section" to section.analyticsName, "action" to action.analyticsName)
    }

    data class Record(val action: RecordAction) : HomeInteraction {
        override val eventName = "home_record_clicked"
        override val parameters = mapOf("action" to action.analyticsName)
    }

    data class Upload(val source: UploadSource) : HomeInteraction {
        override val eventName = "home_upload_clicked"
        override val parameters = mapOf("source" to source.analyticsName)
    }

    data object Feedback : HomeInteraction {
        override val eventName = "home_feedback_clicked"
    }

    data object Notification : HomeInteraction {
        override val eventName = "home_notification_clicked"
    }

    data object Login : HomeInteraction {
        override val eventName = "home_login_clicked"
    }

    enum class RankingSection(val analyticsName: String) {
        Yesterday("yesterday"),
        Trending("trending"),
    }

    enum class RankingAction(val analyticsName: String) {
        Photo("photo"),
        More("more"),
    }

    enum class RecordAction(val analyticsName: String) {
        More("more"),
        Date("date"),
    }

    enum class UploadSource(val analyticsName: String) {
        Hero("hero"),
        WeeklyRecord("weekly_record"),
    }
}
