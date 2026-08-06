package io.interestellar.remote.ui

internal data class AgentMessagePresentation(
    val visible: String,
    val technical: String,
)

private val internalBlockStart = Regex(
    "^<(analysis|thinking|thought|tool_call|tool_calls|function_call|function_calls|invoke|command)(\\s|>)",
    RegexOption.IGNORE_CASE,
)
private val internalBlockEnd = Regex(
    "^</(analysis|thinking|thought|tool_call|tool_calls|function_call|function_calls|invoke|command)>",
    RegexOption.IGNORE_CASE,
)
private val internalRoleLine = Regex(
    "^(assistant(/(analysis|final))?|analysis|thinking|thought|tool|function|recipient)(\\s+to=[^\\s]+)?\\s*[:>]?\\s*$",
    RegexOption.IGNORE_CASE,
)
private val internalLogLine = Regex("^\\s*\\[(debug|trace|tool|function|internal)]", RegexOption.IGNORE_CASE)
private val tokenBudgetLine = Regex("^\\s*you have \\d+ weighted tokens left\\s*$", RegexOption.IGNORE_CASE)
private val internalJsonKey = Regex(
    "\"(event|delta|tool_call|tool_calls|function_call|function_calls|usage_metadata|thought|recipient)\"\\s*:",
    RegexOption.IGNORE_CASE,
)
private val singleLineInternalTag = Regex(
    "<(analysis|thinking|thought|tool_call|tool_calls|function_call|function_calls|invoke|command)(\\s[^>]*)?>.*?</\\1>",
    setOf(RegexOption.IGNORE_CASE),
)

/** Separates runtime protocol/log fragments from text intended for the person. */
internal fun presentAgentMessage(raw: String): AgentMessagePresentation {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        .replace(singleLineInternalTag, "")
    val visible = mutableListOf<String>()
    val technical = mutableListOf<String>()
    var internalBlock = false
    var fencedCode = false

    normalized.lines().forEach { line ->
        val trimmed = line.trim()
        if (!internalBlock && trimmed.startsWith("```")) {
            fencedCode = !fencedCode
            visible += line
            return@forEach
        }
        if (fencedCode) {
            visible += line
            return@forEach
        }
        if (!fencedCode && internalBlockStart.containsMatchIn(trimmed)) {
            internalBlock = true
            technical += line
            if (internalBlockEnd.containsMatchIn(trimmed)) internalBlock = false
            return@forEach
        }
        if (internalBlock) {
            technical += line
            if (internalBlockEnd.containsMatchIn(trimmed)) internalBlock = false
            return@forEach
        }

        val internalJson = trimmed.startsWith("{") && trimmed.endsWith("}") &&
            internalJsonKey.containsMatchIn(trimmed)
        val internalLine = internalRoleLine.matches(trimmed) ||
            internalLogLine.containsMatchIn(trimmed) ||
            tokenBudgetLine.matches(trimmed) ||
            trimmed.startsWith("assistant to=", ignoreCase = true) ||
            trimmed.startsWith("recipient=", ignoreCase = true) ||
            internalJson
        if (internalLine) technical += line else visible += line
    }

    return AgentMessagePresentation(
        visible = visible.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim(),
        technical = technical.joinToString("\n").trim(),
    )
}

