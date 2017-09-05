package shape.komputation.layers.forward.projection

import jcuda.jcublas.cublasHandle
import shape.komputation.cpu.layers.forward.projection.CpuBiasLayer
import shape.komputation.cpu.optimization.DenseAccumulator
import shape.komputation.cuda.CudaContext
import shape.komputation.cuda.kernels.ForwardKernels
import shape.komputation.cuda.layers.forward.projection.CublasBiasLayer
import shape.komputation.initialization.InitializationStrategy
import shape.komputation.initialization.initializeColumnVector
import shape.komputation.layers.CpuForwardLayerInstruction
import shape.komputation.layers.CudaForwardLayerInstruction
import shape.komputation.optimization.OptimizationInstruction

class BiasLayer internal constructor(
    private val name : String?,
    private val numberInputRows: Int,
    private val numberInputColumns: Int,
    private val hasFixedLength: Boolean,
    private val initializationStrategy: InitializationStrategy,
    private val optimizationStrategy : OptimizationInstruction? = null) : CpuForwardLayerInstruction, CudaForwardLayerInstruction {

    private val minimumInputColumns = if (this.hasFixedLength) this.numberInputColumns else 1
    private val maximumInputColumns = this.numberInputColumns

    override fun buildForCpu(): CpuBiasLayer {

        val bias = initializeColumnVector(this.initializationStrategy, this.numberInputRows)
        val accumulator = DenseAccumulator(bias.size)
        val updateRule = this.optimizationStrategy?.buildForCpu()?.invoke(this.numberInputRows, 1)

        val layer = CpuBiasLayer(this.name, this.numberInputRows, this.minimumInputColumns, this.maximumInputColumns, bias, accumulator, updateRule)

        return layer

    }

    override fun buildForCuda(context: CudaContext, cublasHandle: cublasHandle): CublasBiasLayer {

        val bias = initializeColumnVector(this.initializationStrategy, this.numberInputRows)
        val updateRule = this.optimizationStrategy?.buildForCuda(context)?.invoke(1, this.numberInputRows, 1)

        val layer = CublasBiasLayer(
            this.name,
            cublasHandle,
            this.numberInputRows,
            this.maximumInputColumns,
            bias,
            updateRule,
            { context.createKernel(ForwardKernels.bias()) },
            context.warpSize,
            context.maximumNumberOfThreadsPerBlock)

        return layer

    }

}

fun biasLayer(
    numberInputRows: Int,
    numberInputColumns : Int,
    hasFixedLength : Boolean,
    initializationStrategy: InitializationStrategy,
    optimizationStrategy : OptimizationInstruction? = null) =

    biasLayer(null, numberInputRows, numberInputColumns, hasFixedLength, initializationStrategy, optimizationStrategy)

fun biasLayer(
    name : String?,
    numberInputRows: Int,
    numberInputColumns : Int,
    hasFixedLength : Boolean,
    initializationStrategy: InitializationStrategy,
    optimizationStrategy : OptimizationInstruction? = null) =

    BiasLayer(name, numberInputRows, numberInputColumns, hasFixedLength, initializationStrategy, optimizationStrategy)