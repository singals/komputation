#include "zero/Zero.cuh"

__device__ int findNextPowerOfTwo(int input) {

    int result = input -1;
    result |= result >> 1;
    result |= result >> 2;
    result |= result >> 4;
    result |= result >> 8;
    result |= result >> 16;

    result++;

    return result;

}

__device__ int findMaximum(int thisIndex, float thisValue, int nextPowerOfTwo) {

    for (int offset = nextPowerOfTwo / 2; offset > 0; offset /= 2) {

        int otherIndex = __shfl_down(thisIndex, offset, nextPowerOfTwo);
        float otherValue = __shfl_down(thisValue, offset, nextPowerOfTwo);

        if(otherValue > thisValue) {

            thisIndex = otherIndex;
            thisValue = otherValue;

        }

    }

    return thisIndex;

}

/*
    number of blocks in x-dimension = batch size
    number of blocks in y-dimension = number of rows
    number of threads = smallest power of two that is equal to or greater than the number of columns
*/
__global__ void maxPoolingKernel (
    int batchSize,
    int numberEntries,
    float* input,
    int* maxIndices,
    float* result) {

    int indexInstance = blockIdx.x;
    int indexRow = blockIdx.y;
    int indexColumn = threadIdx.x;
    int numberRows = gridDim.y;
    int numberColumns = blockDim.x;

    int resultStartInstance = indexInstance * numberRows;
    int resultIndex = resultStartInstance + indexRow;

    if(indexInstance < batchSize) {

        extern __shared__ int warpMaximumIndices[];

        int numberWarps = (numberColumns + warpSize - 1) / warpSize;
        int lastWarpId = numberWarps - 1;

        int warpId = indexColumn / warpSize;
        int laneId = indexColumn % warpSize;

        int inputStartInstance = indexInstance * numberEntries;
        int inputStartColumnWithinInstance = indexColumn * numberRows;

        int thisIndex = inputStartInstance + inputStartColumnWithinInstance + indexRow;
        float thisValue = input[thisIndex];

        int warpMaximumIndex = findMaximum(thisIndex, thisValue, warpId < lastWarpId ? warpSize : findNextPowerOfTwo(numberColumns - lastWarpId * warpSize));

        if(laneId == 0) {

            warpMaximumIndices[warpId] = warpMaximumIndex;

        }

        if (warpId == 0 && laneId < numberWarps) {

            int thisWarpMaximumIndex = warpMaximumIndices[laneId];
            int thisWarpMaximumValue = input[thisWarpMaximumIndex];

            int blockMaximumIndex = findMaximum(thisWarpMaximumIndex, thisWarpMaximumValue, findNextPowerOfTwo(numberWarps));

            if(laneId == 0) {

                maxIndices[resultIndex] = blockMaximumIndex;
                result[resultIndex] = input[blockMaximumIndex];

            }

        }

    }
    else {

        maxIndices[resultIndex] = 0.0;
        setToZero(result, resultStartInstance, resultStartInstance + 1);

    }

}