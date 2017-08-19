package shape.komputation.cuda.layers.entry

import jcuda.Pointer
import jcuda.runtime.JCuda.cudaFree
import shape.komputation.cpu.functions.sparselyConcatenateFloatMatrixEntries
import shape.komputation.cuda.layers.BaseCudaEntryPoint
import shape.komputation.cuda.memory.InputMemory
import shape.komputation.cuda.setFloatArray
import shape.komputation.cuda.setIntArray
import shape.komputation.layers.Resourceful
import shape.komputation.matrix.FloatMatrix
import shape.komputation.matrix.Matrix

class CudaInputLayer(
    name : String?,
    numberRows: Int,
    numberColumns : Int) : BaseCudaEntryPoint(name), Resourceful {

    override val numberOutputRows = numberRows
    override val maximumOutputColumns = numberColumns
    override var deviceNumberOutputColumns = Pointer()
    override var deviceForwardResult = Pointer()

    private val numberEntries = numberRows * numberColumns
    private var concatenation = FloatArray(0)
    private var batchInputs = emptyArray<FloatArray>()
    private var numberBatchEntries = -1

    override fun acquire(maximumBatchSize: Int) {

        this.concatenation = FloatArray(maximumBatchSize* this.numberEntries)
        this.batchInputs = Array(maximumBatchSize) { FloatArray(0) }
        this.numberBatchEntries = maximumBatchSize * numberEntries

        setIntArray(IntArray(maximumBatchSize) { this.maximumOutputColumns }, maximumBatchSize, this.deviceNumberOutputColumns)

    }

    override fun release() {

        this.concatenation = FloatArray(0)
        this.numberBatchEntries = -1

        cudaFree(this.deviceNumberOutputColumns)

    }

    override fun forward(
        batchId : Int,
        batchSize : Int,
        batch: IntArray,
        inputs : Array<Matrix>,
        memory: InputMemory): Pointer {

        this.deviceForwardResult = getData(batchId, batch, inputs, memory)

        return this.deviceForwardResult

    }

    private fun getData(batchId : Int, batch : IntArray, inputs: Array<Matrix>, memory: InputMemory): Pointer {

        val optionalDeviceForwardPointer = memory.tryToGetData(batchId)

        return if (optionalDeviceForwardPointer == null) {

            for ((withinBatch, id) in batch.withIndex()) {

                val input = inputs[id] as FloatMatrix
                this.batchInputs[withinBatch] = input.entries

            }

            sparselyConcatenateFloatMatrixEntries(this.batchInputs, this.numberOutputRows, this.concatenation)

            val deviceInput = Pointer()
            setFloatArray(this.concatenation, this.numberBatchEntries, deviceInput)

            memory.setData(batchId, deviceInput)

            deviceInput

        }
        else {

            optionalDeviceForwardPointer

        }

    }

    override fun backward(chain: Pointer) =

        chain


}