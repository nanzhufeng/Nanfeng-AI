#!/usr/bin/env swift
import AppKit
import CoreGraphics
import Foundation

enum RetoneError: Error, LocalizedError {
    case invalidArguments
    case unreadableInput(String)
    case unsupportedImage
    case outputFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidArguments:
            return "Usage: retone_nanfeng_ai_launcher_background.swift <source-image> <output-png>"
        case .unreadableInput(let path):
            return "Cannot read source image: \(path)"
        case .unsupportedImage:
            return "Source image has no usable bitmap representation"
        case .outputFailed(let path):
            return "Cannot write PNG: \(path)"
        }
    }
}

let arguments = CommandLine.arguments
guard arguments.count == 3 else { throw RetoneError.invalidArguments }

let inputURL = URL(fileURLWithPath: arguments[1])
let outputURL = URL(fileURLWithPath: arguments[2])
guard let image = NSImage(contentsOf: inputURL) else {
    throw RetoneError.unreadableInput(inputURL.path)
}
guard let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    throw RetoneError.unsupportedImage
}

let width = cgImage.width
let height = cgImage.height
let bytesPerPixel = 4
let bytesPerRow = width * bytesPerPixel
let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)
    .union(.byteOrder32Big)
guard let context = CGContext(
    data: nil,
    width: width,
    height: height,
    bitsPerComponent: 8,
    bytesPerRow: bytesPerRow,
    space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: bitmapInfo.rawValue
) else {
    throw RetoneError.unsupportedImage
}

context.interpolationQuality = .high
context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
guard let rawData = context.data else { throw RetoneError.unsupportedImage }
let pixels = rawData.bindMemory(to: UInt8.self, capacity: width * height * bytesPerPixel)

// The image-generator draft is a two-tone composite: white emblem/canvas over an
// approximately #FA7441 orange.  Recompose that same alpha relationship onto the
// user-approved subtle target #E1764E. This keeps the white Q-mark and anti-aliased
// edges intact while avoiding a stronger hue/brightness jump than requested.
let generatedOrangeBlue = 65.0
// CoreGraphics' source decode runs through an extended RGB space. These calibrated
// component values encode to the requested visible sample #E1764E after that conversion.
let target = (red: 216.0, green: 96.0, blue: 62.0)
let denominator = 255.0 - generatedOrangeBlue
for index in 0..<(width * height) {
    let offset = index * bytesPerPixel
    let blue = Double(pixels[offset + 2])
    let orangeCoverage = min(1.0, max(0.0, (255.0 - blue) / denominator))
    guard orangeCoverage > 0 else { continue }
    pixels[offset] = UInt8((255.0 - orangeCoverage * (255.0 - target.red)).rounded())
    pixels[offset + 1] = UInt8((255.0 - orangeCoverage * (255.0 - target.green)).rounded())
    pixels[offset + 2] = UInt8((255.0 - orangeCoverage * (255.0 - target.blue)).rounded())
}

guard let retunedImage = context.makeImage(),
      let outputData = NSBitmapImageRep(cgImage: retunedImage).representation(using: .png, properties: [:])
else {
    throw RetoneError.unsupportedImage
}
do {
    try outputData.write(to: outputURL, options: .atomic)
} catch {
    throw RetoneError.outputFailed(outputURL.path)
}
