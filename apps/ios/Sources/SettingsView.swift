import SwiftUI

struct SettingsView: View {
    let exitPreview: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section("About") {
                    LabeledContent("App", value: "Plotus")
                    LabeledContent("Version", value: "0.1.0")
                }
                .listRowBackground(PlotusTheme.surface)
                Section("Design preview") {
                    Text("Local sample projects. You are not signed in.")
                        .foregroundStyle(PlotusTheme.muted)
                    Button("Back to sign in", action: exitPreview)
                }
                .listRowBackground(PlotusTheme.surface)
            }
            .scrollContentBackground(.hidden)
            .background(PlotusTheme.background)
            .navigationTitle("General")
            .toolbarBackground(PlotusTheme.background, for: .navigationBar, .tabBar)
        }
    }
}
