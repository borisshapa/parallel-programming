package dijkstra

import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.locks.ReentrantLock
import kotlin.Comparator
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }

class PriorityMultiQueue<T>(private val nWorkers: Int, private val comparator: Comparator<T>) {
    private val queuesCount = 2 * nWorkers;
    private val queues = List(queuesCount) { BlockingPriorityQueue(nWorkers, comparator) }
    private val random = Random()

    private fun <R> doWithLocking(action: (PriorityQueue<T>) -> R): R {
        while (true) {
            val randInd = random.nextInt(queuesCount)
            val queue = queues[randInd]

            if (queue.getLock().tryLock()) {
                return try {
                    action(queue)
                } finally {
                    queue.getLock().unlock()
                }
            }
        }
    }

    fun add(elem: T): Boolean = doWithLocking { q -> q.offer(elem) }

    fun poll(): T? {
        return doWithLocking { q1 ->
            doWithLocking { q2 ->
                val q1Min = q1.peek()
                val q2Min = q2.peek()
                when {
                    q1Min == null && q2Min == null -> null
                    q1Min == null -> q2.poll()
                    q2Min == null -> q1.poll()
                    else -> if (comparator.compare(q1Min, q2Min) < 0) q1.poll() else q2.poll()
                }
            }
        }
    }

    class BlockingPriorityQueue<T>(initialCapacity: Int, comparator: Comparator<T>)
        : PriorityQueue<T>(initialCapacity, comparator) {
        private val lock = ReentrantLock()

        fun getLock(): ReentrantLock {
            return lock
        }
    }
}

private val activeNodes = atomic(1)

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    // Create a priority (by distance) queue and add the start node into it
    val q = PriorityMultiQueue(workers, NODE_DISTANCE_COMPARATOR)
    q.add(start)
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    activeNodes.lazySet(1)

    repeat(workers) {
        thread {
            while (true) {
                val cur: Node = q.poll() ?: if (activeNodes.value == 0) break else continue
                for (e in cur.outgoingEdges) {
                    val newDistance = cur.distance + e.weight
//                    println("${e.to.distance} ${newDistance}")
                    while (true) {
                        val distance = e.to.distance
                        if (distance <= newDistance) {
                            break
                        }
                        if (e.to.casDistance(distance, newDistance)) {
                            q.add(e.to)
                            activeNodes.incrementAndGet()
                            break
                        }
                    }
                }
                activeNodes.decrementAndGet()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}