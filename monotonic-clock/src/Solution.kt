/**
 * В теле класса решения разрешено использовать только переменные делегированные в класс RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author : Shaposhnikov Boris
 */
class Solution : MonotonicClock {
    private var c11 by RegularInt(0)
    private var c12 by RegularInt(0)
    private var c13 by RegularInt(0)

    private var c21 by RegularInt(0)
    private var c22 by RegularInt(0)
    private var c23 by RegularInt(0)

    override fun write(time: Time) {
        // write right-to-left
        c21 = time.d1
        c22 = time.d2
        c23 = time.d3

        c13 = time.d3
        c12 = time.d2
        c11 = time.d1
    }

    override fun read(): Time {
        // read left-to-right
        val r11 = c11
        val r12 = c12
        val r13 = c13

        val r23 = c23
        val r22 = c22
        val r21 = c21

        var (d1, d2, d3) = listOf(0, 0, 0)

        if (r11 == r21) {
            d1 = r11
            if (r12 == r22) {
                d2 = r12
                if (r13 == r23) {
                    d3 = r13
                } else {
                    d3 = r23
                }
            } else {
                d2 = r22
            }
        } else {
            d1 = r21
        }

        return Time(d1, d2, d3)
    }
}