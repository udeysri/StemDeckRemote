import AVFoundation
import MediaPlayer
import SwiftUI

/// Bridges the VOL fader to the device's actual hardware volume — the same
/// level the physical buttons and Control Center control — instead of an
/// internal digital gain the fader used to drive.
///
/// There is no public API to just "set system volume." The one documented
/// way an app can move it is via `MPVolumeView`'s embedded `UISlider`: a
/// long-standing, widely-used technique (reaching into a system view's
/// subviews, not a private API) rather than an officially blessed method,
/// but one that's been stable across iOS versions for years. Reads go
/// through `AVAudioSession.outputVolume`, which is KVO-observable and is
/// how apps detect hardware button presses too, so this stays in sync
/// whichever way the volume changes.
@MainActor
final class SystemVolumeController: NSObject, ObservableObject {
    @Published private(set) var volume: Float

    private var observation: NSKeyValueObservation?
    private weak var hiddenSlider: UISlider?

    override init() {
        volume = AVAudioSession.sharedInstance().outputVolume
        super.init()
        observation = AVAudioSession.sharedInstance().observe(\.outputVolume, options: [.new]) { [weak self] _, change in
            guard let self, let newValue = change.newValue else { return }
            Task { @MainActor in self.volume = newValue }
        }
    }

    fileprivate func attach(slider: UISlider) {
        hiddenSlider = slider
    }

    /// Setting `.value` alone doesn't push the change to the system — only
    /// actually triggering the slider's action does, so this fires it
    /// manually the same way a real touch would. If `hiddenSlider` is nil
    /// (the lookup in `HiddenSystemVolumeView` hasn't found it yet, or
    /// never will on some OS version), this silently does nothing — a
    /// fader that doesn't move rather than a crash.
    func setVolume(_ newValue: Float) {
        guard let hiddenSlider else { return }
        hiddenSlider.value = newValue
        hiddenSlider.sendActions(for: .valueChanged)
        volume = newValue
    }

    deinit {
        observation?.invalidate()
    }
}

/// The hidden `MPVolumeView` itself, kept at a real, non-trivial size —
/// `MPVolumeView` doesn't reliably finish building its internal slider
/// subview when it's sized down to nothing, which a `.frame(width: 1,
/// height: 1)` at the call site would otherwise force it to (SwiftUI's
/// layout constraints override whatever frame this view is initialized
/// with). Callers should size the *wrapper* generously (40pt+) and rely on
/// `.opacity()` for invisibility instead of shrinking it away.
struct HiddenSystemVolumeView: UIViewRepresentable {
    let controller: SystemVolumeController

    func makeUIView(context: Context) -> MPVolumeView {
        let view = MPVolumeView(frame: CGRect(x: 0, y: 0, width: 100, height: 44))
        view.showsRouteButton = false
        findAndAttachSlider(in: view, attempt: 0)
        return view
    }

    func updateUIView(_ uiView: MPVolumeView, context: Context) {}

    /// The internal slider isn't always present the instant the view is
    /// created — it can take a layout pass or two — so this searches the
    /// full subview tree (not just direct children) and retries briefly
    /// rather than giving up after one look.
    private func findAndAttachSlider(in view: UIView, attempt: Int) {
        if let slider = Self.findSlider(in: view) {
            controller.attach(slider: slider)
            return
        }
        guard attempt < 20 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            findAndAttachSlider(in: view, attempt: attempt + 1)
        }
    }

    private static func findSlider(in view: UIView) -> UISlider? {
        for subview in view.subviews {
            if let slider = subview as? UISlider { return slider }
            if let nested = findSlider(in: subview) { return nested }
        }
        return nil
    }
}
