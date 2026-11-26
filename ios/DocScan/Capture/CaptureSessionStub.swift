import AVFoundation
import Foundation

/// Placeholder for the custom AVFoundation capture pipeline.
/// Real implementation: preview layer, Vision document segmentation, auto-capture state machine.
final class CaptureSessionStub: NSObject {
    let session = AVCaptureSession()

    func requestAccess() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .video)
    }

    func configure() throws {
        session.beginConfiguration()
        session.sessionPreset = .photo
        guard
            let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
            let input = try? AVCaptureDeviceInput(device: device),
            session.canAddInput(input)
        else {
            session.commitConfiguration()
            throw CaptureError.cameraUnavailable
        }
        session.addInput(input)
        session.commitConfiguration()
    }

    enum CaptureError: Error {
        case cameraUnavailable
    }
}
