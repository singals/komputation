package shape.komputation.cpu

import shape.komputation.cpu.layers.CpuEntryPoint
import shape.komputation.cpu.layers.CpuForwardLayer
import shape.komputation.cpu.layers.CpuForwardState
import shape.komputation.cpu.workflow.CpuTester
import shape.komputation.cpu.workflow.CpuTrainer
import shape.komputation.layers.*
import shape.komputation.loss.CpuLossFunctionInstruction
import shape.komputation.matrix.Matrix
import shape.komputation.optimization.Optimizable

val printLoss = { _ : Int, loss : Float -> println(loss) }

class CpuForwardPropagator(
    private val entryPoint: CpuEntryPoint,
    private val layers : Array<CpuForwardLayer>) {

    fun forward(withinBatch : Int, input : Matrix, isTraining : Boolean) : FloatArray {

        this.entryPoint.forward(input)

        var previousLayerState : CpuForwardState = this.entryPoint

        for (layer in this.layers) {

            layer.forward(withinBatch, previousLayerState.numberOutputColumns, previousLayerState.forwardResult, isTraining)

            previousLayerState = layer

        }

        return previousLayerState.forwardResult

    }

}

class CpuBackwardPropagator(
    private val entryPoint: CpuEntryPoint,
    private val layers : Array<CpuForwardLayer>) {

    private val numberLayers = this.layers.size

    fun backward(withinBatch: Int, lossGradient: FloatArray) : FloatArray {

        var chain = lossGradient

        for(indexLayer in this.numberLayers - 1 downTo 0) {

            val layer = this.layers[indexLayer]

            chain = layer.backward(withinBatch, chain)

        }

        val result = this.entryPoint.backward(chain)

        return result

    }


}

class Network(
    private val maximumBatchSize: Int,
    entryPointInstruction: CpuEntryPointInstruction,
    vararg forwardLayerInstructions: CpuForwardLayerInstruction) {

    private val entryPoint = entryPointInstruction.buildForCpu()
    private val layers = Array(forwardLayerInstructions.size) { index -> forwardLayerInstructions[index].buildForCpu() }
    private val optimizables = listOf(this.entryPoint).plus(this.layers).filterIsInstance(Optimizable::class.java).reversed().toTypedArray()

    private val forwardPropagator = CpuForwardPropagator(this.entryPoint, this.layers)
    private val backwardPropagator = CpuBackwardPropagator(this.entryPoint, this.layers)

    fun training(
        inputs: Array<Matrix>,
        targets: Array<FloatArray>,
        numberIterations : Int,
        loss: CpuLossFunctionInstruction,
        afterEachIteration : ((index : Int, loss : Float) -> Unit)? = null): CpuTrainer {

        return CpuTrainer(
            this.forwardPropagator,
            this.backwardPropagator,
            this.optimizables,
            inputs,
            targets,
            numberIterations,
            this.maximumBatchSize,
            loss.buildForCpu(),
            afterEachIteration)
        }

    fun test(
        inputs: Array<Matrix>,
        targets: Array<FloatArray>,
        batchSize: Int,
        numberCategories : Int,
        length : Int = 1) =

        CpuTester(
            this.forwardPropagator,
            inputs,
            targets,
            batchSize,
            numberCategories,
            length
        )

    init {

        acquireRecursively(this.entryPoint, this.maximumBatchSize)

        for (layer in this.layers) {

            acquireRecursively(layer, this.maximumBatchSize)

        }

    }

    fun free() {

        for (layer in this.layers) {

            releaseRecursively(layer, CpuForwardLayer::class.java)

        }

        if (this.entryPoint is Resourceful) {

            this.entryPoint.release()

        }

    }

}