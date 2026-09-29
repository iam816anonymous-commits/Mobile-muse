package com.example.localagent.skills

import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.voice.VoiceSynthesizer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

object HeadlessMathEngine {

    private const val TAG = "HeadlessMathEngine"

    fun isMathQuery(rawInput: String): Boolean {
        val cleaned = rawInput.lowercase().trim()
        if (cleaned.contains("calculate") || cleaned.contains("compute") || cleaned.contains("what is") || cleaned.contains("eval")) {
            if (cleaned.contains(Regex("[0-9+\\-*/^%]"))) return true
        }
        val mathPattern = Regex("(?i)^.*\\d+\\s*[*+\\-/^%x]\\s*\\d+.*$")
        return cleaned.matches(mathPattern)
    }

    fun extractExpression(rawInput: String): String {
        val cleaned = rawInput.lowercase()
            .replace("calculate", "")
            .replace("compute", "")
            .replace("what is", "")
            .replace("eval", "")
            .replace("localagent", "")
            .replace("x", "*")
            .trim()

        val exprMatches = Regex("[0-9+\\-*/^%().\\s]+").findAll(cleaned)
            .map { it.value.trim() }
            .filter { it.contains(Regex("[0-9]")) }
            .joinToString(" ")

        return exprMatches.ifEmpty { "0" }
    }

    fun evaluateExpression(expression: String): Double {
        val tokens = tokenize(expression)
        val parser = ExpressionParser(tokens)
        return parser.parse()
    }

    fun processMathGoal(service: LocalAgentService, rawGoal: String, voiceSynthesizer: VoiceSynthesizer? = null): Boolean {
        val expression = extractExpression(rawGoal)
        service.broadcastTelemetryLog("MATH", "Evaluating in-memory math expression: '$expression'")
        return try {
            val resultValue = evaluateExpression(expression)
            val formattedResult = if (resultValue % 1.0 == 0.0) {
                resultValue.toLong().toString()
            } else {
                BigDecimal(resultValue).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            }

            val speechText = "The result is $formattedResult"
            service.broadcastTelemetryLog("MATH", "In-memory evaluation success: $expression = $formattedResult")
            service.broadcastGoalCompleted(rawGoal, "SUCCESS", formattedResult)
            voiceSynthesizer?.speak(speechText)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating expression '$expression'", e)
            service.broadcastTelemetryLog("MATH", "In-memory evaluation failed for '$expression': ${e.message}")
            false
        }
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (c in "+-*/^%()") {
                tokens.add(c.toString())
                i++
            } else if (c.isDigit() || c == '.') {
                val sb = StringBuilder()
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                    sb.append(expr[i])
                    i++
                }
                tokens.add(sb.toString())
            } else {
                i++
            }
        }
        return tokens
    }

    private class ExpressionParser(private val tokens: List<String>) {
        private var pos = 0

        fun parse(): Double {
            val result = parseAddSub()
            if (pos < tokens.size) {
                throw IllegalArgumentException("Unexpected token '${tokens[pos]}' at position $pos")
            }
            return result
        }

        private fun parseAddSub(): Double {
            var left = parseMulDivMod()
            while (pos < tokens.size) {
                val op = tokens[pos]
                if (op == "+" || op == "-") {
                    pos++
                    val right = parseMulDivMod()
                    left = if (op == "+") left + right else left - right
                } else {
                    break
                }
            }
            return left
        }

        private fun parseMulDivMod(): Double {
            var left = parsePower()
            while (pos < tokens.size) {
                val op = tokens[pos]
                if (op == "*" || op == "/" || op == "%") {
                    pos++
                    val right = parsePower()
                    left = when (op) {
                        "*" -> left * right
                        "/" -> if (right == 0.0) throw ArithmeticException("Division by zero") else left / right
                        else -> left % right
                    }
                } else {
                    break
                }
            }
            return left
        }

        private fun parsePower(): Double {
            var left = parsePrimary()
            while (pos < tokens.size && tokens[pos] == "^") {
                pos++
                val right = parsePrimary()
                left = left.pow(right)
            }
            return left
        }

        private fun parsePrimary(): Double {
            if (pos >= tokens.size) throw IllegalArgumentException("Unexpected end of expression")
            val token = tokens[pos]
            if (token == "-") {
                pos++
                return -parsePrimary()
            }
            if (token == "+") {
                pos++
                return parsePrimary()
            }
            if (token == "(") {
                pos++
                val valInParens = parseAddSub()
                if (pos >= tokens.size || tokens[pos] != ")") {
                    throw IllegalArgumentException("Missing closing parenthesis")
                }
                pos++
                return valInParens
            }
            pos++
            return token.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number '$token'")
        }
    }
}
