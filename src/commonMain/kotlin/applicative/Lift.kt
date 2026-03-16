// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew generateLift                                         │
// └──────────────────────────────────────────────────────────────────────┘
package applicative

import applicative.internal.curried

// ── lift: pure . curry ──────────────────────────────────────────────────

/**
 * Curries [f] and wraps it as a pure [Computation], ready for [ap] chains.
 *
 * ```
 * lift2 { a: String, b: Int -> "$a=$b" }
 *     .ap { fetchName() }
 *     .ap { fetchAge() }
 * ```
 *
 * Overloads [lift2] through [lift22] cover arities 2-22.
 */

fun <P1, P2, R> lift2(f: (P1, P2) -> R): Computation<(P1) -> (P2) -> R> = pure(f.curried())

fun <P1, P2, P3, R> lift3(f: (P1, P2, P3) -> R): Computation<(P1) -> (P2) -> (P3) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, R> lift4(f: (P1, P2, P3, P4) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, R> lift5(f: (P1, P2, P3, P4, P5) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, R> lift6(f: (P1, P2, P3, P4, P5, P6) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, R> lift7(f: (P1, P2, P3, P4, P5, P6, P7) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, R> lift8(f: (P1, P2, P3, P4, P5, P6, P7, P8) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, R> lift9(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, R> lift10(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, R> lift11(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, R> lift12(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, R> lift13(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, R> lift14(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, R> lift15(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, R> lift16(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, R> lift17(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, R> lift18(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, R> lift19(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, R> lift20(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, R> lift21(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> R> = pure(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22, R> lift22(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) -> R): Computation<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> (P22) -> R> = pure(f.curried())
