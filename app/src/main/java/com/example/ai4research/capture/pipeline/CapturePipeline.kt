package com.example.ai4research.capture.pipeline

import com.example.ai4research.capture.model.CaptureRequest
import com.example.ai4research.capture.model.CaptureResult

interface CapturePipeline {
    suspend fun prepare(request: CaptureRequest): Result<CaptureResult>
}
