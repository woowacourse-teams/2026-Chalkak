import Foundation
import Testing
@testable import chalkak

@MainActor
struct FeedbackErrorMessageFormatterTests {
    @Test("접수 오류의 UTC 시각과 인접한 표기만 한국어 시각으로 바꾼다")
    func convertsUTCLabelAndTimestamp() {
        // Given 접수 제한 서버 문구와 UTC 시각
        // When 표시용 문구로 변환한다
        // Then 시각과 인접 UTC 표기만 바꾼다
        let message = "문의는 1분에 1건, 최근 24시간에 최대 5건 접수할 수 있습니다. 다음 접수 가능 시각(UTC): 2026-11-06T08:30:22Z"
        #expect(FeedbackErrorMessageFormatter.string(from: message) == "문의는 1분에 1건, 최근 24시간에 최대 5건 접수할 수 있습니다. 다음 접수 가능 시각: 11월 6일 오후 5:31")
    }

    @Test("소수 초와 서울 기준 연도 경계를 처리한다")
    func convertsFractionalSecondsAndYearBoundary() {
        // Given 소수 초가 있는 연말 UTC 시각
        // When 서울 기준으로 표시한다
        // Then 다음 해 1월 1일 오전 시각이다
        #expect(FeedbackErrorMessageFormatter.string(from: "가능 시각(UTC): 2026-12-31T23:30:22.123456Z") == "가능 시각: 1월 1일 오전 8:31")
    }

    @Test("초와 소수 초가 남으면 다음 분으로 올리고 정확한 분은 유지한다")
    func roundsUpToNextMinute() {
        // Given 정확한 분과 남은 초·소수 초 및 서울 자정 직전 시각
        // When 접수 가능 시각을 표시한다
        // Then 일찍 재시도하지 않도록 다음 분으로 올린다
        #expect(FeedbackErrorMessageFormatter.string(from: "2026-11-06T08:30:00Z") == "11월 6일 오후 5:30")
        #expect(FeedbackErrorMessageFormatter.string(from: "2026-11-06T08:30:00.000000Z") == "11월 6일 오후 5:30")
        #expect(FeedbackErrorMessageFormatter.string(from: "2026-11-06T08:30:00.001Z") == "11월 6일 오후 5:31")
        #expect(FeedbackErrorMessageFormatter.string(from: "2026-11-06T08:30:00.000000001Z") == "11월 6일 오후 5:31")
        #expect(FeedbackErrorMessageFormatter.string(from: "2026-12-31T14:59:59.999999999Z") == "1월 1일 오전 12:00")
    }

    @Test("UTC 표기 없는 시각도 바꾸되 관련 없는 UTC 설명은 유지한다")
    func preservesUnrelatedUTCText() {
        // Given 시각과 관계없는 UTC 설명
        // When UTC 시각을 변환한다
        // Then 다른 UTC 설명은 유지한다
        #expect(FeedbackErrorMessageFormatter.string(from: "서버 기준(UTC)을 참고하세요. 가능 시각: 2026-11-06T08:30:22Z") == "서버 기준(UTC)을 참고하세요. 가능 시각: 11월 6일 오후 5:31")
    }

    @Test("시각이 없거나 형식 또는 날짜가 잘못되면 UTC 표기를 포함한 원문을 유지한다")
    func keepsUnrecognizedMessage() {
        // Given 시각이 없거나 지원하지 않는 형식의 문구
        // When 표시용 문구로 변환한다
        // Then 원문과 UTC 표기를 그대로 유지한다
        let messages = [
            "피드백 내용이 올바르지 않습니다.",
            "가능 시각(UTC): 알 수 없음",
            "가능 시각(UTC): 2026-11-06T08:30:22+00:00",
            "가능 시각(UTC): 2026-13-06T08:30:22Z",
            "가능 시각(UTC): 2026-02-30T08:30:22Z",
            "가능 시각(UTC): 2026-11-06T08:30:22Zinvalid"
        ]
        for message in messages {
            #expect(FeedbackErrorMessageFormatter.string(from: message) == message)
        }
    }

    @Test("제출 400 안내는 변환한 문구를 전달하고 입력은 보존한다")
    func submissionUsesFormattedMessage() async {
        // Given 문의를 작성한 뒤 서버400과 UTC 시각을 받는다
        // When 제출 결과를 처리한다
        // Then 변환된 안내와 작성 내용을 보존한다
        let viewModel = FeedbackViewModel(submitFeedback: { _ in
            throw FeedbackAPIError.http(statusCode: 400, message: "다음 접수 가능 시각(UTC): 2026-11-06T08:30:22Z")
        })
        viewModel.updateContent("작성한 문의")
        await viewModel.submit()
        #expect(viewModel.event == .showMessage("다음 접수 가능 시각: 11월 6일 오후 5:31"))
        #expect(viewModel.viewState.content == "작성한 문의")
        #expect(!viewModel.viewState.isSubmitting)
    }
}
