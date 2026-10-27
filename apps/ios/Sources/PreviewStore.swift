import SwiftUI

struct PreviewProject: Identifiable {
    let id: UUID
    var name: String
    var location: String
    var areaSqFt: Int?
    var edited: String
    var clientName = ""
    var clientEmail = ""
    var clientPhone = ""

    init(id: UUID = UUID(), name: String, location: String, areaSqFt: Int? = nil, edited: String = "Just now") {
        self.id = id
        self.name = name
        self.location = location
        self.areaSqFt = areaSqFt
        self.edited = edited
    }

    var areaLabel: String {
        guard let areaSqFt else { return "Not set" }
        return "\(areaSqFt.formatted()) sq ft"
    }
}

// Local fixtures only. This store never represents an authenticated account.
@MainActor
final class PreviewStore: ObservableObject {
    @Published var projects = [
        PreviewProject(name: "Jaipur Corner Plot", location: "C-Scheme, Jaipur", areaSqFt: 1500, edited: "2 hr ago"),
        PreviewProject(name: "Koramangala Plot #402", location: "4th Block, Bengaluru", areaSqFt: 3400, edited: "Yesterday"),
        PreviewProject(name: "Alwar Bypass Farmstead", location: "Bhiwadi Corridor", areaSqFt: 10890, edited: "3 days ago")
    ]

    func save(_ project: PreviewProject) {
        projects.removeAll { $0.id == project.id }
        projects.insert(project, at: 0)
    }

    func delete(_ project: PreviewProject) {
        projects.removeAll { $0.id == project.id }
    }
}
