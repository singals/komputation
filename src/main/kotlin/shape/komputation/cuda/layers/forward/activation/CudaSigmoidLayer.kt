package shape.komputation.cuda.layers.forward.activation

import shape.komputation.cuda.kernels.Kernel

class CudaSigmoidLayer internal constructor(
    name : String? = null,
    numberRows : Int,
    numberColumns : Int,
    createForwardKernel: () -> Kernel,
    createBackwardKernel: () -> Kernel,
    warpSize : Int,
    maximumNumberThreadsPerBlock : Int) : BaseCudaEntrywiseActivationLayer(name, createForwardKernel, createBackwardKernel, numberRows, numberColumns, warpSize, maximumNumberThreadsPerBlock)