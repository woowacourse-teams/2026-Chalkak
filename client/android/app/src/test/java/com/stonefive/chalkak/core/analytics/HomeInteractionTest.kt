package com.stonefive.chalkak.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeInteractionTest {
    @Test
    fun `issue click carries banner and position`() {
        val interaction = HomeInteraction.Issue(bannerId = "banner-1", position = 2)

        assertEquals("home_issue_clicked", interaction.eventName)
        assertEquals(mapOf("banner_id" to "banner-1", "position" to 2), interaction.parameters)
    }

    @Test
    fun `ranking click separates section and action`() {
        val interaction = HomeInteraction.Ranking(
            HomeInteraction.RankingSection.Yesterday,
            HomeInteraction.RankingAction.More,
        )

        assertEquals("home_ranking_clicked", interaction.eventName)
        assertEquals(mapOf("section" to "yesterday", "action" to "more"), interaction.parameters)
    }

    @Test
    fun `record and upload clicks carry where they started`() {
        assertEquals(
            mapOf("action" to "date"),
            HomeInteraction.Record(HomeInteraction.RecordAction.Date).parameters,
        )
        assertEquals(
            mapOf("source" to "weekly_record"),
            HomeInteraction.Upload(HomeInteraction.UploadSource.WeeklyRecord).parameters,
        )
    }

    @Test
    fun `simple clicks have no parameters`() {
        assertEquals("home_feedback_clicked", HomeInteraction.Feedback.eventName)
        assertEquals("home_notification_clicked", HomeInteraction.Notification.eventName)
        assertEquals("home_login_clicked", HomeInteraction.Login.eventName)
        assertEquals(emptyMap<String, Any>(), HomeInteraction.Login.parameters)
    }
}
