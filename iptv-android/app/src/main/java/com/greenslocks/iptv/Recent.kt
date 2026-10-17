package com.greenslocks.iptv

import java.util.PriorityQueue

/** Keeps the [limit] highest-scoring items out of a stream, holding only that many in memory. */
class TopN<T>(private val limit: Int) {
    private val heap = PriorityQueue<Pair<Long, T>>(11) { a, b -> a.first.compareTo(b.first) }

    fun offer(score: Long, item: T) {
        if (limit <= 0) return
        if (heap.size < limit) heap.add(score to item)
        else if (score > heap.peek().first) { heap.poll(); heap.add(score to item) }
    }

    /** Best first. */
    fun toList(): List<T> = heap.sortedByDescending { it.first }.map { it.second }
}

/** The "new episodes" rules, kept apart from the screens so they can be tested. */
object EpisodeWatch {
    /** No saved baseline yet for this series: record one without flagging anything. */
    fun needsBaseline(seenStamp: Long?, seenCount: Int?) = seenStamp == null || seenCount == null

    /** The provider says the series changed since the last time it was opened; worth counting episodes. */
    fun changedSince(stamp: Long, seenStamp: Long) = stamp > seenStamp

    /** More episodes than last time means there really is something new. */
    fun hasNew(count: Int, seenCount: Int) = count > seenCount
}
