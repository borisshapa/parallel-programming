fun main() {
    val da = DynamicArrayImpl<Int>()
    da.pushBack(1)
    da.pushBack(3)
    da.put(0, 6)
    println(da.size)
}