package shape.komputation.layers.forward.projection

import jcuda.jcublas.cublasHandle
import shape.komputation.cpu.layers.forward.projection.CpuWeightingLayer
import shape.komputation.cpu.optimization.DenseAccumulator
import shape.komputation.cuda.CudaContext
import shape.komputation.cuda.layers.forward.projection.CublasWeightingLayer
import shape.komputation.initialization.InitializationStrategy
import shape.komputation.initialization.initializeWeights
import shape.komputation.layers.CpuForwardLayerInstruction
import shape.komputation.layers.CudaForwardLayerInstruction
import shape.komputation.layers.concatenateNames
import shape.komputation.optimization.OptimizationInstruction

class WeightingLayer internal constructor(
    private val name : String?,
    private val numberInputRows: Int,
    private val numberInputColumns: Int,
    private val hasFixedLength: Boolean,
    private val numberOutputRows : Int,
    private val weightInitializationStrategy: InitializationStrategy,
    private val optimizationStrategy : OptimizationInstruction? = null) : CpuForwardLayerInstruction, CudaForwardLayerInstruction {

    private val minimumInputColumns = if (this.hasFixedLength) this.numberInputColumns else 1
    private val maximumInputColumns = this.numberInputColumns

    private val numberWeightRows = this.numberOutputRows
    private val numberWeightColumns = this.numberInputRows

    private val maximumNumberEntries = this.numberInputRows * this.maximumInputColumns

    override fun buildForCpu(): CpuWeightingLayer {

        val name = concatenateNames(name, "weighting")
        val initialWeights = initializeWeights(this.weightInitializationStrategy, this.numberWeightRows, this.numberWeightColumns, this.maximumNumberEntries)
        val accumulator = DenseAccumulator(this.numberWeightRows * this.numberWeightColumns)
        val updateRule = this.optimizationStrategy?.buildForCpu()?.invoke(this.numberWeightRows, this.numberWeightColumns)

        return CpuWeightingLayer(name, initialWeights, this.numberInputRows, this.minimumInputColumns, this.maximumInputColumns, this.numberWeightRows, accumulator, updateRule)

    }

    override fun buildForCuda(context: CudaContext, cublasHandle : cublasHandle): CublasWeightingLayer {

        val name = concatenateNames(this.name, "weighting")

        val initialWeights = initializeWeights(this.weightInitializationStrategy, this.numberWeightRows, this.numberWeightColumns, this.maximumNumberEntries)
        val updateRule = this.optimizationStrategy?.buildForCuda(context)?.invoke(1, this.numberWeightRows, this.numberWeightColumns)

        return CublasWeightingLayer(name, cublasHandle, this.numberInputRows, this.maximumInputColumns, this.numberOutputRows, initialWeights, updateRule)

    }

}

fun weightingLayer(
    inputDimension: Int,
    outputDimension: Int,
    initializationStrategy: InitializationStrategy,
    optimizationStrategy : OptimizationInstruction? = null) =

    weightingLayer(null, inputDimension, outputDimension, initializationStrategy, optimizationStrategy)

fun weightingLayer(
    name : String?,
    inputDimension: Int,
    outputDimension: Int,
    initializationStrategy: InitializationStrategy,
    optimizationStrategy : OptimizationInstruction? = null) =

    weightingLayer(
        name,
        inputDimension,
        1,
        true,
        outputDimension,
        initializationStrategy,
        optimizationStrategy)

fun weightingLayer(
    numberInputRows: Int,
    numberInputColumns : Int,
    hasFixedLength : Boolean,
    outputRows: Int,
    initializationStrategy: InitializationStrategy,
    optimizationStrategy : OptimizationInstruction? = null) =

    weightingLayer(
        null,
        numberInputRows,
        numberInputColumns,
        hasFixedLength,
        outputRows,
        initializationStrategy,
        optimizationStrategy)

fun weightingLayer(
    name : String?,
    numberInputRows: Int,
    numberInputColumns: Int,
    hasFixedLength: Boolean,
    outputRows: Int,
    initializationStrategy: InitializationStrategy,
    optimizationStrategy : OptimizationInstruction? = null) =

    WeightingLayer(
        name,
        numberInputRows,
        numberInputColumns,
        hasFixedLength,
        outputRows,
        initializationStrategy,
        optimizationStrategy)