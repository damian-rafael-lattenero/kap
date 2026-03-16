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