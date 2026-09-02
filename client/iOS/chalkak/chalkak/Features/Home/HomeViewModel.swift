import Foundation
import Combine

enum HomeViewState: Equatable {
    case loading
    case content(HomeData)
    case failure(String)
}

@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var state: HomeViewState = .loading

    private let repository: HomeRepository
    private let dateProvider: () -> String
    private var loadTask: Task<Void, Never>?

    init(
        repository: HomeRepository,
        dateProvider: @escaping () -> String = { HomeViewModel.currentDateString() }
    ) {
        self.repository = repository
        self.dateProvider = dateProvider
        load()
    }

    deinit {
        loadTask?.cancel()
    }

    func load() {
        loadTask?.cancel()
        state = .loading
        let requestedDate = dateProvider()
        loadTask = Task { [weak self] in
            guard let self else { return }
            do {
                let home = try await self.repository.fetchHome(for: requestedDate)
                guard !Task.isCancelled else { return }
                self.state = .content(home)
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                self.state = .failure(error.localizedDescription)
            }
        }
    }

    nonisolated private static func currentDateString() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
