package com.kotlinapp.detection

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Trace
import android.util.Log
import com.kotlinapp.utils.TAG
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*



class ImageClassifier private constructor() : Classifier {
    private var isModelQuantized = false

    // Config values.
    private var inputSize = 0

    // Pre-allocated buffers.
    private val labels = Vector<String>()
    private lateinit var intValues: IntArray

    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private lateinit var outputLocations: Array<Array<FloatArray>>

    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private lateinit var outputClasses: Array<FloatArray>

    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private lateinit var outputScores: Array<FloatArray>

    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private lateinit var numDetections: IntArray
    private var imgData: ByteBuffer? = null
    private var tfLite: Interpreter? = null

    override fun recognizeImage(bitmap: Bitmap?): List<Classifier.Recognition>? {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage")
        Trace.beginSection("preprocessBitmap")
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap!!.getPixels(
            intValues,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )
        imgData!!.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    // Quantized model
                    imgData!!.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData!!.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData!!.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imgData!!.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        Trace.endSection() // preprocessBitmap

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed")
        outputLocations = Array(
            1
        ) { Array(NUM_DETECTIONS) { FloatArray(4) } }
        outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
        outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
        numDetections = IntArray(1)
        val inputArray = arrayOf<Any?>(imgData)
        val outputMap: MutableMap<Int, Any> =
            HashMap()
        outputMap[0] = outputLocations //[1,10,4]
        outputMap[1] = outputClasses // [1,1]
        outputMap[2] = outputScores // [1,1]
        outputMap[3] = numDetections // int[1]
        Trace.endSection()

        // Run the inference call.
        Trace.beginSection("run")
        tfLite!!.runForMultipleInputsOutputs(inputArray, outputMap)
        Trace.endSection()

        // Show the best detections.
        val numDetectionsOutput = Math.min(
            NUM_DETECTIONS,
            numDetections[0]
        )


        val recognitions: ArrayList<Classifier.Recognition> =
            ArrayList<Classifier.Recognition>(numDetectionsOutput)
        for (i in 0 until numDetectionsOutput) {
            val detection = RectF(
                outputLocations[0][i][1] * inputSize,
                outputLocations[0][i][0] * inputSize,
                outputLocations[0][i][3] * inputSize,
                outputLocations[0][i][2] * inputSize
            )
            recognitions.add(
                Classifier.Recognition(
                    "" + i,
                    labels[outputClasses[0][i].toInt()],
                    outputScores[0][i],
                    detection
                )
            )
        }
        Trace.endSection() // "recognizeImage"
        return recognitions
    }




    override fun setNumThreads(num_threads: Int) {
        tfLite!!.setNumThreads(num_threads)
    }

    override fun setUseNNAPI(isChecked: Boolean) {
        tfLite!!.setUseNNAPI(isChecked)
    }

    companion object {

        // Only return this many results.
        private const val NUM_DETECTIONS = 10

        // Float model
        private const val IMAGE_MEAN = 0f
        private const val IMAGE_STD = 255f

        // Number of threads in the java app
        private const val NUM_THREADS = 4

        /** Memory-map the model file in Assets.  */
        @Throws(IOException::class)
        private fun loadModelFile(
            assets: AssetManager,
            modelFilename: String
        ): MappedByteBuffer {
            val fileDescriptor = assets.openFd(modelFilename)
            val inputStream =
                FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
        }

        @Throws(IOException::class)
        fun create(
            assetManager: AssetManager,
            modelFilename: String,
            labelFilename: String,
            inputSize: Int,
            isQuantized: Boolean
        ): Classifier {
            val d = ImageClassifier()
            val actualFilename =
                labelFilename.split("file:///android_asset/").toTypedArray()[1]
            val labelsInput = assetManager.open(actualFilename)
            val br =
                BufferedReader(InputStreamReader(labelsInput))
            Log.d(TAG, "Loading labels into detector...")
            for (line in br.lines()) {
                Log.w(TAG, line)
                d.labels.add(line)
            }
            br.close()
            d.inputSize = inputSize
            try {
                val opts =
                    Interpreter.Options()
                opts.setNumThreads(NUM_THREADS)
                d.tfLite = Interpreter(
                    loadModelFile(
                        assetManager,
                        modelFilename
                    ), opts
                )
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            d.isModelQuantized = isQuantized
            // Pre-allocate buffers.
            val numBytesPerChannel: Int = if (isQuantized) {
                1 // Quantized
            } else {
                4 // Floating point
            }
            d.imgData =
                ByteBuffer.allocateDirect(d.inputSize * d.inputSize * 3 * numBytesPerChannel)
            d.imgData!!.order(ByteOrder.nativeOrder())
            d.intValues = IntArray(d.inputSize * d.inputSize)
            d.tfLite!!.setNumThreads(NUM_THREADS)
            d.outputLocations = Array(
                1
            ) { Array(NUM_DETECTIONS) { FloatArray(4) } }
            d.outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
            d.outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
            d.numDetections = IntArray(1)
            return d
        }
    }
}