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
    private var hasLoaded = false

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
        let authenticated = isAuthenticated()
        guard !hasLoaded || authenticated != viewState.isLoggedIn else { return }

        hasLoaded = true
        guard authenticated else {
            viewState.isLoading = false
            viewState.isLoggedIn = false
            viewState.signatureURL = nil
            return
        }

        viewState.isLoading = true
        viewState.isLoggedIn = true
        do {
            viewState.signatureURL = try await loadSignature()
            viewState.isLoading = false
        } catch SettingsAPIError.unauthorized {
            await finishSignedOut()
        } catch is CancellationError {
            hasLoaded = false
            viewState.isLoading = false
            return
        } catch {
            viewState.isLoading = false
            publish(.showMessage("사인을 불러오지 못했어요. 다시 시도해 주세요."))
        }
    }

    func showLogoutDialog() {
        viewState.accountDialog = .logout
    }

    func updateSignature(_ pngData: Data) async throws {
        do {
            viewState.signatureURL = try await updateSignatureHandler(pngData)
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

    func confirmAccountAction() async {
        guard let dialog = viewState.accountDialog,
              !viewState.isAccountActionInProgress
        else { return }

        viewState.accountDialog = nil
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
