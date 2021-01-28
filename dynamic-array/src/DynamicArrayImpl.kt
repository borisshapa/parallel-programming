import kotlinx.atomicfu.*

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val sz = atomic(0)

    private fun checkIndex(index: Int) {
        if (index >= size) {
            throw IllegalArgumentException()
        }
    }

    override fun get(index: Int): E {
        checkIndex(index)
        return core.value.array[index].value!!.value
    }

    override fun put(index: Int, element: E) {
        checkIndex(index)

        var currentCore = core.value

        while (true) {
            val ref = currentCore.array[index]
            when (val current = ref.value!!) {
                is VariableValue -> {
                    if (ref.compareAndSet(current, VariableValue(element))) {
                        return
                    }
                }
                is FixedValue, is MovedValue -> {
                    move(index)
                    val nextCoreVal = currentCore.next.value!!
                    core.compareAndSet(currentCore, nextCoreVal)
                    currentCore = nextCoreVal
                }
            }
        }
    }

    override fun pushBack(element: E) {
        var currentCore = core.value

        while (true) {
            val currentSize = size
            val currentCapacity = currentCore.capacity

            if (currentSize < currentCapacity) {
                val ref = currentCore.array[currentSize]
                if (ref.compareAndSet(null, VariableValue(element))) {
                    sz.incrementAndGet()
                    return
                }
            } else {
                if (currentSize == currentCapacity) {
                    currentCore.next.compareAndSet(null, Core(2 * currentCapacity))
                    move(0)
                    core.compareAndSet(currentCore, currentCore.next.value!!)
                }
                currentCore = core.value
            }
        }
    }

    override val size: Int get() {
        return sz.value
    }

    private fun moveValue(core: Core<E>,
                         nextCore: Core<E>,
                         ind: Int,
                         valueForCur: Value<E>,
                         valueForNext: Value<E>): Int {
        nextCore.array[ind].compareAndSet(null, valueForNext)
        core.array[ind].compareAndSet(valueForCur, MovedValue(valueForCur))
        return ind + 1
    }

    private fun move(fromIndex: Int) {
        val currentCore = core.value
        val nextCore = currentCore.next.value?: return

        var ind = fromIndex
        while (ind < currentCore.capacity) {
            val ref = currentCore.array[ind]

            when (val current = ref.value!!) {
                is MovedValue -> ind++
                is VariableValue -> {
                    val fixedVal = FixedValue(current)
                    if (ref.compareAndSet(current, fixedVal)) {
                        ind = moveValue(currentCore, nextCore, ind, fixedVal, current)
                    }
                }
                is FixedValue -> {
                    ind = moveValue(currentCore, nextCore, ind, current, VariableValue(current))
                }
            }
        }
    }
}

private class Core<E>(val capacity: Int) {
    val array = atomicArrayOfNulls<Value<E>>(capacity)
    val next = atomic<Core<E>?>(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

private open class Value<E>(val value: E)

private class FixedValue<E>(e: Value<E>): Value<E>(e.value)

private class VariableValue<E>(e: Value<E>): Value<E>(e.value) {
    constructor(value: E) : this(Value(value))
}

private class MovedValue<E>(e: Value<E>): Value<E>(e.value)