import SwiftUI

struct NotificationSetupScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: NotificationSetupViewModel
    let onFinish: () -> Void

    @State private var isCustomTimePickerPresented = false
    @State private var customDate = Self.defaultCustomDate
    @State private var message: String?
    @GestureState private var pickerDragOffset: CGFloat = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            timeOptions
            Spacer(minLength: theme.spacing.xxl)
            footer
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .padding(.top, Metrics.topPadding)
        .padding(.bottom, Metrics.bottomPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(theme.colors.background)
        .overlay(alignment: .bottom) {
            if let message {
                Text(message)
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.onActionPrimary)
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.vertical, theme.spacing.md)
                    .background(theme.colors.actionPrimary, in: Capsule())
                    .padding(.bottom, Metrics.toastBottomPadding)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .overlay {
            if isCustomTimePickerPresented {
                customTimePickerOverlay
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: isCustomTimePickerPresented)
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .onAppear {
            syncCustomDateWithViewState()
        }
    }

    private func syncCustomDateWithViewState() {
        let time = viewModel.viewState.customTime
        guard let hour = time.hour, let minute = time.minute,
              let date = Calendar.current.date(from: DateComponents(hour: hour, minute: minute)) else {
            return
        }
        customDate = date
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: theme.spacing.xl) {
            Text("언제 알려드릴까요?")
                .font(theme.typography.title1)
                .foregroundStyle(theme.colors.textPrimary)
                .accessibilityIdentifier("notificationSetup.title")

            Text("주제는 밤 12시에 변경돼요.\n날마다 주제를 알림으로 알려드릴게요.")
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)
                .lineSpacing(theme.spacing.sm)
        }
    }

    private var timeOptions: some View {
        VStack(spacing: theme.spacing.md) {
            ForEach(NotificationTimeOption.allCases) { option in
                notificationTimeButton(option)
            }
        }
        .padding(.top, Metrics.optionsTopPadding)
    }

    private func notificationTimeButton(_ option: NotificationTimeOption) -> some View {
        let isSelected = viewModel.viewState.selectedOption == option
        let title = option == .custom && isSelected
            ? customTimeTitle
            : option.title

        return Button {
            if option == .custom {
                isCustomTimePickerPresented = true
            } else {
                viewModel.select(option)
            }
        } label: {
            HStack(spacing: theme.spacing.md) {
                Text(title)
                    .font(theme.typography.body)
                Spacer(minLength: theme.spacing.md)
                if let context = option.context {
                    Text(context)
                        .font(theme.typography.subheadline)
                        .foregroundStyle(isSelected
                            ? theme.colors.onActionPrimary
                            : theme.colors.textInactive)
                }
            }
            .foregroundStyle(isSelected
                ? theme.colors.onActionPrimary
                : theme.colors.textPrimary)
            .padding(.horizontal, theme.spacing.lg)
            .frame(maxWidth: .infinity, minHeight: Metrics.optionHeight)
            .background(
                isSelected ? theme.colors.actionPrimary : theme.colors.surfaceElevated,
                in: RoundedRectangle(cornerRadius: theme.shapes.input)
            )
            .overlay {
                if !isSelected {
                    RoundedRectangle(cornerRadius: theme.shapes.input)
                        .stroke(theme.colors.border, lineWidth: Metrics.borderWidth)
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: theme.shapes.input))
        }
        .buttonStyle(.plain)
        .disabled(viewModel.viewState.isSaving)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("notificationTime.\(option.rawValue)")
    }

    private var footer: some View {
        VStack(spacing: theme.spacing.xl) {
            Button("알림 따로 필요 없어요!", action: viewModel.skip)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textInactive)
                .underline()
                .frame(minHeight: Metrics.minimumTouchHeight)
                .disabled(viewModel.viewState.isSaving)
                .accessibilityIdentifier("notificationSetup.skip")

            ChalkakButton(
                title: viewModel.viewState.isSaving ? "설정하는 중..." : "이 시간으로 정할게요",
                action: { Task { await viewModel.confirm() } },
                isEnabled: !viewModel.viewState.isSaving,
                fillsWidth: true
            )
            .accessibilityIdentifier("notificationSetup.confirm")
        }
        .frame(maxWidth: .infinity)
    }

    private var customTimePickerOverlay: some View {
        ZStack(alignment: .bottom) {
            theme.colors.scrim
                .ignoresSafeArea()
                .onTapGesture {
                    isCustomTimePickerPresented = false
                }
                .accessibilityLabel("시간 선택 닫기")

            VStack(spacing: 0) {
                Color.clear
                    .frame(height: Metrics.pickerDragAreaHeight)
                    .contentShape(Rectangle())
                    .gesture(pickerDragGesture)
                    .accessibilityElement()
                    .accessibilityLabel("시간 선택 창 닫기")
                    .accessibilityHint("아래로 쓸어내리면 닫혀요")
                    .accessibilityIdentifier("notificationTimePicker.dragHandle")

                DatePicker(
                    "알림 시간",
                    selection: $customDate,
                    displayedComponents: .hourAndMinute
                )
                .datePickerStyle(.wheel)
                .labelsHidden()

                ChalkakButton(
                    title: "이 시간 선택하기",
                    action: {
                        viewModel.updateCustomTime(customDate)
                        isCustomTimePickerPresented = false
                    },
                    fillsWidth: true
                )
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .padding(.bottom, Metrics.bottomPadding)
            }
            .background {
                UnevenRoundedRectangle(
                    topLeadingRadius: theme.shapes.sheet,
                    topTrailingRadius: theme.shapes.sheet
                )
                .fill(theme.colors.background)
                .ignoresSafeArea(edges: .bottom)
            }
            .offset(y: resolvedPickerDragOffset)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("notificationTimePicker")
        }
    }

    private var resolvedPickerDragOffset: CGFloat {
        max(0, pickerDragOffset)
    }

    private var pickerDragGesture: some Gesture {
        DragGesture(
            minimumDistance: Metrics.minimumDragDistance,
            coordinateSpace: .global
        )
            .updating($pickerDragOffset) { value, offset, _ in
                offset = max(0, value.translation.height)
            }
            .onEnded { value in
                let shouldDismiss = value.translation.height > Metrics.dismissDragDistance
                    || value.predictedEndTranslation.height > Metrics.dismissPredictedDistance
                if shouldDismiss {
                    isCustomTimePickerPresented = false
                }
            }
    }

    private var customTimeTitle: String {
        guard let hour = viewModel.viewState.customTime.hour,
              let minute = viewModel.viewState.customTime.minute else {
            return NotificationTimeOption.custom.title
        }
        return String(format: "직접 설정 · %02d:%02d", hour, minute)
    }

    private func handle(_ event: NotificationSetupEvent?) {
        guard let event else { return }
        switch event {
        case .completed:
            onFinish()
        case let .showMessage(text):
            withAnimation(.snappy) { message = text }
        }
        viewModel.consumeEvent()
    }

    private static var defaultCustomDate: Date {
        Calendar.current.date(from: DateComponents(hour: 18, minute: 0)) ?? Date()
    }
}

private enum Metrics {
    static let topPadding: CGFloat = 72
    static let bottomPadding: CGFloat = 8
    static let optionsTopPadding: CGFloat = 56
    static let optionHeight: CGFloat = 64
    static let borderWidth: CGFloat = 1
    static let minimumTouchHeight: CGFloat = 44
    static let pickerDragAreaHeight: CGFloat = 44
    static let minimumDragDistance: CGFloat = 4
    static let dismissDragDistance: CGFloat = 80
    static let dismissPredictedDistance: CGFloat = 140
    static let toastBottomPadding: CGFloat = 104
}

#Preview("Notification setup") {
    NotificationSetupScreen(
        viewModel: NotificationSetupViewModel(),
        onFinish: {}
    )
    .chalkakTheme(.light)
}
