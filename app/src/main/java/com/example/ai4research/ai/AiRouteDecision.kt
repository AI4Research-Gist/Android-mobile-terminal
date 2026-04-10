package com.example.ai4research.ai

data class AiRouteDecision(
    val taskType: AiTaskType,
    val policy: AiRoutePolicy,
    val preferredTarget: AiTarget,
    val allowCloudFallback: Boolean,
    val reason: String
)
