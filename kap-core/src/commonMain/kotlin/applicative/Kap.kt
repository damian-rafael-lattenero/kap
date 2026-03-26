// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew :kap-core:generateKap                                │
// └──────────────────────────────────────────────────────────────────────┘
package applicative

import applicative.internal.curried

// ── kap: curry and wrap for .with chains ────────────────────────────────

/** Curries [f] and wraps it as a [Effect], ready for [with] chains. */

fun <P1, P2, R> kap(f: (P1, P2) -> R): Effect<(P1) -> (P2) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, R> kap(f: (P1, P2, P3) -> R): Effect<(P1) -> (P2) -> (P3) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, R> kap(f: (P1, P2, P3, P4) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, R> kap(f: (P1, P2, P3, P4, P5) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, R> kap(f: (P1, P2, P3, P4, P5, P6) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, R> kap(f: (P1, P2, P3, P4, P5, P6, P7) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> R> = Effect.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) -> R): Effect<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> (P22) -> R> = Effect.of(f.curried())
