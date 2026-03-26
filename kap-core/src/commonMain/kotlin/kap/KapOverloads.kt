// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew :kap-core:generateKap                                │
// └──────────────────────────────────────────────────────────────────────┘
package kap

import kap.internal.curried

// ── kap: curry and wrap for .with chains ────────────────────────────────

/** Curries [f] and wraps it as a [Kap], ready for [with] chains. */

fun <P1, P2, R> kap(f: (P1, P2) -> R): Kap<(P1) -> (P2) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, R> kap(f: (P1, P2, P3) -> R): Kap<(P1) -> (P2) -> (P3) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, R> kap(f: (P1, P2, P3, P4) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, R> kap(f: (P1, P2, P3, P4, P5) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, R> kap(f: (P1, P2, P3, P4, P5, P6) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, R> kap(f: (P1, P2, P3, P4, P5, P6, P7) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> R> = Kap.of(f.curried())

fun <P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22, R> kap(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) -> R): Kap<(P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> (P22) -> R> = Kap.of(f.curried())
