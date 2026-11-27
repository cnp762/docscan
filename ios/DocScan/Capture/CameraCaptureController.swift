import AVFoundation
import Foundation
import UIKit
import Vision

final class CameraCaptureController: NSObject, ObservableObject {
    let session = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let queue = DispatchQueue(label: "cam.docscan.capture")
    private var photoContinuation: CheckedContinuation<UIImage?, Never>?

    @Published var detectedQuad: [CGPoint]?

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

    func capturePhoto() async -> UIImage? {
        await withCheckedContinuation { continuation in
            self.photoContinuation = continuation
            let settings = AVCapturePhotoSettings()
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
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let request = VNDetectRectanglesRequest { [weak self] req, _ in
            guard let rect = (req.results as? [VNRectangleObservation])?.first else {
                DispatchQueue.main.async { self?.detectedQuad = nil }
                return
            }
            let pts = [rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft]
            DispatchQueue.main.async { self?.detectedQuad = pts }
        }
        request.maximumObservations = 1
        request.minimumConfidence = 0.65
        try? VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .right).perform([request])
    }
}
