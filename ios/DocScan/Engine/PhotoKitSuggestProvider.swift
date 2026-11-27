import Foundation
import Photos
import UIKit

/// Real PhotoKit-backed 7-day photo suggest provider for iOS.
final class PhotoKitSuggestProvider: SuggestMediaProvider {
    private let imageManager = PHCachingImageManager()

    static func requestAuthorization() async -> Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if status == .authorized || status == .limited {
            return true
        }
        let granted = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        return granted == .authorized || granted == .limited
    }

    func loadChunk(cursor: String?, limit: Int) async -> [SuggestMediaItem] {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .authorized || status == .limited else { return [] }

        let sevenDaysAgo = Date().addingTimeInterval(-7 * 86_400)
        let options = PHFetchOptions()
        options.predicate = NSPredicate(
            format: "mediaType == %d AND creationDate >= %@",
            PHAssetMediaType.image.rawValue,
            sevenDaysAgo as NSDate
        )
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]

        let fetchResult = PHAsset.fetchAssets(with: options)
        let offset = Int(cursor ?? "0") ?? 0
        var items: [SuggestMediaItem] = []
        let end = min(fetchResult.count, offset + limit)
        if offset < end {
            for idx in offset..<end {
                let asset = fetchResult.object(at: idx)
                let takenMs = Int64((asset.creationDate?.timeIntervalSince1970 ?? 0) * 1000)
                items.append(
                    SuggestMediaItem(
                        mediaId: asset.localIdentifier,
                        takenAtMs: takenMs,
                        width: asset.pixelWidth,
                        height: asset.pixelHeight
                    )
                )
            }
        }
        return items
    }

    func loadThumb(mediaId: String) async -> UIImage? {
        await loadImage(mediaId: mediaId, targetSize: CGSize(width: 384, height: 384), deliveryMode: .fastFormat)
    }

    func loadFullImage(mediaId: String) async -> UIImage? {
        await loadImage(mediaId: mediaId, targetSize: PHImageManagerMaximumSize, deliveryMode: .highQualityFormat)
    }

    private func loadImage(
        mediaId: String,
        targetSize: CGSize,
        deliveryMode: PHImageRequestOptionsDeliveryMode
    ) async -> UIImage? {
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: [mediaId], options: nil)
        guard let asset = assets.firstObject else { return nil }
        return await withCheckedContinuation { cont in
            let opts = PHImageRequestOptions()
            opts.deliveryMode = deliveryMode
            opts.isNetworkAccessAllowed = true
            opts.isSynchronous = false
            var resumed = false
            imageManager.requestImage(
                for: asset,
                targetSize: targetSize,
                contentMode: .aspectFit,
                options: opts
            ) { image, info in
                let isDegraded = (info?[PHImageResultIsDegradedKey] as? Bool) ?? false
                if isDegraded && deliveryMode == .highQualityFormat {
                    return
                }
                guard !resumed else { return }
                resumed = true
                cont.resume(returning: image)
            }
        }
    }
}
