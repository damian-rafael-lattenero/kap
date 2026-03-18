package applicative

/**
 * A non-empty list — guarantees at least one element at the type level.
 *
 * Used as the error accumulator in [validated][apV] computations.
 * Extends [AbstractList] for zero-allocation `List` compliance — `size` and
 * `get` are computed on the fly from [head] and [tail] without creating a
 * backing list.
 *
 * **Note on equality:** [equals] and [hashCode] are inherited from [AbstractList],
 * which compares element-by-element. Two `NonEmptyList` instances with the same elements
 * in the same order are equal regardless of how they were constructed.
 */
class NonEmptyList<out A>(val head: A, val tail: List<A> = emptyList()) : AbstractList<A>() {

    override val size: Int get() = 1 + tail.size

    override fun get(index: Int): A =
        if (index == 0) head else tail[index - 1]

    override fun isEmpty(): Boolean = false

    companion object {
        /** Creates a [NonEmptyList] from one or more elements. */
        fun <A> of(head: A, vararg tail: A): NonEmptyList<A> = NonEmptyList(head, tail.toList())

        /** Returns a [NonEmptyList] or `null` if the input list is empty. */
        fun <A> fromList(list: List<A>): NonEmptyList<A>? =
            if (list.isEmpty()) null else NonEmptyList(list[0], list.subList(1, list.size))
    }

    override fun toString(): String = "NonEmptyList(${joinToString(", ")})"
}

/** Concatenates two non-empty lists, preserving all elements in order. */
operator fun <A> NonEmptyList<A>.plus(other: NonEmptyList<A>): NonEmptyList<A> =
    NonEmptyList(head, buildList(tail.size + other.size) {
        addAll(tail)
        add(other.head)
        addAll(other.tail)
    })

/** Transforms each element of the list. */
fun <A, B> NonEmptyList<A>.map(f: (A) -> B): NonEmptyList<B> =
    NonEmptyList(f(head), tail.map(f))

/** Monadic bind — applies [f] to each element and flattens the results. */
fun <A, B> NonEmptyList<A>.flatMap(f: (A) -> NonEmptyList<B>): NonEmptyList<B> {
    val first = f(head)
    val rest = buildList {
        addAll(first.tail)
        for (a in tail) {
            val nel = f(a)
            add(nel.head)
            addAll(nel.tail)
        }
    }
    return NonEmptyList(first.head, rest)
}

/** Pairwise zip, truncated to the shorter list. */
fun <A, B> NonEmptyList<A>.zip(other: NonEmptyList<B>): NonEmptyList<Pair<A, B>> =
    zip(other, ::Pair)

/** Zip with a combining function, truncated to the shorter list. */
fun <A, B, C> NonEmptyList<A>.zip(other: NonEmptyList<B>, f: (A, B) -> C): NonEmptyList<C> =
    NonEmptyList(f(head, other.head), tail.zip(other.tail, f))

/** Deduplicates elements preserving order; the head always stays. */
fun <A> NonEmptyList<A>.distinct(): NonEmptyList<A> {
    val seen = mutableSetOf(head)
    return NonEmptyList(head, tail.filter(seen::add))
}

/** Sorts by [selector] while preserving the non-empty guarantee. */
fun <A> NonEmptyList<A>.sortedBy(selector: (A) -> Comparable<*>): NonEmptyList<A> {
    @Suppress("UNCHECKED_CAST")
    val sorted = (this as List<A>).sortedBy(selector as (A) -> Comparable<Any?>)
    return NonEmptyList(sorted[0], sorted.subList(1, sorted.size))
}

/** Returns elements in reverse order. */
fun <A> NonEmptyList<A>.reversed(): NonEmptyList<A> {
    val rev = (this as List<A>).reversed()
    return NonEmptyList(rev[0], rev.subList(1, rev.size))
}

/** Appends a single element. */
operator fun <A> NonEmptyList<A>.plus(element: @UnsafeVariance A): NonEmptyList<A> =
    NonEmptyList(head, tail + element)

/** Wraps a single value into a [NonEmptyList]. */
fun <A> A.toNonEmptyList(): NonEmptyList<A> = NonEmptyList(this)

// ── traverse / sequence ──────────────────────────────────────────────────

/**
 * Applies [f] to each element, producing a [Computation] per element,
 * then runs all computations in parallel and collects results into a [NonEmptyList].
 *
 * The non-empty guarantee is preserved: since the input is non-empty and [f]
 * produces exactly one computation per element, the output is also non-empty.
 *
 * ```
 * val users: NonEmptyList<UserId> = NonEmptyList.of(1, 2, 3)
 * val profiles: NonEmptyList<Profile> = Async {
 *     users.traverse { id -> Computation { fetchProfile(id) } }
 * }
 * ```
 */
fun <A, B> NonEmptyList<A>.traverse(f: (A) -> Computation<B>): Computation<NonEmptyList<B>> =
    (this as List<A>).traverse(f).map { list -> NonEmptyList(list[0], list.subList(1, list.size)) }

/**
 * Like [traverse] but limits the number of concurrent computations.
 */
fun <A, B> NonEmptyList<A>.traverse(concurrency: Int, f: (A) -> Computation<B>): Computation<NonEmptyList<B>> =
    (this as List<A>).traverse(concurrency, f).map { list -> NonEmptyList(list[0], list.subList(1, list.size)) }

/**
 * Runs all computations in the [NonEmptyList] in parallel, collecting results
 * into a new [NonEmptyList].
 */
fun <A> NonEmptyList<Computation<A>>.sequence(): Computation<NonEmptyList<A>> =
    traverse { it }

/**
 * Like [sequence] but limits the number of concurrent computations.
 */
fun <A> NonEmptyList<Computation<A>>.sequence(concurrency: Int): Computation<NonEmptyList<A>> =
    traverse(concurrency) { it }

/**
 * Applies [f] to each element producing a validated computation,
 * runs all in parallel, and accumulates errors from all branches.
 *
 * Preserves the [NonEmptyList] guarantee on both the success and error sides.
 */
fun <E, A, B> NonEmptyList<A>.traverseV(
    f: (A) -> Computation<Either<NonEmptyList<E>, B>>,
): Computation<Either<NonEmptyList<E>, NonEmptyList<B>>> =
    (this as List<A>).traverseV(f).map { either ->
        either.map { list -> NonEmptyList(list[0], list.subList(1, list.size)) }
    }

/**
 * Like [traverseV] but limits the number of concurrent computations.
 */
fun <E, A, B> NonEmptyList<A>.traverseV(
    concurrency: Int,
    f: (A) -> Computation<Either<NonEmptyList<E>, B>>,
): Computation<Either<NonEmptyList<E>, NonEmptyList<B>>> =
    (this as List<A>).traverseV(concurrency, f).map { either ->
        either.map { list -> NonEmptyList(list[0], list.subList(1, list.size)) }
    }

/** Convenience alias — shorter than [NonEmptyList] in validated signatures. */
typealias Nel<A> = NonEmptyList<A>

/**
 * Convenience alias for validated computation results.
 *
 * Reduces the verbosity of `Computation<Either<NonEmptyList<E>, A>>` in
 * function signatures and variable declarations:
 *
 * ```
 * // Before:
 * fun validateName(input: String): Computation<Either<NonEmptyList<RegError>, ValidName>>
 *
 * // After:
 * fun validateName(input: String): Validated<RegError, ValidName>
 * ```
 */
typealias Validated<E, A> = Computation<Either<NonEmptyList<E>, A>>