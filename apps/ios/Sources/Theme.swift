import SwiftUI

enum PlotusTheme {
    static let background = Color("Background")
    static let surface = Color("Surface")
    static let ink = Color("Ink")
    static let muted = Color("Muted")
    static let line = Color("Line")
    static let quiet = Color("Quiet")

    static func font(_ size: CGFloat, weight: String = "Regular", relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("PlusJakartaSans-\(weight)", size: size, relativeTo: style)
    }
}

struct PlotusButtonStyle: ButtonStyle {
    var primary = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(PlotusTheme.font(14, weight: "SemiBold"))
            .frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(primary ? Color("OnAccent") : PlotusTheme.ink)
            .background(primary ? Color.accentColor : PlotusTheme.surface, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(primary ? Color.clear : PlotusTheme.line))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

struct GeometryBadge: View {
    var body: some View {
        ZStack {
            Image("Boundary").accessibilityHidden(true)
            Text("PLOT").font(PlotusTheme.font(7.5)).foregroundStyle(PlotusTheme.muted)
                .offset(x: -3, y: 0)
        }
        .frame(width: 48, height: 48)
        .background(PlotusTheme.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color("Border")))
        .accessibilityHidden(true)
    }
}
