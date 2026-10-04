import Testing

@MainActor
struct WaitUntilTests {
    @Test("이미 만족한 조건은 대기 시간이 없어도 성공한다")
    func returnsForSatisfiedCondition() async throws {
        try await waitUntil(timeout: .zero) { true }
    }

    @Test("100회 양보보다 늦게 완료되는 작업도 제한 시간 안에 기다린다")
    func waitsForDelayedCondition() async throws {
        var completed = false
        let task = Task {
            try await Task.sleep(for: .milliseconds(150))
            completed = true
        }
        defer { task.cancel() }

        try await waitUntil { completed }
        try await task.value
        #expect(completed)
    }

    @Test("충족되지 않는 조건은 제한 시간 후 호출 위치에 실패를 기록하고 중단한다")
    func reportsTimeoutAtCallSite() async throws {
        let sourceLocation = #_sourceLocation
        let clock = ContinuousClock()
        let start = clock.now
        var returnedNormally = false

        try await withKnownIssue {
            try await waitUntil(timeout: .milliseconds(20), sourceLocation: sourceLocation) { false }
            returnedNormally = true
        } matching: { issue in
            issue.sourceLocation == sourceLocation
        }

        #expect(!returnedNormally)
        #expect(start.duration(to: clock.now) >= .milliseconds(20))
    }

    @Test("취소된 대기는 시간 초과까지 반복하지 않고 취소를 전파한다")
    func propagatesCancellation() async {
        let task = Task {
            try await waitUntil { false }
        }
        task.cancel()

        await #expect(throws: CancellationError.self) {
            try await task.value
        }
    }
}
