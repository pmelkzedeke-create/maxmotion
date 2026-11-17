package com.example.engine

import com.example.model.AnimatableProperty
import com.example.model.CompositionNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class ExpressionContext(
    val timeMs: Long,
    val fps: Int,
    val durationMs: Long,
    val baseKeyframeValue: Float,
    val currentProperty: AnimatableProperty,
    val composition: CompositionNode,
    val audioAmplitude: Float = 0f,
    val audioBass: Float = 0f,
    val depth: Int = 0
)

/**
 * Deterministic lightweight expression evaluator supporting After Effects style expressions:
 * - time, frame, value, duration, pi, audioAmp, audioBass
 * - wiggle(freq, amp)
 * - loopOut("cycle" | "pingpong" | "continue")
 * - valueAtTime(tSeconds)
 * - linear(t, tMin, tMax, v1, v2) & ease(t, tMin, tMax, v1, v2)
 * - clamp(v, min, max), sin, cos, abs, sqrt, pow, min, max, floor, round
 * - layer("Name").prop (e.g., layer("Prisma Cinético").scaleX)
 */
object ExpressionEvaluator {

    data class EvalResult(
        val value: Float,
        val error: String? = null
    )

    fun evaluate(expression: String?, ctx: ExpressionContext): EvalResult {
        if (expression.isNullOrBlank()) return EvalResult(ctx.baseKeyframeValue)
        if (ctx.depth > 4) return EvalResult(ctx.baseKeyframeValue, "Recursão excedida")
        return try {
            val tokens = tokenize(expression)
            val parser = Parser(tokens, ctx)
            val value = parser.parseExpression().toFloat()
            if (value.isNaN() || value.isInfinite()) {
                EvalResult(ctx.baseKeyframeValue, "Resultado inválido (NaN/Inf)")
            } else {
                EvalResult(value)
            }
        } catch (e: Exception) {
            EvalResult(ctx.baseKeyframeValue, e.message ?: "Erro de sintaxe")
        }
    }

    private sealed interface Token {
        data class Num(val v: Double) : Token
        data class Ident(val s: String) : Token
        data class Str(val s: String) : Token
        data class Op(val c: Char) : Token
        data object LParen : Token
        data object RParen : Token
        data object Comma : Token
        data object Dot : Token
    }

