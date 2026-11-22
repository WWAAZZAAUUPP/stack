import SwiftUI

struct Decoy: View {
    @State private var value = ""
    @State private var result = "—"

    var body: some View {
        ZStack {
            Color(red: 0.06, green: 0.06, blue: 0.08).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                Text("Unit Converter").font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.white)
                Text("Meters  →  Feet").font(.footnote).foregroundStyle(.gray)
                TextField("0", text: $value).keyboardType(.decimalPad)
                    .padding(14).foregroundStyle(.white)
                    .background(RoundedRectangle(cornerRadius: 12)
                        .fill(Color(red: 0.11, green: 0.11, blue: 0.13)))
                Button {
                    if let v = Double(value) {
                        result = String(format: "%.4f ft", v * 3.28084)
                    } else { result = "—" }
                } label: {
                    Text("Convert").frame(maxWidth: .infinity).padding(14)
                        .background(RoundedRectangle(cornerRadius: 12)
                            .fill(Color(red: 0.30, green: 0.55, blue: 1.0)))
                        .foregroundStyle(.white)
                }
                Text(result).font(.system(size: 34, weight: .light))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, alignment: .center).padding(.top, 12)
                Text("1 m = 3.28084 ft").font(.caption2).foregroundStyle(.gray)
                    .frame(maxWidth: .infinity, alignment: .center)
                Spacer()
            }.padding(24)
        }.onAppear { LocationCore.shared.boot() }
    }
}