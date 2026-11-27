import AVFoundation
import CoreImage
import Foundation
import UIKit
import Vision

final class CameraCaptureController: NSObject, ObservableObject {
    let session = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let queue = DispatchQueue(label: "cam.docscan.capture")
    private var photoContinuation: CheckedContinuation<UIImage?, Never>?
    private var lastAnalyzedAt: TimeInterval = 0
    private var prevCorners: [CGPoint]?
    private var stableStreak = 0
    private var flashEnabled = false

    @Published var detectedQuad: [CGPoint]?
    @Published var isLowLight = false
    @Published var isStable = false
    @Published var guidanceMessage = "Align document in frame"

    func requestAccess() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .video)
    }

    func configureAndStart() throws {
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

        if session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
        }
        videoOutput.setSampleBufferDelegate(self, queue: queue)
        if session.canAddOutput(videoOutput) {
            session.addOutput(videoOutput)
        }
        session.commitConfiguration()
        queue.async { [weak self] in
            self?.session.startRunning()
        }
    }

    func stop() {
        queue.async { [weak self] in
            self?.session.stopRunning()
        }
    }

    func setFlashEnabled(_ enabled: Bool) {
        flashEnabled = enabled
    }

    func capturePhoto() async -> UIImage? {
        await withCheckedContinuation { continuation in
            self.photoContinuation = continuation
            let settings = AVCapturePhotoSettings()
            if photoOutput.supportedFlashModes.contains(flashEnabled ? .on : .off) {
                settings.flashMode = flashEnabled ? .on : .off
            }
            photoOutput.capturePhoto(with: settings, delegate: self)
        }
    }

    enum CaptureError: Error {
        case cameraUnavailable
    }
}

extension CameraCaptureController: AVCapturePhotoCaptureDelegate {
    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        let image = photo.fileDataRepresentation().flatMap { UIImage(data: $0) }
        photoContinuation?.resume(returning: image)
        photoContinuation = nil
    }
}

extension CameraCaptureController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        let now = CACurrentMediaTime()
        guard now - lastAnalyzedAt >= 0.28 else { return }
        lastAnalyzedAt = now
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        let luma = estimateLuma(pixelBuffer: pixelBuffer)
        let lowLight = luma < 48.0

        let request = VNDetectDocumentSegmentationRequest { [weak self] req, _ in
            guard let self else { return }
            let obs = (req.results as? [VNRectangleObservation])?.first
            let pts: [CGPoint]? = obs.map { rect in
                [
                    CGPoint(x: rect.topLeft.x, y: 1.0 - rect.topLeft.y),
                    CGPoint(x: rect.topRight.x, y: 1.0 - rect.topRight.y),
                    CGPoint(x: rect.bottomRight.x, y: 1.0 - rect.bottomRight.y),
                    CGPoint(x: rect.bottomLeft.x, y: 1.0 - rect.bottomLeft.y),
                ]
            }
            let stable = self.updateStability(pts)
            let msg: String
            if lowLight {
                msg = "Low light · Try enabling flash"
            } else if pts == nil {
                msg = "Align document inside frame"
            } else if stable {
                msg = "Hold steady · All corners locked"
            } else {
                msg = "Corners detected · Hold steady"
            }
            DispatchQueue.main.async {
                self.detectedQuad = pts
                self.isLowLight = lowLight
                self.isStable = stable
                self.guidanceMessage = msg
            }
        }
        try? VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .right).perform([request])
    }

    private func estimateLuma(pixelBuffer: CVPixelBuffer) -> Double {
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0) else { return 128.0 }
        let width = CVPixelBufferGetWidthOfPlane(pixelBuffer, 0)
        let height = CVPixelBufferGetHeightOfPlane(pixelBuffer, 0)
        let stride = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0)
        let ptr = base.assumingMemoryBound(to: UInt8.self)
        var sum = 0
        var count = 0
        let stepY = max(1, height / 24)
        let stepX = max(1, width / 24)
        for y in Swift.stride(from: 0, to: height, by: stepY) {
            for x in Swift.stride(from: 0, to: width, by: stepX) {
                sum += Int(ptr[y * stride + x])
                count += 1
            }
        }
        return count > 0 ? Double(sum) / Double(count) : 128.0
    }

    private func updateStability(_ current: [CGPoint]?) -> Bool {
        guard let current, current.count == 4 else {
            stableStreak = 0
            prevCorners = nil
            return false
        }
        let prev = prevCorners
        prevCorners = current
        guard let prev, prev.count == 4 else {
            stableStreak = 0
            return false
        }
        let maxDelta = zip(current, prev).map { max(abs($0.x - $1.x), abs($0.y - $1.y)) }.max() ?? 1.0
        if maxDelta <= 0.035 {
            stableStreak += 1
        } else {
            stableStreak = 0
        }
        return stableStreak >= 2
    }
}
