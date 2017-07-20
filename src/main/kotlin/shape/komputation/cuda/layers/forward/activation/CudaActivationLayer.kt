package shape.komputation.cuda.layers.forward.activation

import shape.komputation.cuda.layers.CudaForwardLayer
import shape.komputation.layers.CudaActivationLayerInstruction
import shape.komputation.layers.forward.activation.*

fun cudaActivationLayer(name: String?, function: ActivationFunction, dimension : Int) : CudaActivationLayerInstruction =

    when (function) {

        ActivationFunction.Identity ->
            identityLayer(name)
        ActivationFunction.Sigmoid ->
            sigmoidLayer(name, dimension)
        ActivationFunction.ReLU ->
            reluLayer(name, dimension)
        ActivationFunction.Softmax ->
            softmaxLayer(name, dimension)
        else ->
            throw NotImplementedError()

    }


interface CudaActivationLayer : CudaForwardLayer