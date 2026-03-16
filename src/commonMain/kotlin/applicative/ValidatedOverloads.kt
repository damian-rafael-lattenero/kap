// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew generateValidatedOverloads                           │
// └──────────────────────────────────────────────────────────────────────┘
package applicative

import kotlinx.coroutines.async
import applicative.internal.curried

// ── zipV: parallel validation with full type inference ───────────────────

fun <E, A, B, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    combine: (A, B) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val ea = da.await()
    val eb = db.await()
    if (ea is Either.Right && eb is Either.Right)
        Either.Right(combine(ea.value, eb.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    combine: (A, B, C) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    combine: (A, B, C, D) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    combine: (A, B, C, D, F) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    combine: (A, B, C, D, F, G) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    combine: (A, B, C, D, F, G, H) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    combine: (A, B, C, D, F, G, H, I) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    combine: (A, B, C, D, F, G, H, I, J) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    combine: (A, B, C, D, F, G, H, I, J, K) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    combine: (A, B, C, D, F, G, H, I, J, K, L) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    fO: suspend () -> Either<Nel<E>, O>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val dO = async { fO() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    val eO = dO.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
            if (eO is Either.Left) add(eO.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    fO: suspend () -> Either<Nel<E>, O>,
    fp: suspend () -> Either<Nel<E>, P>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val dO = async { fO() }
    val dp = async { fp() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    val eO = dO.await()
    val ep = dp.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
            if (eO is Either.Left) add(eO.value)
            if (ep is Either.Left) add(ep.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    fO: suspend () -> Either<Nel<E>, O>,
    fp: suspend () -> Either<Nel<E>, P>,
    fq: suspend () -> Either<Nel<E>, Q>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val dO = async { fO() }
    val dp = async { fp() }
    val dq = async { fq() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    val eO = dO.await()
    val ep = dp.await()
    val eq = dq.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
            if (eO is Either.Left) add(eO.value)
            if (ep is Either.Left) add(ep.value)
            if (eq is Either.Left) add(eq.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    fO: suspend () -> Either<Nel<E>, O>,
    fp: suspend () -> Either<Nel<E>, P>,
    fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val dO = async { fO() }
    val dp = async { fp() }
    val dq = async { fq() }
    val ds = async { fs() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    val eO = dO.await()
    val ep = dp.await()
    val eq = dq.await()
    val es = ds.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
            if (eO is Either.Left) add(eO.value)
            if (ep is Either.Left) add(ep.value)
            if (eq is Either.Left) add(eq.value)
            if (es is Either.Left) add(es.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    fO: suspend () -> Either<Nel<E>, O>,
    fp: suspend () -> Either<Nel<E>, P>,
    fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>,
    ft: suspend () -> Either<Nel<E>, T>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val dO = async { fO() }
    val dp = async { fp() }
    val dq = async { fq() }
    val ds = async { fs() }
    val dt = async { ft() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    val eO = dO.await()
    val ep = dp.await()
    val eq = dq.await()
    val es = ds.await()
    val et = dt.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right && et is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value, et.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
            if (eO is Either.Left) add(eO.value)
            if (ep is Either.Left) add(ep.value)
            if (eq is Either.Left) add(eq.value)
            if (es is Either.Left) add(es.value)
            if (et is Either.Left) add(et.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    fO: suspend () -> Either<Nel<E>, O>,
    fp: suspend () -> Either<Nel<E>, P>,
    fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>,
    ft: suspend () -> Either<Nel<E>, T>,
    fu: suspend () -> Either<Nel<E>, U>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val dO = async { fO() }
    val dp = async { fp() }
    val dq = async { fq() }
    val ds = async { fs() }
    val dt = async { ft() }
    val du = async { fu() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    val eO = dO.await()
    val ep = dp.await()
    val eq = dq.await()
    val es = ds.await()
    val et = dt.await()
    val eu = du.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right && et is Either.Right && eu is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value, et.value, eu.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
            if (eO is Either.Left) add(eO.value)
            if (ep is Either.Left) add(ep.value)
            if (eq is Either.Left) add(eq.value)
            if (es is Either.Left) add(es.value)
            if (et is Either.Left) add(et.value)
            if (eu is Either.Left) add(eu.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    fO: suspend () -> Either<Nel<E>, O>,
    fp: suspend () -> Either<Nel<E>, P>,
    fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>,
    ft: suspend () -> Either<Nel<E>, T>,
    fu: suspend () -> Either<Nel<E>, U>,
    fv: suspend () -> Either<Nel<E>, V>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val dO = async { fO() }
    val dp = async { fp() }
    val dq = async { fq() }
    val ds = async { fs() }
    val dt = async { ft() }
    val du = async { fu() }
    val dv = async { fv() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    val eO = dO.await()
    val ep = dp.await()
    val eq = dq.await()
    val es = ds.await()
    val et = dt.await()
    val eu = du.await()
    val ev = dv.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right && et is Either.Right && eu is Either.Right && ev is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value, et.value, eu.value, ev.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
            if (eO is Either.Left) add(eO.value)
            if (ep is Either.Left) add(ep.value)
            if (eq is Either.Left) add(eq.value)
            if (es is Either.Left) add(es.value)
            if (et is Either.Left) add(et.value)
            if (eu is Either.Left) add(eu.value)
            if (ev is Either.Left) add(ev.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    fO: suspend () -> Either<Nel<E>, O>,
    fp: suspend () -> Either<Nel<E>, P>,
    fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>,
    ft: suspend () -> Either<Nel<E>, T>,
    fu: suspend () -> Either<Nel<E>, U>,
    fv: suspend () -> Either<Nel<E>, V>,
    fw: suspend () -> Either<Nel<E>, W>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val dn = async { fn() }
    val dO = async { fO() }
    val dp = async { fp() }
    val dq = async { fq() }
    val ds = async { fs() }
    val dt = async { ft() }
    val du = async { fu() }
    val dv = async { fv() }
    val dw = async { fw() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    val en = dn.await()
    val eO = dO.await()
    val ep = dp.await()
    val eq = dq.await()
    val es = ds.await()
    val et = dt.await()
    val eu = du.await()
    val ev = dv.await()
    val ew = dw.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right && et is Either.Right && eu is Either.Right && ev is Either.Right && ew is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value, et.value, eu.value, ev.value, ew.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
            if (en is Either.Left) add(en.value)
            if (eO is Either.Left) add(eO.value)
            if (ep is Either.Left) add(ep.value)
            if (eq is Either.Left) add(eq.value)
            if (es is Either.Left) add(es.value)
            if (et is Either.Left) add(et.value)
            if (eu is Either.Left) add(eu.value)
            if (ev is Either.Left) add(ev.value)
            if (ew is Either.Left) add(ew.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

// ── liftV: curried validated lift ────────────────────────────────────────

fun <E, P1, P2, R> liftV2(f: (P1, P2) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, R> liftV3(f: (P1, P2, P3) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, R> liftV4(f: (P1, P2, P3, P4) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, R> liftV5(f: (P1, P2, P3, P4, P5) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, R> liftV6(f: (P1, P2, P3, P4, P5, P6) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, R> liftV7(f: (P1, P2, P3, P4, P5, P6, P7) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, R> liftV8(f: (P1, P2, P3, P4, P5, P6, P7, P8) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, R> liftV9(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, R> liftV10(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, R> liftV11(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, R> liftV12(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, R> liftV13(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, R> liftV14(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, R> liftV15(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, R> liftV16(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, R> liftV17(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, R> liftV18(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, R> liftV19(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, R> liftV20(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, R> liftV21(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22, R> liftV22(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> (P22) -> R>> =
    pure(Either.Right(f.curried()))
