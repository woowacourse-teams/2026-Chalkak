import Foundation

enum SocialLoginProvider: String, CaseIterable, Sendable {
    case apple = "APPLE"
    case google = "GOOGLE"
    case kakao = "KAKAO"

    var title: String {
        switch self {
        case .apple:
            "Apple"
        case .google:
            "Google"
        case .kakao:
            "카카오"
        }
    }

    var buttonTitle: String {
        "\(title)로 계속하기"
    }
}

enum LoginStatus: Equatable {
    case idle
    case loading(SocialLoginProvider?)
    case authenticated
    case guestAccessGranted
    case signUpRequired
}

struct LoginViewState: Equatable {
    var status: LoginStatus = .idle
    var errorMessage: String?

    var canSubmit: Bool {
        if case .idle = status { return true }
        return false
    }
}
