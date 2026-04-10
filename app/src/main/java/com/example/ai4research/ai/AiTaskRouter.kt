package com.example.ai4research.ai

import com.example.ai4research.ai.local.LocalAiBackend
import com.example.ai4research.ai.local.OnDeviceAiSettingsStore
import com.example.ai4research.ai.local.OnDeviceModelInstallState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiTaskRouter @Inject constructor(
    private val settingsStore: OnDeviceAiSettingsStore,
    private val localAiBackend: LocalAiBackend
) {
    suspend fun decide(taskType: AiTaskType): AiRouteDecision {
        val policy = defaultPolicy(taskType)
        val settings = settingsStore.snapshot()

        if (policy == AiRoutePolicy.CLOUD_ONLY) {
            return AiRouteDecision(
                taskType = taskType,
                policy = policy,
                preferredTarget = AiTarget.CLOUD,
                allowCloudFallback = false,
                reason = "This task still requires the cloud model."
            )
        }

        if (!settings.enabled) {
            return AiRouteDecision(
                taskType = taskType,
                policy = policy,
                preferredTarget = AiTarget.CLOUD,
                allowCloudFallback = policy == AiRoutePolicy.LOCAL_THEN_CLOUD,
                reason = "On-device AI is disabled."
            )
        }

        if (settings.installState != OnDeviceModelInstallState.READY) {
            return AiRouteDecision(
                taskType = taskType,
                policy = policy,
                preferredTarget = AiTarget.CLOUD,
                allowCloudFallback = policy == AiRoutePolicy.LOCAL_THEN_CLOUD,
                reason = "On-device model is not installed and ready yet."
            )
        }

        if (!localAiBackend.canHandle(taskType)) {
            return AiRouteDecision(
                taskType = taskType,
                policy = policy,
                preferredTarget = AiTarget.CLOUD,
                allowCloudFallback = policy == AiRoutePolicy.LOCAL_THEN_CLOUD,
                reason = "On-device backend is not ready for this task yet."
            )
        }

        return AiRouteDecision(
            taskType = taskType,
            policy = policy,
            preferredTarget = AiTarget.LOCAL,
            allowCloudFallback = policy == AiRoutePolicy.LOCAL_THEN_CLOUD,
            reason = "On-device model is available for this task."
        )
    }

    fun defaultPolicy(taskType: AiTaskType): AiRoutePolicy {
        return when (taskType) {
            AiTaskType.TRANSCRIPTION_ENHANCE,
            AiTaskType.ITEM_QA_SHORT -> AiRoutePolicy.LOCAL_ONLY

            AiTaskType.LINK_PARSE,
            AiTaskType.SHORT_SUMMARY,
            AiTaskType.OCR_POST_PROCESS -> AiRoutePolicy.LOCAL_THEN_CLOUD

            AiTaskType.IMAGE_OCR,
            AiTaskType.AUDIO_TRANSCRIPTION,
            AiTaskType.PROJECT_OVERVIEW,
            AiTaskType.RESEARCH_COMPARE -> AiRoutePolicy.CLOUD_ONLY
        }
    }
}
