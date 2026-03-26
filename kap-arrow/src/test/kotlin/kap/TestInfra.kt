package kap

import kotlinx.coroutines.CompletableDeferred

/** Await all other latches in the list except the one at [myIndex]. */
suspend fun List<CompletableDeferred<Unit>>.awaitOthers(myIndex: Int) {
    forEachIndexed { i, latch -> if (i != myIndex) latch.await() }
}
