#!/usr/bin/env swift
import AppKit
import CoreGraphics
import Foundation

enum LauncherForegroundError: Error, LocalizedError {
    case invalidArguments
    case unreadableInput(String)
    case unsupportedImage
    case outputFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidArguments:
            return "Usage: prepare_nanfeng_ai_launcher_foreground.swift <source-image> <output-png>"
        case .unreadableInput(let path):
            return "Cannot read source image: \(path)"
        case .unsupportedImage:
            return "Source image has no CGImage representation"
        case .outputFailed(let path):
            return "Cannot write PNG: \(path)"
        }
    }
}

let arguments = CommandLine.arguments
guard arguments.count == 3 else { throw LauncherForegroundError.invalidArguments }

let inputURL = URL(fileURLWithPath: arguments[1])
let outputURL = URL(fileURLWithPath: arguments[2])
guard let image = NSImage(contentsOf: inputURL) else {
    throw LauncherForegroundError.unreadableInput(inputURL.path)
}
guard let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    throw LauncherForegroundError.unsupportedImage
}

let width = cgImage.width
let height = cgImage.height
let bytesPerPixel = 4
let bytesPerRow = width * bytesPerPixel
let pixelCount = width * height
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
    throw LauncherForegroundError.unsupportedImage
}

context.interpolationQuality = .high
context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
guard let rawData = context.data else { throw LauncherForegroundError.unsupportedImage }
let pixels = rawData.bindMemory(to: UInt8.self, capacity: pixelCount * bytesPerPixel)

// The supplied square contains an exterior white canvas around an orange rounded icon.
// Remove only near-white pixels connected to that canvas; the isolated white AI/leaf mark
// remains byte-for-byte untouched in its original coordinate system.
func isExteriorCanvasWhite(_ index: Int) -> Bool {
    let offset = index * bytesPerPixel
    return pixels[offset] >= 232 && pixels[offset + 1] >= 232 && pixels[offset + 2] >= 232 && pixels[offset + 3] >= 232
}

var visited = Array(repeating: false, count: pixelCount)
var queue: [Int] = []
queue.reserveCapacity(width * 2 + height * 2)

func enqueueIfExteriorWhite(_ x: Int, _ y: Int) {
    let index = y * width + x
    guard !visited[index], isExteriorCanvasWhite(index) else { return }
    visited[index] = true
    queue.append(index)
}

for x in 0..<width {
    enqueueIfExteriorWhite(x, 0)
    enqueueIfExteriorWhite(x, height - 1)
}
for y in 1..<(height - 1) {
    enqueueIfExteriorWhite(0, y)
    enqueueIfExteriorWhite(width - 1, y)
}

var cursor = 0
while cursor < queue.count {
    let index = queue[cursor]
    cursor += 1
    let x = index % width
    let y = index / width
    if x > 0 { enqueueIfExteriorWhite(x - 1, y) }
    if x + 1 < width { enqueueIfExteriorWhite(x + 1, y) }
    if y > 0 { enqueueIfExteriorWhite(x, y - 1) }
    if y + 1 < height { enqueueIfExteriorWhite(x, y + 1) }
}

for index in queue {
    let offset = index * bytesPerPixel
    pixels[offset] = 0
    pixels[offset + 1] = 0
    pixels[offset + 2] = 0
    pixels[offset + 3] = 0
}

guard let foregroundImage = context.makeImage() else { throw LauncherForegroundError.unsupportedImage }
let outputData = NSBitmapImageRep(cgImage: foregroundImage).representation(using: .png, properties: [:])
guard let outputData else { throw LauncherForegroundError.outputFailed(outputURL.path) }
do {
    try outputData.write(to: outputURL, options: .atomic)
} catch {
    throw LauncherForegroundError.outputFailed(outputURL.path)
}
