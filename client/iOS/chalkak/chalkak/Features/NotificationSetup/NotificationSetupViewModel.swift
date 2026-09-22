import Foundation
import Observation

@MainActor
@Observable
final class NotificationSetupViewModel {
    private(set) var viewState: NotificationSetupViewState
    private(set) var event: NotificationSetupEvent?

    private let scheduler: any DailyNotificationScheduling
    private let store: any NotificationSetupStoring

    init() {
        viewState = NotificationSetupViewState()
        scheduler = UserNotificationDailyScheduler()
        store = NotificationSetupStore()
    }

    init(
        initialState: NotificationSetupViewState,
        scheduler: any DailyNotificationScheduling,
        store: any NotificationSetupStoring
    ) {
        viewState = initialState
        self.scheduler = scheduler
        self.store = store
    }

    func select(_ option: NotificationTimeOption) {
        guard !viewState.isSaving else { return }
        viewState.selectedOption = option
    }

    func updateCustomTime(_ date: Date, calendar: Calendar = .current) {
        viewState.customTime = calendar.dateComponents([.hour, .minute], from: date)
        viewState.selectedOption = .custom
    }

    func confirm() async {
        guard !viewState.isSaving else { return }

        viewState.isSaving = true
        do {
            let isAuthorized = try await scheduler.requestAuthorization()
            if isAuthorized {
                try await scheduler.scheduleDaily(at: viewState.selectedTime)
            }
            store.completeSetup(time: isAuthorized ? viewState.selectedTime : nil)
            viewState.isSaving = false
            event = .completed
        } catch is CancellationError {
            viewState.isSaving = false
        } catch {
            viewState.isSaving = false
            event = .showMessage("알림을 설정하지 못했어요. 다시 시도해 주세요.")
        }
    }

    func skip() {
        guard !viewState.isSaving else { return }
        store.completeSetup(time: nil)
        event = .completed
    }

    func consumeEvent() {
        event = nil
    }
}
