package com.nanzhufeng.ai.domain

/** Sampling affects local display pixels only; original file bytes remain untouched. */
fun previewBitmapSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 0
    var sample = 1
    while ((width.toLong() / sample) * (height.toLong() / sample) > 4_000_000L ||
        width / sample > 4096 || height / sample > 4096) sample *= 2
    return sample
}
