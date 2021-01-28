import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueueMS<E> : SynchronousQueue<E> {
    private val head = atomic(Node<E>())
    private val tail = atomic(head.value)

    private fun checkTail(tail: Node<E>, checkOperation: (node: Node<E>) -> Boolean) =
        tail == head.value || checkOperation(tail)

    private suspend fun suspend(
        checkOperation: (node: Node<E>) -> Boolean,
        supplier: (cont: Continuation<Any>, element: E?) -> Node<E>,
        element: E? = null
    ): Any? {
        return suspendCoroutine sc@{ cont ->
            val newTail = supplier(cont, element)
            val curTail = tail.value
            if (checkTail(curTail, checkOperation) && curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
            } else {
                cont.resume(RETRY)
                return@sc
            }
        }
    }

    override suspend fun send(element: E) {
        val checkOperation = { tail: Node<E> -> tail is Sender<*> }
        val supplier = {cont: Continuation<Any>, e: E? -> Sender<E>(cont as Continuation<E?>, e!!) }
        while (true) {
            val tail = this.tail.value
            if (checkTail(tail, checkOperation)) {
                if (suspend(checkOperation, supplier, element) != RETRY) {
                    return
                }
            } else {
                val head = this.head.value
                if (head == this.tail.value) {
                    continue
                }
                val newHead = head.next.value ?: continue
                if (!checkOperation(newHead) && this.head.compareAndSet(head, newHead)) {
                    newHead.cor!!.resume(element)
                    return
                }
            }
        }
    }

    override suspend fun receive(): E {
        val checkOperation = { tail: Node<E> -> tail is Receiver<*> }
        val supplier = { cont: Continuation<Any>, _: E? -> Receiver<E>(cont as Continuation<E?>) }
        while (true) {
            val tail = this.tail.value
            if (checkTail(tail, checkOperation)) {
                val res = suspend(checkOperation, supplier)
                if (res != RETRY) {
                    return res as E
                }
            } else {
                val head = this.head.value
                if (head == this.tail.value) {
                    continue
                }
                val newHead = head.next.value ?: continue
                if (head != this.tail.value && !checkOperation(newHead) && this.head.compareAndSet(head, newHead)) {
                    newHead.cor!!.resume(null)
                    return newHead.value!!
                }
            }
        }
    }

    open class Node<E> {
        val next = atomic<Node<E>?>(null)
        var cor: Continuation<E?>? = null
        var value: E? = null

        constructor()

        constructor(cor: Continuation<E?>, value: E? = null) {
            this.cor = cor
            this.value = value
        }
    }

    class Receiver<E>(cor: Continuation<E?>) : Node<E>(cor)

    class Sender<E>(cor: Continuation<E?>, value: E) : Node<E>(cor, value)

    object RETRY
}