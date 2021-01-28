import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = FCArray<E>(32)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val request = Request { q.poll() }
        handleRequest(request)
        return request.value
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val request = Request { q.peek() }
        handleRequest(request)
        return request.value
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        handleRequest(Request { q.add(element); null })
    }

    private fun handleRequest(request: Request<E>) {
        fcArray.addRequest(request)
        while (true) {
            if (fcArray.tryLock()) {
                for (ind in 0 until fcArray.size) {
                    val fcRequest = fcArray.data[ind].value
                    if (fcRequest != null) {
                        fcRequest.value = fcRequest.operation.invoke()
                        fcRequest.status = Status.FINISHED
                        fcArray.data[ind].compareAndSet(fcRequest, null)
                    }
                }
                fcArray.unlock()
                return
            } else {
                if (request.status == Status.FINISHED) {
                    return
                }
            }
        }
    }

    class FCArray<E>(val size: Int) {
        val data = atomicArrayOfNulls<Request<E>>(size)
        private val lock = atomic(false)

        fun tryLock(): Boolean {
            return !lock.value && lock.compareAndSet(expect = false, update = true)
        }

        fun unlock() {
            lock.value = false
        }

        fun addRequest(request: Request<E>) {
            var ind = Random.nextInt(0, size)
            while (true) {
                if (data[ind].compareAndSet(null, request)) {
                    return
                }
                ind++
                ind %= size
            }
        }
    }

    class Request<E>(@Volatile var operation: () -> E?) {
        @Volatile
        var status = Status.PUSHED

        @Volatile
        var value: E? = null
    }

    enum class Status {
        PUSHED, FINISHED
    }
}