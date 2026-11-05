package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.feedback.FeedbackApi
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSource
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSourceImpl
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackDetailResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackListResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionResponse
import com.stonefive.chalkak.domain.model.FeedbackAnswer
import com.stonefive.chalkak.domain.model.FeedbackInquiry
import com.stonefive.chalkak.domain.model.FeedbackInquiryException
import com.stonefive.chalkak.domain.model.FeedbackInquiryFailure
import com.stonefive.chalkak.domain.model.FeedbackSubmissionException
import com.stonefive.chalkak.domain.model.FeedbackSubmissionFailure
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class FeedbackRepositoryImplTest {
    private val feedbackDataSource = FakeFeedbackDataSource()
    private val repository = FeedbackRepositoryImpl(feedbackDataSource)

    // 실제 요청·응답을 보는 테스트는 MockWebServer에 붙인 저장소를 쓴다.
    private lateinit var server: MockWebServer
    private lateinit var remoteRepository: FeedbackRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FeedbackApi::class.java)
        remoteRepository = FeedbackRepositoryImpl(FeedbackDataSourceImpl(api, ApiRequestExecutor(json)))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `내 문의 목록 첫 페이지를 20건 단위로 요청하고 항목으로 바꾼다`() = runTest {
        // Given  {"currentPage":1,"pageSize":20,"hasNext":false,"feedbacks":[F1]} 응답
        server.enqueue(
            MockResponse().setBody(
                """
                {"currentPage":1,"pageSize":20,"hasNext":false,"feedbacks":[
                  {"feedbackId":"F1","content":"사진 업로드 후 화면이 멈춰요.","createdAt":"2026-11-03T06:00:00Z"}
                ]}
                """.trimIndent(),
            ),
        )

        // When   목록 첫 페이지를 불러온다
        val page = remoteRepository.getFeedbacks(page = 1)

        // Then   GET feedbacks?page=1&pageSize=20, 항목 F1의 내용·접수 시각 2026-11-03T06:00:00Z
        assertEquals("GET /api/v1/feedbacks?page=1&pageSize=20", server.takeRequestOrNull()?.line())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        assertEquals(
            listOf(FeedbackInquiry(id = "F1", content = "사진 업로드 후 화면이 멈춰요.", createdAt = "2026-11-03T06:00:00Z")),
            page.inquiries,
        )
    }

    @Test
    fun `목록 항목의 답변 여부를 답변 완료·답변 대기로 담는다`() = runTest {
        // Given  항목 A hasAnswer=true, B hasAnswer=false, C 필드 없음
        server.enqueue(
            MockResponse().setBody(
                """
                {"currentPage":1,"pageSize":20,"hasNext":false,"feedbacks":[
                  {"feedbackId":"A","content":"문의 A","createdAt":"2026-11-03T06:00:00Z","hasAnswer":true},
                  {"feedbackId":"B","content":"문의 B","createdAt":"2026-11-03T06:00:00Z","hasAnswer":false},
                  {"feedbackId":"C","content":"문의 C","createdAt":"2026-11-03T06:00:00Z"}
                ]}
                """.trimIndent(),
            ),
        )

        // When   목록을 불러온다
        val page = remoteRepository.getFeedbacks(page = 1)

        // Then   A 답변 완료, B 답변 대기, C 상태 없음(null)
        assertEquals(listOf(true, false, null), page.inquiries.map { it.hasAnswer })
    }

    @Test
    fun `답변이 있으면 상세 상태에 답변 내용과 시각을 담는다`() = runTest {
        // Given  answerContent="확인했어요", answeredAt="2026-11-04T01:00:00Z"
        server.enqueue(
            MockResponse().setBody(detailJson(answerContent = "\"확인했어요\"", answeredAt = "\"2026-11-04T01:00:00Z\"")),
        )

        // When   상세를 불러온다
        val detail = remoteRepository.getFeedbackDetail("F")

        // Then   답변 내용 "확인했어요", 답변 시각 2026-11-04T01:00:00Z
        assertEquals("GET /api/v1/feedbacks/F", server.takeRequestOrNull()?.line())
        assertEquals(FeedbackAnswer(content = "확인했어요", answeredAt = "2026-11-04T01:00:00Z"), detail.answer)
    }

    @Test
    fun `답변 필드가 둘 다 null이면 미답변 상태가 된다`() = runTest {
        // Given  answerContent=null, answeredAt=null
        server.enqueue(MockResponse().setBody(detailJson(answerContent = "null", answeredAt = "null")))

        // When   상세를 불러온다
        val detail = remoteRepository.getFeedbackDetail("F")

        // Then   답변 없음(null)
        assertEquals("GET /api/v1/feedbacks/F", server.takeRequestOrNull()?.line())
        assertEquals("사진 업로드 후 화면이 멈춰요.", detail.content)
        assertNull(detail.answer)
    }

    @Test
    fun `삭제 204 빈 본문을 성공으로 처리한다`() = runTest {
        // Given  문의 B, 삭제 요청이 본문 없는 204
        server.enqueue(MockResponse().setResponseCode(204))

        // When   B를 삭제한다
        val error = runCatching { remoteRepository.deleteFeedback("B") }.exceptionOrNull()

        // Then   DELETE feedbacks/B, 성공(예외 없음)
        assertEquals("DELETE /api/v1/feedbacks/B", server.takeRequestOrNull()?.line())
        assertNull(error)
    }

    @Test
    fun `제출이 400이면 서버 메시지를 그대로 안내한다`() = runTest {
        // Given  400 / BUSINESS_ERROR, message=접수 제한 안내
        val serverMessage = "문의는 1분에 1건, 최근 24시간에 최대 5건 접수할 수 있습니다. 다음 접수 가능 시각(UTC): 2026-11-03T06:01:00Z"
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"errorCode":"BUSINESS_ERROR","message":"$serverMessage"}"""),
        )

        // When   보낸다
        val error = runCatching { remoteRepository.submitFeedback("피드백") }.exceptionOrNull()

        // Then   잘못된 내용 실패에 서버 message를 담는다
        val submissionError = error as FeedbackSubmissionException
        assertEquals(FeedbackSubmissionFailure.INVALID_CONTENT, submissionError.reason)
        assertEquals(serverMessage, submissionError.serverMessage)
    }

    @Test
    fun `글자 수는 code point로 세어 이모지 1,000자까지 보낼 수 있다`() = runTest {
        // Given  "😀" 1,000개(UTF-16 2,000자) / 1,001개
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"feedbackId":"F1","createdAt":"2026-11-03T06:00:00Z"}"""),
        )

        // When   제출한다
        val thousandError = runCatching { remoteRepository.submitFeedback("😀".repeat(1_000)) }.exceptionOrNull()
        val thousandRequest = server.takeRequestOrNull()
        val overLimitError = runCatching { remoteRepository.submitFeedback("😀".repeat(1_001)) }.exceptionOrNull()

        // Then   1,000개는 제출 요청됨, 1,001개는 잘못된 내용(요청 없음)
        assertNull(thousandError)
        assertEquals("POST /api/v1/feedbacks", thousandRequest?.line())
        assertEquals(
            FeedbackSubmissionFailure.INVALID_CONTENT,
            (overLimitError as FeedbackSubmissionException).reason,
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `목록·상세·삭제의 404는 찾을 수 없음, 401은 재인증 실패로 바꾼다`() = runTest {
        // Given  상세 404, 삭제 404, 목록 401
        server.enqueue(MockResponse().setResponseCode(404).setBody(NOT_FOUND_BODY))
        server.enqueue(MockResponse().setResponseCode(404).setBody(NOT_FOUND_BODY))
        server.enqueue(MockResponse().setResponseCode(401))

        // When   상세·삭제·목록을 요청한다
        val detailError = runCatching { remoteRepository.getFeedbackDetail("F") }.exceptionOrNull()
        val deleteError = runCatching { remoteRepository.deleteFeedback("F") }.exceptionOrNull()
        val listError = runCatching { remoteRepository.getFeedbacks(page = 1) }.exceptionOrNull()

        // Then   NOT_FOUND, NOT_FOUND, UNAUTHORIZED
        assertEquals(FeedbackInquiryFailure.NOT_FOUND, (detailError as? FeedbackInquiryException)?.reason)
        assertEquals(FeedbackInquiryFailure.NOT_FOUND, (deleteError as? FeedbackInquiryException)?.reason)
        assertEquals(FeedbackInquiryFailure.UNAUTHORIZED, (listError as? FeedbackInquiryException)?.reason)
    }

    @Test
    fun `내용의 공백을 제거한 뒤 제출한다`() = runTest {
        repository.submitFeedback("  피드백 내용  ")

        assertEquals("피드백 내용", feedbackDataSource.submittedContent)
    }

    @Test
    fun `빈 내용이면 잘못된 내용 오류를 던진다`() = runTest {
        val error = runCatching { repository.submitFeedback("   ") }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.INVALID_CONTENT,
            (error as FeedbackSubmissionException).reason,
        )
        assertNull(feedbackDataSource.submittedContent)
    }

    @Test
    fun `최대 길이를 초과하면 잘못된 내용 오류를 던진다`() = runTest {
        val error = runCatching { repository.submitFeedback("가".repeat(1_001)) }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.INVALID_CONTENT,
            (error as FeedbackSubmissionException).reason,
        )
        assertNull(feedbackDataSource.submittedContent)
    }

    @Test
    fun `401 응답을 인증 오류로 변환한다`() = runTest {
        feedbackDataSource.result = ApiResult.Failure(ApiError.Http(401, null))

        val error = runCatching { repository.submitFeedback("피드백") }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.UNAUTHORIZED,
            (error as FeedbackSubmissionException).reason,
        )
    }

    @Test
    fun `400 응답을 잘못된 내용 오류로 변환한다`() = runTest {
        feedbackDataSource.result = ApiResult.Failure(ApiError.Http(400, "INVALID"))

        val error = runCatching { repository.submitFeedback("피드백") }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.INVALID_CONTENT,
            (error as FeedbackSubmissionException).reason,
        )
    }

    @Test
    fun `네트워크 오류를 네트워크 실패로 변환한다`() = runTest {
        feedbackDataSource.result = ApiResult.Failure(ApiError.Network)

        val error = runCatching { repository.submitFeedback("피드백") }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.NETWORK,
            (error as FeedbackSubmissionException).reason,
        )
    }
}

