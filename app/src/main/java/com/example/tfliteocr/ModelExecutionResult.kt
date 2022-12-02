package com.example.tfliteocr

import android.graphics.Bitmap

data class ModelExecutionResult(
    val bitmapResult: Bitmap,
    val executionLog: String,
    // A map between words and colors of the items found.
    val itemsFound: Map<String, Int>
)