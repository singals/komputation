package shape.komputation.layers.forward.convolution

import jcuda.jcublas.cublasHandle
import shape.komputation.cpu.functions.computeNumberFilterColumnPositions
import shape.komputation.cpu.functions.computeNumberFilterRowPositions
import shape.komputation.cpu.layers.forward.convolution.CpuConvolutionLayer
import shape.komputation.cuda.CudaContext
import shape.komputation.cuda.layers.CudaForwardLayer
import shape.komputation.cuda.layers.forward.convolution.CudaConvolutionLayer
import shape.komputation.initialization.InitializationStrategy
import shape.komputation.layers.CpuForwardLayerInstruction
import shape.komputation.layers.CudaForwardLayerInstruction
import shape.komputation.layers.concatenateNames
import shape.komputation.layers.forward.projection.projectionLayer
import shape.komputation.optimization.OptimizationInstruction

class ConvolutionLayer internal constructor(
    private val name : String?,
    private val numberInputRows : Int,
    private val numberInputColumns : Int,
    private val hasFixedLength: Boolean,
    private val numberFilters: Int,
    private val filterWidth: Int,
    private val filterHeight : Int,
    private val weightInitialization: InitializationStrategy,
    private val biasInitialization: InitializationStrategy,
    private val optimization: OptimizationInstruction? = null) : CpuForwardLayerInstruction, CudaForwardLayerInstruction {

    private val minimumInputColumns = if(this.hasFixedLength) this.numberInputColumns else this.filterWidth
    private val maximumInputColumns = this.numberInputColumns

    private val filterSize = this.filterWidth * this.filterHeight
    private val numberRowFilterPositions = computeNumberFilterRowPositions(this.numberInputRows, this.filterHeight)

    private val minimumNumberConvolutions = computeNumberFilterColumnPositions(this.minimumInputColumns, this.filterWidth) * this.numberRowFilterPositions
    private val maximumNumberConvolutions = computeNumberFilterColumnPositions(this.maximumInputColumns, this.filterWidth) * this.numberRowFilterPositions

    override fun buildForCpu(): CpuConvolutionLayer {

        val expansionLayer = expansionLayer(
            concatenateNames(this.name, "expansion"),
            this.numberInputRows,
            this.numberInputColumns,
            this.hasFixedLength,
            this.numberRowFilterPositions,
            this.filterWidth,
            this.filterHeight).buildForCpu()

        val projectionLayer = projectionLayer(
            concatenateNames(this.name, "projection"),
            this.filterSize,
            this.maximumNumberConvolutions,
            this.hasFixedLength,
            this.numberFilters,
            this.weightInitialization,
            this.biasInitialization,
            this.optimization).buildForCpu()

        val maxPoolingLayer = MaxPoolingLayer(
            concatenateNames(this.name, "max-pooling"),
            this.numberFilters,
            this.minimumNumberConvolutions,
            this.maximumNumberConvolutions,
            0f).buildForCpu()

        return CpuConvolutionLayer(this.name, expansionLayer, projectionLayer, maxPoolingLayer)

    }

    override fun buildForCuda(context: CudaContext, cublasHandle: cublasHandle): CudaForwardLayer {

        val expansionLayer = expansionLayer(
            concatenateNames(this.name, "expansion"),
            this.numberInputRows,
            this.numberInputColumns,
            this.hasFixedLength,
            this.numberRowFilterPositions,
            this.filterWidth,
            this.filterHeight).buildForCuda(context, cublasHandle)

        val projectionLayer = projectionLayer(
            concatenateNames(this.name, "projection"),
            this.filterSize,
            this.maximumNumberConvolutions,
            this.hasFixedLength,
            this.numberFilters,
            this.weightInitialization,
            this.biasInitialization,
            this.optimization).buildForCuda(context, cublasHandle)

        val maxPoolingLayer = MaxPoolingLayer(
            concatenateNames(this.name, "max-pooling"),
            this.numberFilters,
            this.minimumNumberConvolutions,
            this.maximumNumberConvolutions,
            0f).buildForCuda(context, cublasHandle)

        return CudaConvolutionLayer(this.name, expansionLayer, projectionLayer, maxPoolingLayer)

    }



}

fun convolutionalLayer(
    numberInputRows : Int,
    numberInputColumns : Int,
    hasFixedLength: Boolean,
    numberFilters: Int,
    filterWidth: Int,
    filterHeight : Int,
    weightInitialization: InitializationStrategy,
    biasInitialization: InitializationStrategy,
    optimization: OptimizationInstruction? = null) =

    convolutionalLayer(null, numberInputRows, numberInputColumns, hasFixedLength, numberFilters, filterWidth, filterHeight, weightInitialization, biasInitialization, optimization)

fun convolutionalLayer(
    name : String?,
    numberInputRows : Int,
    numberInputColumns : Int,
    hasFixedLength: Boolean,
    numberFilters: Int,
    filterWidth: Int,
    filterHeight : Int,
    weightInitialization: InitializationStrategy,
    biasInitialization: InitializationStrategy,
    optimization: OptimizationInstruction? = null) =

    ConvolutionLayer(name, numberInputRows, numberInputColumns, hasFixedLength, numberFilters, filterWidth, filterHeight, weightInitialization, biasInitialization, optimization)