private fun MockWebServer.takeRequestOrNull(): RecordedRequest? = takeRequest(1, TimeUnit.SECONDS)

private fun RecordedRequest.line(): String = "$method $path"

private fun detailJson(
    answerContent: String,
    answeredAt: String,
): String = """
    {"feedbackId":"F","content":"사진 업로드 후 화면이 멈춰요.","createdAt":"2026-11-03T06:00:00Z",
     "answerContent":$answerContent,"answeredAt":$answeredAt}
""".trimIndent()

private const val NOT_FOUND_BODY = """{"errorCode":"BUSINESS_ERROR","message":"문의를 찾을 수 없습니다."}"""

private class FakeFeedbackDataSource : FeedbackDataSource {
    var submittedContent: String? = null
    var result: ApiResult<FeedbackSubmissionResponse> = ApiResult.Success(
        FeedbackSubmissionResponse(
            feedbackId = "feedback-id",
            createdAt = "2026-09-16T00:00:00Z",
        ),
    )

    override suspend fun submitFeedback(content: String): ApiResult<FeedbackSubmissionResponse> {
        submittedContent = content
        return result
    }

    override suspend fun getFeedbacks(
        page: Int,
        pageSize: Int,
    ): ApiResult<FeedbackListResponse> = ApiResult.Failure(ApiError.InvalidResponse)

    override suspend fun getFeedbackDetail(feedbackId: String): ApiResult<FeedbackDetailResponse> =
        ApiResult.Failure(ApiError.InvalidResponse)

    override suspend fun deleteFeedback(feedbackId: String): ApiResult<Unit> =
        ApiResult.Failure(ApiError.InvalidResponse)
}
