package shape.komputation.cuda.layers.forward.activation

import jcuda.Pointer
import shape.komputation.cuda.layers.forward.normalization.CudaNormalizationLayer

class CudaSoftmaxLayer internal constructor(
    name : String? = null,
    private val exponentiationLayer: CudaExponentiationLayer,
    private val normalizationLayer: CudaNormalizationLayer) : BaseCudaActivationLayer(name) {

    override val numberOutputRows
        get() = this.normalizationLayer.numberOutputRows
    override val maximumOutputColumns
        get() = this.normalizationLayer.maximumOutputColumns
    override val deviceForwardResult
        get() = this.normalizationLayer.deviceForwardResult

    override val deviceBackwardResult
        get() = this.exponentiationLayer.deviceBackwardResult
    override val numberInputRows
        get() = this.exponentiationLayer.numberInputRows
    override val maximumInputColumns
        get() = this.exponentiationLayer.maximumInputColumns

    override fun forward(batchSize: Int, deviceNumberInputColumns : Pointer, deviceInput : Pointer, isTraining: Boolean): Pointer {

        val exponentiated = this.exponentiationLayer.forward(batchSize, deviceNumberInputColumns, deviceInput, isTraining)

        return this.normalizationLayer.forward(batchSize, this.exponentiationLayer.deviceNumberInputColumns, exponentiated, isTraining)

    }

    override fun backward(batchSize: Int, chain: Pointer) : Pointer {

        val backwardNormalization = this.normalizationLayer.backward(batchSize, chain)

        return this.exponentiationLayer.backward(batchSize, backwardNormalization)

    }

}