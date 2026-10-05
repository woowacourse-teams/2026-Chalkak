import Testing

/// 실행 횟수가 아니라 경과 시간으로 제한하고, 실패는 호출한 테스트 위치에 기록한다.
@MainActor
func waitUntil(
    timeout: Duration = .seconds(2),
    sourceLocation: SourceLocation = #_sourceLocation,
    _ condition: () -> Bool
) async throws {
    let clock = ContinuousClock()
    let deadline = clock.now.advanced(by: timeout)
    while !condition() && clock.now < deadline {
        try await clock.sleep(until: min(clock.now.advanced(by: .milliseconds(1)), deadline))
    }
    try Task.checkCancellation()
    try #require(
        condition(),
        "조건이 \(timeout) 안에 충족되지 않았습니다",
        sourceLocation: sourceLocation
    )
}
