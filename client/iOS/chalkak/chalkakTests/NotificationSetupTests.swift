import Foundation
import Testing
@testable import chalkak

@MainActor
struct NotificationSetupViewModelTests {
    @Test
    func defaultsToEveningReminder() {
        let viewModel = makeViewModel()

        #expect(viewModel.viewState.selectedOption == .evening)
        #expect(viewModel.viewState.selectedTime.hour == 18)
        #expect(viewModel.viewState.selectedTime.minute == 0)
    }

    @Test
    func selectingPresetUpdatesReminderTime() {
        let viewModel = makeViewModel()

        viewModel.select(.morning)

        #expect(viewModel.viewState.selectedOption == .morning)
        #expect(viewModel.viewState.selectedTime.hour == 8)
        #expect(viewModel.viewState.selectedTime.minute == 0)
    }

    @Test
    func updatingCustomTimeSelectsCustomOption() throws {
        let viewModel = makeViewModel()
        let calendar = Calendar(identifier: .gregorian)
        let date = try #require(calendar.date(from: DateComponents(hour: 21, minute: 35)))

        viewModel.updateCustomTime(date, calendar: calendar)

        #expect(viewModel.viewState.selectedOption == .custom)
        #expect(viewModel.viewState.selectedTime.hour == 21)
        #expect(viewModel.viewState.selectedTime.minute == 35)
    }

    @Test
    func confirmSchedulesDailyReminderAndCompletesSetupWhenAuthorized() async {
        let scheduler = SchedulerStub(isAuthorized: true)
        let store = StoreSpy()
        let viewModel = makeViewModel(scheduler: scheduler, store: store)
        viewModel.select(.noon)

        await viewModel.confirm()

        #expect(scheduler.scheduledTimes.count == 1)
        #expect(scheduler.scheduledTimes.first?.hour == 12)
        #expect(scheduler.scheduledTimes.first?.minute == 0)
        #expect(store.completedTimes.count == 1)
        #expect(store.completedTimes.first??.hour == 12)
        #expect(viewModel.event == .completed)
        #expect(!viewModel.viewState.isSaving)
    }

    @Test
    func confirmCompletesWithoutSchedulingWhenAuthorizationIsDenied() async {
        let scheduler = SchedulerStub(isAuthorized: false)
        let store = StoreSpy()
        let viewModel = makeViewModel(scheduler: scheduler, store: store)

        await viewModel.confirm()

        #expect(scheduler.scheduledTimes.isEmpty)
        #expect(store.completedTimes.count == 1)
        #expect(store.completedTimes.first! == nil)
        #expect(viewModel.event == .completed)
    }

    @Test
    func schedulingFailureKeepsSetupIncompleteForRetry() async {
        let scheduler = SchedulerStub(isAuthorized: true, error: TestError.failed)
        let store = StoreSpy()
        let viewModel = makeViewModel(scheduler: scheduler, store: store)

        await viewModel.confirm()

        #expect(store.completedTimes.isEmpty)
        #expect(viewModel.event == .showMessage("알림을 설정하지 못했어요. 다시 시도해 주세요."))
        #expect(!viewModel.viewState.isSaving)
    }

    @Test
    func skipCompletesSetupWithoutScheduling() {
        let scheduler = SchedulerStub(isAuthorized: true)
        let store = StoreSpy()
        let viewModel = makeViewModel(scheduler: scheduler, store: store)

        viewModel.skip()

        #expect(scheduler.scheduledTimes.isEmpty)
        #expect(store.completedTimes.count == 1)
        #expect(store.completedTimes.first! == nil)
        #expect(viewModel.event == .completed)
    }

    private func makeViewModel(
        scheduler: SchedulerStub? = nil,
        store: StoreSpy = StoreSpy()
    ) -> NotificationSetupViewModel {
        NotificationSetupViewModel(
            initialState: NotificationSetupViewState(),
            scheduler: scheduler ?? SchedulerStub(isAuthorized: true),
            store: store
        )
    }
}

struct NotificationSetupStoreTests {
    @Test
    func persistsCompletionAndSelectedTime() throws {
        let suiteName = "NotificationSetupStoreTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = NotificationSetupStore(defaults: defaults)

        store.completeSetup(time: DateComponents(hour: 7, minute: 25))

        #expect(store.hasCompletedSetup)
        #expect(defaults.integer(forKey: "notificationSetup.hour") == 7)
        #expect(defaults.integer(forKey: "notificationSetup.minute") == 25)
    }
}

struct NotificationSetupRouteTests {
    @Test
    func routesIncompleteSetupToNotificationScreenAfterAuthentication() {
        #expect(
            AppRouteResolver.destinationAfterAuthentication(hasCompletedNotificationSetup: false)
                == .notificationSetup
        )
    }

    @Test
    func routesCompletedSetupDirectlyHomeAfterAuthentication() {
        #expect(
            AppRouteResolver.destinationAfterAuthentication(hasCompletedNotificationSetup: true)
                == .home
        )
    }
}

@MainActor
private final class SchedulerStub: DailyNotificationScheduling {
    let isAuthorized: Bool
    let error: Error?
    private(set) var scheduledTimes: [DateComponents] = []

    init(isAuthorized: Bool, error: Error? = nil) {
        self.isAuthorized = isAuthorized
        self.error = error
    }

    func requestAuthorization() async throws -> Bool {
        isAuthorized
    }

    func scheduleDaily(at time: DateComponents) async throws {
        if let error { throw error }
        scheduledTimes.append(time)
    }
}

private final class StoreSpy: NotificationSetupStoring {
    private(set) var completedTimes: [DateComponents?] = []

    var hasCompletedSetup: Bool {
        !completedTimes.isEmpty
    }

    func completeSetup(time: DateComponents?) {
        completedTimes.append(time)
    }
}

private enum TestError: Error {
    case failed
}