    private fun tokenize(src: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() || c == ';' -> i++
                c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    out.add(Token.Num(src.substring(start, i).toDouble()))
                }
                c == '"' || c == '\'' -> {
                    val quote = c
                    i++
                    val start = i
                    while (i < src.length && src[i] != quote) i++
                    out.add(Token.Str(src.substring(start, i)))
                    if (i < src.length) i++
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    out.add(Token.Ident(src.substring(start, i)))
                }
                c == '(' -> { out.add(Token.LParen); i++ }
                c == ')' -> { out.add(Token.RParen); i++ }
                c == ',' -> { out.add(Token.Comma); i++ }
                c == '.' -> { out.add(Token.Dot); i++ }
                c in listOf('+', '-', '*', '/', '%') -> { out.add(Token.Op(c)); i++ }
                else -> throw IllegalArgumentException("Símbolo inesperado: '$c'")
            }
        }
        return out
    }

    private class Parser(
        private val tokens: List<Token>,
        private val ctx: ExpressionContext
    ) {
        private var pos = 0

        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun next(): Token = tokens[pos++]

        fun parseExpression(): Double = parseAddSub()

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.c == '+' || t.c == '-')) {
                    next()
                    val right = parseMulDiv()
                    left = if (t.c == '+') left + right else left - right
                } else break
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parseUnary()
            while (true) {
                val t = peek()
                if (t is Token.Op && (t.c == '*' || t.c == '/' || t.c == '%')) {
                    next()
                    val right = parseUnary()
                    left = when (t.c) {
                        '*' -> left * right
                        '/' -> if (abs(right) < 1e-9) 0.0 else left / right
                        else -> if (abs(right) < 1e-9) 0.0 else left % right
                    }
                } else break
            }
            return left
        }

        private fun parseUnary(): Double {
            val t = peek()
            if (t is Token.Op && (t.c == '-' || t.c == '+')) {
                next()
                val v = parseUnary()
                return if (t.c == '-') -v else v
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            return when (val t = peek()) {
                is Token.Num -> { next(); t.v }
                is Token.LParen -> {
                    next()
                    val v = parseExpression()
                    if (peek() is Token.RParen) next()
                    v
                }
                is Token.Ident -> {
                    next()
                    if (t.s == "layer" && peek() is Token.LParen) {
                        parseLayerPropertyRef()
                    } else if (peek() is Token.LParen) {
                        parseFunctionCall(t.s)
                    } else {
                        resolveIdentifier(t.s)
                    }
                }
                else -> throw IllegalArgumentException("Expressão incompleta")
            }
        }

        private fun parseLayerPropertyRef(): Double {
            next() // '('
            val nameToken = next()
            val layerName = (nameToken as? Token.Str)?.s
                ?: throw IllegalArgumentException("Nome de camada esperado em layer(\"...\")")
            if (peek() is Token.RParen) next()
            if (peek() is Token.Dot) next()
            val propName = (next() as? Token.Ident)?.s
                ?: throw IllegalArgumentException("Propriedade esperada após layer(...).")
            val target = ctx.composition.layers.firstOrNull { it.name.equals(layerName, ignoreCase = true) }
                ?: return ctx.baseKeyframeValue.toDouble()
            val prop = when (propName.lowercase()) {
                "posx", "x" -> target.transform.posX
                "posy", "y" -> target.transform.posY
                "posz", "z" -> target.transform.posZ
                "scalex", "scale" -> target.transform.scaleX
                "scaley" -> target.transform.scaleY
                "rotation", "rotz", "rot" -> target.transform.rotZ
                "rotx" -> target.transform.rotX
                "roty" -> target.transform.rotY
                "opacity" -> target.transform.opacity
                "anchorx" -> target.transform.anchorX
                "anchory" -> target.transform.anchorY
                else -> return ctx.baseKeyframeValue.toDouble()
            }
            val evaluated = CurveInterpolator.evaluateKeyframes(prop, ctx.timeMs)
            return if (!prop.expression.isNullOrBlank()) {
                evaluate(
                    prop.expression,
                    ctx.copy(baseKeyframeValue = evaluated, currentProperty = prop, depth = ctx.depth + 1)
                ).value.toDouble()
            } else {
                evaluated.toDouble()
            }
        }

        private fun resolveIdentifier(name: String): Double {
            val timeSec = ctx.timeMs / 1000.0
            return when (name.lowercase()) {
                "time" -> timeSec
                "frame" -> timeSec * ctx.fps
                "duration" -> ctx.durationMs / 1000.0
                "value" -> ctx.baseKeyframeValue.toDouble()
                "pi" -> PI
                "audioamp", "audio" -> ctx.audioAmplitude.toDouble()
                "audiobass", "bass" -> ctx.audioBass.toDouble()
                else -> throw IllegalArgumentException("Variável desconhecida: $name")
            }
        }

        private fun parseArgs(): List<Any> {
            val args = mutableListOf<Any>()
            next() // '('
            if (peek() !is Token.RParen) {
                while (true) {
                    val p = peek()
                    if (p is Token.Str) {
                        next()
                        args.add(p.s)
                    } else {
                        args.add(parseExpression())
                    }
                    if (peek() is Token.Comma) {
                        next()
                    } else break
                }
            }
            if (peek() is Token.RParen) next()
            return args
        }

        private fun numArg(args: List<Any>, idx: Int, default: Double = 0.0): Double =
            (args.getOrNull(idx) as? Number)?.toDouble() ?: default

        private fun strArg(args: List<Any>, idx: Int, default: String = ""): String =
            args.getOrNull(idx)?.toString() ?: default

        private fun parseFunctionCall(fn: String): Double {
            val args = parseArgs()
            val timeSec = ctx.timeMs / 1000.0
            return when (fn.lowercase()) {
                "wiggle" -> {
                    val freq = numArg(args, 0, 2.0)
                    val amp = numArg(args, 1, 15.0)
                    val n = smoothNoise1D(timeSec * freq)
                    ctx.baseKeyframeValue.toDouble() + n * amp
                }
                "loopout" -> {
                    val mode = strArg(args, 0, "cycle").lowercase()
                    computeLoopOut(mode)
                }
                "valueattime" -> {
                    val tSec = numArg(args, 0, timeSec)
                    CurveInterpolator.evaluateKeyframes(ctx.currentProperty, (tSec * 1000.0).toLong()).toDouble()
                }
                "linear" -> {
                    val t = numArg(args, 0)
                    val tMin = numArg(args, 1, 0.0)
                    val tMax = numArg(args, 2, 1.0)
                    val v1 = numArg(args, 3, 0.0)
                    val v2 = numArg(args, 4, 1.0)
                    val u = if (abs(tMax - tMin) < 1e-6) 0.0 else ((t - tMin) / (tMax - tMin)).coerceIn(0.0, 1.0)
                    v1 + (v2 - v1) * u
                }
                "ease" -> {
                    val t = numArg(args, 0)
                    val tMin = numArg(args, 1, 0.0)
                    val tMax = numArg(args, 2, 1.0)
                    val v1 = numArg(args, 3, 0.0)
                    val v2 = numArg(args, 4, 1.0)
                    val u = if (abs(tMax - tMin) < 1e-6) 0.0 else ((t - tMin) / (tMax - tMin)).coerceIn(0.0, 1.0)
                    val smooth = u * u * (3.0 - 2.0 * u)
                    v1 + (v2 - v1) * smooth
                }
                "clamp" -> {
                    val v = numArg(args, 0)
                    val lo = numArg(args, 1)
                    val hi = numArg(args, 2, 1.0)
                    v.coerceIn(min(lo, hi), max(lo, hi))
                }
                "sin" -> sin(numArg(args, 0))
                "cos" -> cos(numArg(args, 0))
                "abs" -> abs(numArg(args, 0))
                "sqrt" -> sqrt(max(0.0, numArg(args, 0)))
                "pow" -> numArg(args, 0).pow(numArg(args, 1, 1.0))
                "min" -> min(numArg(args, 0), numArg(args, 1))
                "max" -> max(numArg(args, 0), numArg(args, 1))
                "floor" -> floor(numArg(args, 0))
                "round" -> round(numArg(args, 0))
                else -> throw IllegalArgumentException("Função desconhecida: $fn")
            }
        }

        private fun computeLoopOut(mode: String): Double {
            val kfs = ctx.currentProperty.keyframes
            if (kfs.size < 2) return ctx.baseKeyframeValue.toDouble()
            val startMs = kfs.first().timeMs
            val endMs = kfs.last().timeMs
            val span = (endMs - startMs).coerceAtLeast(1L)
            if (ctx.timeMs <= endMs) return ctx.baseKeyframeValue.toDouble()
            val elapsed = ctx.timeMs - startMs
            return when (mode) {
                "pingpong" -> {
                    val cycleIdx = (elapsed / span).toInt()
                    val rem = elapsed % span
                    val mapped = if (cycleIdx % 2 == 1) endMs - rem else startMs + rem
                    CurveInterpolator.evaluateKeyframes(ctx.currentProperty, mapped).toDouble()
                }
                "continue" -> {
                    val kPrev = kfs[kfs.size - 2]
                    val kLast = kfs.last()
                    val dt = ((kLast.timeMs - kPrev.timeMs).coerceAtLeast(1L)) / 1000.0
                    val slope = (kLast.value - kPrev.value) / dt
                    val extraSec = (ctx.timeMs - endMs) / 1000.0
                    kLast.value + slope * extraSec
                }
                else -> { // "cycle"
                    val mapped = startMs + (elapsed % span)
                    CurveInterpolator.evaluateKeyframes(ctx.currentProperty, mapped).toDouble()
                }
            }
        }

        private fun smoothNoise1D(x: Double): Double {
            val i0 = floor(x)
            val f = x - i0
            val u = f * f * (3.0 - 2.0 * f)
            val a = hash1D(i0)
            val b = hash1D(i0 + 1.0)
            return (a + (b - a) * u) * 2.0 - 1.0
        }

        private fun hash1D(n: Double): Double {
            val s = sin(n * 127.1 + 311.7) * 43758.5453123
            return s - floor(s)
        }
    }
}
