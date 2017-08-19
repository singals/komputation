package shape.komputation.cuda.layers.forward.projection

import jcuda.Pointer
import shape.komputation.cuda.layers.BaseCudaForwardLayer
import shape.komputation.optimization.Optimizable

class CublasProjectionLayer internal constructor(
    name: String?,
    private val weightingLayer: CublasWeightingLayer,
    private val biasLayer: CublasBiasLayer? = null) : BaseCudaForwardLayer(name), Optimizable {

    override var deviceForwardResult = Pointer()
    override var numberOutputRows = -1
    override var maximumOutputColumns = -1

    override var deviceBackwardResult = Pointer()
    override var numberInputRows = -1
    override var maximumInputColumns = -1

    override fun forward(batchSize: Int, deviceNumberInputColumns: Pointer, deviceInput: Pointer, isTraining: Boolean): Pointer {

        val weighted = this.weightingLayer.forward(batchSize, deviceNumberInputColumns, deviceInput, isTraining)

        if (this.biasLayer == null) {

            this.deviceForwardResult = this.weightingLayer.deviceForwardResult
            this.numberOutputRows = this.weightingLayer.numberOutputRows
            this.maximumOutputColumns = this.weightingLayer.maximumOutputColumns

            return weighted

        }
        else {

            val weightedAndBiased = this.biasLayer.forward(batchSize, this.deviceNumberOutputColumns, weighted, isTraining)

            this.deviceBackwardResult = this.weightingLayer.deviceBackwardResult
            this.numberInputRows = this.weightingLayer.numberInputRows
            this.maximumInputColumns = this.weightingLayer.maximumInputColumns

            return weightedAndBiased

        }

    }

    override fun backward(batchSize: Int, chain: Pointer): Pointer {

        val backwardWeighting = this.weightingLayer.backward(batchSize, chain)

        if (this.biasLayer != null) {

            this.biasLayer.backward(batchSize, chain)

        }

        return backwardWeighting

    }

    override fun optimize(scalingFactor: Float) {

        this.weightingLayer.optimize(scalingFactor)

        this.biasLayer?.optimize(scalingFactor)

    }

}