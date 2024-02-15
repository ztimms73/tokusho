package org.xtimms.tokusho.utils.lang

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

fun <T> Flow<T>.onEachWhile(action: suspend (T) -> Boolean): Flow<T> {
    var isCalled = false
    return onEach {
        if (!isCalled) {
            isCalled = action(it)
        }
    }.onCompletion {
        isCalled = false
    }
}

inline fun <T, R> Flow<List<T>>.mapItems(crossinline transform: (T) -> R): Flow<List<R>> {
    return map { list -> list.map(transform) }
}