import SwiftUI

private enum ProjectSort: String, CaseIterable, Identifiable {
    case recent = "Last edited"
    case name = "Name"
    var id: Self { self }
}

struct ProjectsView: View {
    @ObservedObject var store: PreviewStore
    @State private var query = ""
    @State private var sort = ProjectSort.recent
    @State private var creating = false
    @State private var editing: PreviewProject?
    @State private var deleting: PreviewProject?

    private var filtered: [PreviewProject] {
        let matches = store.projects.filter {
            query.isEmpty || "\($0.name) \($0.location)".localizedCaseInsensitiveContains(query)
        }
        return sort == .name ? matches.sorted { $0.name.localizedCompare($1.name) == .orderedAscending } : matches
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                Text("Plotus")
                    .font(PlotusTheme.font(24, weight: "Bold", relativeTo: .title2))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 24).padding(.top, 20).padding(.bottom, 24)
                HStack {
                    Text("Projects")
                        .font(PlotusTheme.font(26, weight: "Bold", relativeTo: .title))
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button("New project") { creating = true }
                        .font(PlotusTheme.font(14, weight: "SemiBold"))
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
                .padding(.horizontal, 24)
                HStack(spacing: 10) {
                    HStack(spacing: 10) {
                        Image("IconSearch").accessibilityHidden(true)
                        TextField("Search projects", text: $query)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .accessibilityLabel("Search projects")
                    }
                    .padding(.horizontal, 12).frame(minHeight: 44)
                    .background(PlotusTheme.surface, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(PlotusTheme.line))
                    Menu {
                        Picker("Sort projects", selection: $sort) {
                            ForEach(ProjectSort.allCases) { Text($0.rawValue).tag($0) }
                        }
                    } label: {
                        Image("IconSort").frame(width: 44, height: 44)
                            .background(PlotusTheme.surface, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(PlotusTheme.line))
                    }
                    .accessibilityLabel("Sort projects")
                }
                .padding(.horizontal, 24).padding(.top, 14).padding(.bottom, 20)
                List {
                    ForEach(filtered) { project in
                        HStack(spacing: 14) {
                            Button { editing = project } label: {
                                HStack(spacing: 14) {
                                    GeometryBadge()
                                    VStack(alignment: .leading, spacing: 0) {
                                        Text(project.name).font(PlotusTheme.font(15, weight: "SemiBold"))
                                        Text(project.location).font(PlotusTheme.font(13)).foregroundStyle(PlotusTheme.muted)
                                        Text("\(project.areaLabel) · \(project.edited)")
                                            .font(PlotusTheme.font(12, relativeTo: .caption)).foregroundStyle(Color("Subtle"))
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                }
                                .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Edit \(project.name), \(project.location)")
                            Menu {
                                Button("Edit project") { editing = project }
                                Button("Delete project", role: .destructive) { deleting = project }
                            } label: { Image("IconMore").frame(width: 44, height: 44) }
                            .accessibilityLabel("Actions for \(project.name)")
                        }
                        .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                        .listRowBackground(PlotusTheme.background)
                        .listRowSeparatorTint(PlotusTheme.line)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .overlay {
                    if filtered.isEmpty {
                        VStack(spacing: 16) {
                            Text(query.isEmpty ? "Your next plan starts here" : "No matching projects")
                                .font(PlotusTheme.font(20, weight: "SemiBold"))
                            if !query.isEmpty { Button("Clear search") { query = "" } }
                        }
                    }
                }
            }
            .background(PlotusTheme.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .toolbarBackground(PlotusTheme.background, for: .tabBar)
            .sheet(isPresented: $creating) { ProjectFormView(store: store) }
            .sheet(item: $editing) { ProjectFormView(store: store, project: $0) }
            .confirmationDialog("Delete project?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
                if let project = deleting {
                    Button("Delete \(project.name)", role: .destructive) { store.delete(project); deleting = nil }
                }
                Button("Cancel", role: .cancel) { deleting = nil }
            } message: { Text("This removes the project from the local preview and cannot be undone.") }
        }
    }
}
