package com.example.ai4research.service

object CompetitionImportDecider {
    fun needsManualFallback(parseResult: FullLinkParseResult): Boolean {
        if (!parseResult.contentType.equals("competition", ignoreCase = true)) return false
        val timeline = parseResult.timeline.orEmpty()
        val hasKeyNode = timeline.any { node ->
            node.name.contains("报名截止") || node.name.contains("提交截止") || node.name.contains("结果公布")
        }
        return !hasKeyNode
    }
}
