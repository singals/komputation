package shape.komputation.demos.and

import shape.komputation.matrix.Matrix
import shape.komputation.matrix.floatMatrix

object OneHotAndData {

    val input = arrayOf<Matrix>(
        floatMatrix(0.0f, 0.0f),
        floatMatrix(0.0f, 1.0f),
        floatMatrix(1.0f, 0.0f),
        floatMatrix(1.0f, 1.0f)
    )

    val targets = arrayOf(
        floatArrayOf(1.0f, 0.0f),
        floatArrayOf(1.0f, 0.0f),
        floatArrayOf(1.0f, 0.0f),
        floatArrayOf(0.0f, 1.0f)
    )


}