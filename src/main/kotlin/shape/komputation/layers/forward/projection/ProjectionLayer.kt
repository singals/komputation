package shape.komputation.layers.forward.projection

import jcuda.jcublas.cublasHandle
import shape.komputation.cpu.layers.forward.projection.CpuBiasLayer
import shape.komputation.cpu.layers.forward.projection.CpuProjectionLayer
import shape.komputation.cpu.layers.forward.projection.CpuWeightingLayer
import shape.komputation.cpu.optimization.DenseAccumulator
import shape.komputation.cuda.CudaContext
import shape.komputation.cuda.kernels.ForwardKernels
import shape.komputation.cuda.layers.forward.projection.CublasBiasLayer
import shape.komputation.cuda.layers.forward.projection.CublasProjectionLayer
import shape.komputation.cuda.layers.forward.projection.CublasWeightingLayer
import shape.komputation.initialization.InitializationStrategy
import shape.komputation.initialization.initializeColumnVector
import shape.komputation.initialization.initializeWeights
import shape.komputation.layers.CpuForwardLayerInstruction
import shape.komputation.layers.CudaForwardLayerInstruction
import shape.komputation.layers.concatenateNames
import shape.komputation.optimization.OptimizationInstruction

class ProjectionLayer(
    private val name : String?,
    private val numberInputRows: Int,
    private val numberInputColumns: Int,
    private val hasFixedLength: Boolean,
    private val numberOutputRows : Int,
    private val weightInitializationStrategy: InitializationStrategy,
    private val biasInitializationStrategy: InitializationStrategy?,
    private val optimizationStrategy : OptimizationInstruction? = null) : CpuForwardLayerInstruction, CudaForwardLayerInstruction {

    private val minimumInputColumns = if (this.hasFixedLength) this.numberInputColumns else 1
    private val maximumInputColumns = this.numberInputColumns

    private val numberWeightRows = this.numberOutputRows
    private val numberWeightColumns = this.numberInputRows

    private val maximumNumberEntries = this.numberInputRows * this.maximumInputColumns

    override fun buildForCpu(): CpuProjectionLayer {

        val weightingName = concatenateNames(name, "weighting")
        val weights = initializeWeights(this.weightInitializationStrategy, this.numberWeightRows, this.numberWeightColumns, this.maximumNumberEntries)
        val weightAccumulator = DenseAccumulator(this.numberWeightRows * this.numberWeightColumns)
        val weightingUpdateRule = this.optimizationStrategy?.buildForCpu()?.invoke(this.numberWeightRows, this.numberWeightColumns)

        val weightingLayer = CpuWeightingLayer(weightingName, weights, this.numberInputRows, this.minimumInputColumns, this.maximumInputColumns, this.numberWeightRows, weightAccumulator, weightingUpdateRule)

        val biasLayer = if (this.biasInitializationStrategy != null) {

            val biasName = concatenateNames(name, "bias")

            val bias = initializeColumnVector(this.biasInitializationStrategy, this.numberOutputRows)
            val biasAccumulator = DenseAccumulator(bias.size)
            val biasUpdateRule = this.optimizationStrategy?.buildForCpu()?.invoke(bias.size, 1)

            CpuBiasLayer(biasName, this.numberWeightRows, this.minimumInputColumns, this.maximumInputColumns, bias, biasAccumulator, biasUpdateRule)

        }
        else {

            null

        }

        return CpuProjectionLayer(this.name, weightingLayer, biasLayer)

    }

    override fun buildForCuda(context: CudaContext, cublasHandle : cublasHandle): CublasProjectionLayer {

        val initialWeights = initializeWeights(this.weightInitializationStrategy, this.numberWeightRows, this.numberWeightColumns, this.maximumNumberEntries)
        val weightUpdateRule = this.optimizationStrategy?.buildForCuda(context)?.invoke(1, this.numberWeightRows, this.numberWeightColumns)

        val weightingName = concatenateNames(this.name, "weighting")

        val weightingLayer = CublasWeightingLayer(weightingName, cublasHandle, this.numberInputRows, this.maximumInputColumns, this.numberOutputRows, initialWeights, weightUpdateRule)

        val biasLayer : CublasBiasLayer?

        if (this.biasInitializationStrategy != null) {

            val biasName = concatenateNames(this.name, "bias")

            val initializedBias = initializeColumnVector(this.biasInitializationStrategy, this.numberOutputRows)
            val biasUpdateRule = this.optimizationStrategy?.buildForCuda(context)?.invoke(1, this.numberOutputRows, 1)

            biasLayer = CublasBiasLayer(
                biasName,
                cublasHandle,
                context.maximumNumberOfThreadsPerBlock,
                this.numberOutputRows,
                this.maximumInputColumns,
                { context.createKernel(ForwardKernels.bias()) },
                initializedBias,
                biasUpdateRule)

        }
        else {

            biasLayer = null

        }

        return CublasProjectionLayer(this.name, weightingLayer, biasLayer)

    }

}

// Vector weighting

fun projectionLayer(
    inputDimension: Int,
    outputDimension: Int,
    weightInitializationStrategy: InitializationStrategy,
    biasInitializationStrategy: InitializationStrategy? = null,
    optimizationStrategy : OptimizationInstruction? = null) =

    projectionLayer(null, inputDimension, outputDimension, weightInitializationStrategy, biasInitializationStrategy, optimizationStrategy)

fun projectionLayer(
    name : String?,
    inputDimension: Int,
    outputDimension: Int,
    weightInitializationStrategy: InitializationStrategy,
    biasInitializationStrategy: InitializationStrategy? = null,
    optimizationStrategy : OptimizationInstruction? = null) =

    projectionLayer(
        name,
        inputDimension,
        1,
        true,
        outputDimension,
        weightInitializationStrategy,
        biasInitializationStrategy,
        optimizationStrategy)

fun projectionLayer(
    numberInputRows: Int,
    numberInputColumns : Int,
    hasFixedLength : Boolean,
    outputRows: Int,
    weightInitializationStrategy: InitializationStrategy,
    biasInitializationStrategy: InitializationStrategy? = null,
    optimizationStrategy : OptimizationInstruction? = null) =

    projectionLayer(
        null,
        numberInputRows,
        numberInputColumns,
        hasFixedLength,
        outputRows,
        weightInitializationStrategy,
        biasInitializationStrategy,
        optimizationStrategy)


fun projectionLayer(
    name : String?,
    numberInputRows: Int,
    numberInputColumns: Int,
    hasFixedLength: Boolean,
    outputRows: Int,
    weightInitializationStrategy: InitializationStrategy,
    biasInitializationStrategy: InitializationStrategy? = null,
    optimizationStrategy : OptimizationInstruction? = null) =

    ProjectionLayer(
        name,
        numberInputRows,
        numberInputColumns,
        hasFixedLength,
        outputRows,
        weightInitializationStrategy,
        biasInitializationStrategy,
        optimizationStrategy)