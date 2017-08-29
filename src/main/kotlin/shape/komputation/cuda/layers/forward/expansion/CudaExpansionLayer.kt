package shape.komputation.cuda.layers.forward.expansion

import jcuda.Pointer
import jcuda.runtime.JCuda.cudaFree
import shape.komputation.cpu.functions.computeNumberFilterColumnPositions
import shape.komputation.cpu.functions.computeNumberFilterRowPositions
import shape.komputation.cuda.allocateDeviceFloatMemory
import shape.komputation.cuda.kernels.Kernel
import shape.komputation.cuda.kernels.launch.KernelLaunchConfiguration
import shape.komputation.cuda.layers.BaseCudaForwardLayer
import shape.komputation.cuda.layers.CudaVariableLengthForwardLayer
import shape.komputation.cuda.setIntArray
import shape.komputation.layers.Resourceful

class CudaExpansionLayer internal constructor(
    name : String?,
    override val numberInputRows : Int,
    override val maximumInputColumns : Int,
    filterHeight : Int,
    filterWidth : Int,
    private val createForwardKernel: () -> Kernel,
    private val createBackwardKernel: () -> Kernel,
    private val warpSize : Int,
    maximumNumberThreads : Int) : BaseCudaForwardLayer(name), Resourceful, CudaVariableLengthForwardLayer {

    private val pointerToFilterHeight = Pointer.to(intArrayOf(filterHeight))
    private val pointerToFilterWidth = Pointer.to(intArrayOf(filterWidth))

    private val filterSize = filterHeight * filterWidth
    private val pointerToFilterSize = Pointer.to(intArrayOf(filterSize))
    override val numberOutputRows = this.filterSize

    private var forwardKernel : Kernel? = null
    override val deviceForwardResult = Pointer()
    private val pointerToForwardResult = Pointer.to(this.deviceForwardResult)

    private var backwardKernel : Kernel? = null
    override val deviceBackwardResult = Pointer()
    private val pointerToBackwardResult = Pointer.to(this.deviceBackwardResult)

    private val batchSize = intArrayOf(-1)
    private val pointerToBatchSize = Pointer.to(this.batchSize)

    private val deviceMaximumBatchLengths = Pointer()
    private var pointerToMaximumBatchLengths = Pointer()

    private val pointerToNumberRows = Pointer.to(intArrayOf(this.numberInputRows))

    private val numberFilterRowPositions = computeNumberFilterRowPositions(this.numberInputRows, filterHeight)
    private val pointerToNumberFilterRowPositions = Pointer.to(intArrayOf(this.numberFilterRowPositions))
    private val numberFilterColumnPositions = computeNumberFilterColumnPositions(this.maximumInputColumns, filterHeight)

    private val maximumConvolutions = this.numberFilterRowPositions * this.numberFilterColumnPositions
    private val pointerToNumberConvolutions = Pointer.to(intArrayOf(this.maximumConvolutions))
    override val maximumOutputColumns = maximumConvolutions

    private val numberInputEntries = this.numberInputRows * this.maximumInputColumns
    private val pointerToNumberInputEntries = Pointer.to(intArrayOf(this.numberInputEntries))

    private val numberResultEntries = this.maximumOutputColumns * this.numberOutputRows
    private val pointerToNumberResultEntries = Pointer.to(intArrayOf(this.numberResultEntries))

    private var maximumBatchSize = -1

    private val numberForwardBlocksInYDimension = this.maximumOutputColumns
    private val numberForwardThreads = this.numberOutputRows

    private val maximumNumberWarpsPerBlock = maximumNumberThreads / this.warpSize
    private val pointerToNumberWarpsPerBlock = Pointer.to(intArrayOf(this.maximumNumberWarpsPerBlock))
    private var numberBackwardBlocksInYDimension = -1
    private var numberBackwardThreads = -1

    override fun acquire(maximumBatchSize: Int) {

        this.maximumBatchSize = maximumBatchSize

        this.forwardKernel = this.createForwardKernel()

        val maximumBatchLengths = IntArray(maximumBatchSize) { this.maximumInputColumns }
        setIntArray(maximumBatchLengths, maximumBatchSize, this.deviceMaximumBatchLengths)

        this.pointerToMaximumBatchLengths = Pointer.to(this.deviceMaximumBatchLengths)

        allocateDeviceFloatMemory(this.deviceForwardResult, maximumBatchSize * this.numberResultEntries)

        this.backwardKernel = this.createBackwardKernel()

        allocateDeviceFloatMemory(this.deviceBackwardResult, maximumBatchSize * this.numberInputEntries)

        val backwardLaunch = computeBackwardLaunchConfiguration(this.numberInputEntries, this.maximumNumberWarpsPerBlock, this.warpSize)

        this.numberBackwardBlocksInYDimension = backwardLaunch.numberBlocks
        this.numberBackwardThreads = backwardLaunch.numberThreadsPerBlock


    }

    private fun computeBackwardLaunchConfiguration(numberInputEntries : Int, maximumNumberWarpsPerBlock : Int, warpSize: Int): KernelLaunchConfiguration {

        val numberBackwardBlocksInYDimension =
            if(numberInputEntries <= maximumNumberWarpsPerBlock)
                1
            else
                (numberInputEntries + maximumNumberWarpsPerBlock - 1) / maximumNumberWarpsPerBlock

        val numberWarpsPerBlock = if(numberInputEntries < maximumNumberWarpsPerBlock)
            numberInputEntries
        else
            maximumNumberWarpsPerBlock

        val numberThreads = numberWarpsPerBlock * warpSize

        return KernelLaunchConfiguration(numberBackwardBlocksInYDimension, numberThreads, 1)

    }

    override fun release() {

        cudaFree(this.deviceMaximumBatchLengths)

        this.forwardKernel!!.destroy()
        cudaFree(this.deviceForwardResult)

        this.backwardKernel!!.destroy()
        cudaFree(this.deviceBackwardResult)

    }

    override fun forward(batchSize: Int, deviceInput: Pointer, isTraining: Boolean): Pointer {

        this.batchSize[0] = batchSize

        this.forwardKernel!!.launch(
            Pointer.to(
                this.pointerToBatchSize,
                this.pointerToMaximumBatchLengths,
                this.pointerToNumberRows,
                this.pointerToNumberFilterRowPositions,
                this.pointerToNumberInputEntries,
                this.pointerToNumberResultEntries,
                this.pointerToFilterHeight,
                this.pointerToFilterWidth,
                this.pointerToFilterSize,
                Pointer.to(deviceInput),
                this.pointerToForwardResult
            ),
            this.maximumBatchSize,
            this.numberForwardBlocksInYDimension,
            this.numberForwardThreads,
            0
        )

        return this.deviceForwardResult

    }

    override fun forward(batchSize: Int, deviceLengths: Pointer, deviceInput: Pointer, isTraining: Boolean) : Pointer {

        this.batchSize[0] = batchSize

        this.forwardKernel!!.launch(
            Pointer.to(
                this.pointerToBatchSize,
                Pointer.to(deviceLengths),
                this.pointerToNumberRows,
                this.pointerToNumberFilterRowPositions,
                this.pointerToNumberInputEntries,
                this.pointerToNumberResultEntries,
                this.pointerToFilterHeight,
                this.pointerToFilterWidth,
                this.pointerToFilterSize,
                Pointer.to(deviceInput),
                this.pointerToForwardResult
            ),
            this.maximumBatchSize,
            this.numberForwardBlocksInYDimension,
            this.numberForwardThreads,
            0
        )

        return this.deviceForwardResult

    }

    override fun backward(batchSize: Int, chain: Pointer): Pointer {

        this.backwardKernel!!.launch(
            Pointer.to(
                this.pointerToBatchSize,
                this.pointerToMaximumBatchLengths,
                this.pointerToNumberRows,
                this.pointerToNumberInputEntries,
                this.pointerToNumberWarpsPerBlock,
                this.pointerToFilterHeight,
                this.pointerToFilterWidth,
                this.pointerToFilterSize,
                this.pointerToNumberConvolutions,
                this.pointerToNumberFilterRowPositions,
                Pointer.to(chain),
                this.pointerToBackwardResult
            ),
            this.maximumBatchSize,
            this.numberBackwardBlocksInYDimension,
            this.numberBackwardThreads,
            0
        )

        return this.deviceBackwardResult

    }


}