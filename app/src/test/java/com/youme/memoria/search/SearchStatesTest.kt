package com.youme.memoria.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchStatesTest {

    @Test
    fun searchStillWorksAfterAFailedQuery() = runBlocking {
        val errorShown = CompletableDeferred<Unit>()
        val queries = flow {
            emit("bad")
            errorShown.await()
            emit("good")
        }

        val states = queries
            .toSearchStates { query ->
                if (query == "bad") throw IllegalStateException("model failed")
                emptyList()
            }
            .onEach { if (it is SearchState.Error) errorShown.complete(Unit) }
            .toList()

        assertEquals(
            listOf(
                SearchState.Loading,
                SearchState.Error("model failed"),
                SearchState.Loading,
                SearchState.Success(emptyList()),
            ),
            states,
        )
    }
}
