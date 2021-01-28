/**
 * @author :Shaposhnikov Boris
 */
class Solution : AtomicCounter {
    // объявите здесь нужные вам поля
    private val root = Node(0)
    private val last : ThreadLocal<Node> = ThreadLocal.withInitial { root }

    override fun getAndAdd(x: Int): Int {
        // напишите здесь код
        var res: Int
        do {
            val old = last.get().value
            res = old
            val node = Node(old + x)
            last.set(last.get().next.decide(node))
        } while (last.get() != node)
        return res
    }

    // вам наверняка потребуется дополнительный класс
    private class Node (val value: Int) {
        val next = Consensus<Node>()
    }
}
