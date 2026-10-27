import SwiftUI

struct ProjectFormView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store: PreviewStore
    let project: PreviewProject?
    @State private var name: String
    @State private var location: String
    @State private var clientProject: Bool
    @State private var clientName: String
    @State private var email: String
    @State private var phone: String

    init(store: PreviewStore, project: PreviewProject? = nil) {
        self.store = store
        self.project = project
        _name = State(initialValue: project?.name ?? "")
        _location = State(initialValue: project?.location ?? "")
        _clientProject = State(initialValue: !(project?.clientName.isEmpty ?? true))
        _clientName = State(initialValue: project?.clientName ?? "")
        _email = State(initialValue: project?.clientEmail ?? "")
        _phone = State(initialValue: project?.clientPhone ?? "")
    }

    private var valid: Bool {
        let fieldsValid = !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let emailParts = email.split(separator: "@", omittingEmptySubsequences: false)
        let emailValid = emailParts.count == 2 && !emailParts[0].isEmpty && emailParts[1].contains(".") && !email.contains(where: { $0.isWhitespace })
        return fieldsValid && name.count <= 120 && location.count <= 200 && (!clientProject || (!clientName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && emailValid))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $name).accessibilityLabel("Name")
                    TextField("Location", text: $location).accessibilityLabel("Location")
                }
                .listRowBackground(PlotusTheme.surface)
                Section {
                    Toggle("Client project?", isOn: $clientProject)
                }
                .listRowBackground(PlotusTheme.surface)
                if clientProject {
                    Section("Client information") {
                        TextField("Client name", text: $clientName).textContentType(.name)
                        TextField("Email", text: $email).keyboardType(.emailAddress).textContentType(.emailAddress)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                        TextField("Phone no.", text: $phone).keyboardType(.phonePad).textContentType(.telephoneNumber)
                    }
                    .listRowBackground(PlotusTheme.surface)
                }
                Section {
                    Text("Changes stay in this local design preview.")
                        .font(PlotusTheme.font(12, relativeTo: .caption)).foregroundStyle(PlotusTheme.muted)
                }
                .listRowBackground(Color.clear)
            }
            .scrollContentBackground(.hidden)
            .background(PlotusTheme.background)
            .navigationTitle(project == nil ? "New project" : "Edit project")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Image("IconClose") }.accessibilityLabel("Cancel")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(project == nil ? "Create" : "Save") {
                        var saved = project ?? PreviewProject(name: name, location: location)
                        saved.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        saved.location = location.trimmingCharacters(in: .whitespacesAndNewlines)
                        saved.edited = "Just now"
                        saved.clientName = clientProject ? clientName.trimmingCharacters(in: .whitespacesAndNewlines) : ""
                        saved.clientEmail = clientProject ? email.trimmingCharacters(in: .whitespacesAndNewlines) : ""
                        saved.clientPhone = clientProject ? phone.trimmingCharacters(in: .whitespacesAndNewlines) : ""
                        store.save(saved)
                        dismiss()
                    }
                    .disabled(!valid)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
