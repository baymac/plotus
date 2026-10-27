import SwiftUI

struct RootView: View {
    @State private var preview = false
    @StateObject private var store = PreviewStore()

    var body: some View {
        Group {
            if preview {
                TabView {
                    ProjectsView(store: store)
                        .tabItem { Label { Text("Projects") } icon: { Image("IconProjects") } }
                    SettingsView { preview = false }
                        .tabItem { Label { Text("Settings") } icon: { Image("IconSettings") } }
                }
            } else {
                SignInView { preview = true }
            }
        }
        .font(PlotusTheme.font(14))
        .foregroundStyle(PlotusTheme.ink)
        .background(PlotusTheme.background)
    }
}

struct SignInView: View {
    let openPreview: () -> Void
    @State private var showAuthNotice = false

    var body: some View {
        GeometryReader { geometry in
        ScrollView {
        VStack(alignment: .leading, spacing: 0) {
            Text("Plotus")
                .font(PlotusTheme.font(24, weight: "Bold", relativeTo: .title2))
                .padding(.top, 24)
            VStack(alignment: .leading, spacing: 16) {
                Text("Sign in to Plotus")
                    .font(PlotusTheme.font(26, weight: "Bold", relativeTo: .title))
                    .accessibilityAddTraits(.isHeader)
                Text("Create and compare floor plans")
                    .font(PlotusTheme.font(20, weight: "SemiBold", relativeTo: .title2))
                Text("Draw your plot, add your priorities and compare floor-plan concepts.")
                    .font(PlotusTheme.font(16))
                    .foregroundStyle(PlotusTheme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.top, 76)
            Spacer(minLength: 32)
            VStack(spacing: 10) {
                Button { showAuthNotice = true } label: {
                    HStack(spacing: 8) { Image("IconGoogle").accessibilityHidden(true); Text("Continue with Google") }
                }
                .buttonStyle(PlotusButtonStyle())
                Button { showAuthNotice = true } label: {
                    HStack(spacing: 8) { Image("IconPasskey").accessibilityHidden(true); Text("Use a passkey") }
                }
                .buttonStyle(PlotusButtonStyle(primary: false))
            }
            Text("Concepts for early planning. Have a qualified architect review before building.")
                .font(PlotusTheme.font(12, relativeTo: .caption))
                .foregroundStyle(PlotusTheme.muted)
                .padding(.top, 32)
            Button("Explore design preview", action: openPreview)
                .font(PlotusTheme.font(12, relativeTo: .caption))
                .frame(maxWidth: .infinity, minHeight: 44)
                .padding(.top, 8)
                .accessibilityHint("Opens sample projects without signing in")
        }
        .padding(.horizontal, 24)
        .frame(minHeight: geometry.size.height, alignment: .top)
        }
        .background(PlotusTheme.background.ignoresSafeArea())
        .alert("Sign-in is not connected yet", isPresented: $showAuthNotice) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("You can explore the design preview with local sample projects. No authenticated session is created.")
        }
        }
    }
}
