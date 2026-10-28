import Foundation
import Observation

@MainActor
@Observable
final class SettingsViewModel {
    typealias AuthenticationProvider = @MainActor @Sendable () -> Bool
    typealias SignatureLoader = @MainActor @Sendable () async throws -> URL?
    typealias SignatureUpdater = @MainActor @Sendable (Data) async throws -> URL
    typealias AccountAction = @MainActor @Sendable () async throws -> Void

    private(set) var viewState: SettingsViewState
    private(set) var event: SettingsEvent?

    private let isAuthenticated: AuthenticationProvider
    private let loadSignature: SignatureLoader
    private let updateSignatureHandler: SignatureUpdater
    private let logout: AccountAction
    private let withdraw: AccountAction
    private var isLoadingSignature = false
    private var signatureRevision = 0

    init(
        initialState: SettingsViewState? = nil,
        isAuthenticated: @escaping AuthenticationProvider = { false },
        loadSignature: @escaping SignatureLoader = { nil },
        updateSignature: @escaping SignatureUpdater = { _ in
            throw SettingsAPIError.configuration
        },
        logout: @escaping AccountAction = {},
        withdraw: @escaping AccountAction = {}
    ) {
        viewState = initialState ?? SettingsViewState()
        self.isAuthenticated = isAuthenticated
        self.loadSignature = loadSignature
        updateSignatureHandler = updateSignature
        self.logout = logout
        self.withdraw = withdraw
    }

    func load() async {
        guard !isLoadingSignature else { return }
        let authenticated = isAuthenticated()

        guard authenticated else {
            viewState.isLoading = false
            viewState.isLoggedIn = false
            viewState.signatureURL = nil
            return
        }

        let revision = signatureRevision
        isLoadingSignature = true
        defer { isLoadingSignature = false }
        viewState.isLoading = !viewState.isLoggedIn
        viewState.isLoggedIn = true
        do {
            let signatureURL = try await loadSignature()
            guard revision == signatureRevision else { return }
            viewState.signatureURL = signatureURL
            viewState.isLoading = false
        } catch SettingsAPIError.unauthorized {
            guard revision == signatureRevision else { return }
            await finishSignedOut()
        } catch is CancellationError {
            guard revision == signatureRevision else { return }
            viewState.isLoading = false
            return
        } catch {
            guard revision == signatureRevision else { return }
            viewState.isLoading = false
            publish(.showMessage("사인을 불러오지 못했어요. 다시 시도해 주세요."))
        }
    }

    func showLogoutDialog() {
        viewState.accountDialog = .logout
    }

    func updateSignature(_ pngData: Data) async throws {
        do {
            let signatureURL = try await updateSignatureHandler(pngData)
            signatureRevision += 1
            viewState.signatureURL = signatureURL
            viewState.isLoading = false
        } catch SettingsAPIError.unauthorized {
            await finishSignedOut()
            throw SettingsAPIError.unauthorized
        } catch {
            throw error
        }
    }

    func showWithdrawDialog() {
        viewState.accountDialog = .withdraw
    }

    func dismissAccountDialog() {
        viewState.accountDialog = nil
    }

    func confirmAccountAction(for confirmedDialog: SettingsAccountDialog? = nil) async {
        // A native alert dismisses its presentation binding as soon as a button is tapped.
        // Use the dialog captured by the button so that dismissal cannot erase the action first.
        guard let dialog = confirmedDialog ?? viewState.accountDialog,
              !viewState.isAccountActionInProgress
        else { return }

        if viewState.accountDialog == dialog {
            viewState.accountDialog = nil
        }
        viewState.isAccountActionInProgress = true

        do {
            switch dialog {
            case .logout:
                try await logout()
            case .withdraw:
                try await withdraw()
            }
            await finishSignedOut()
        } catch SettingsAPIError.unauthorized {
            await finishSignedOut()
        } catch is CancellationError {
            viewState.isAccountActionInProgress = false
        } catch {
            viewState.isAccountActionInProgress = false
            publish(.showMessage("요청을 처리하지 못했어요. 다시 시도해 주세요."))
        }
    }

    func consumeEvent() {
        event = nil
    }

    private func finishSignedOut() async {
        if isAuthenticated() {
            try? await logout()
        }
        viewState.isLoading = false
        viewState.isLoggedIn = false
        viewState.isAccountActionInProgress = false
        viewState.signatureURL = nil
        publish(.signedOut)
    }

    private func publish(_ event: SettingsEvent) {
        self.event = event
    }
}
