package com.kotlinapp

import android.content.res.AssetManager
import android.graphics.*
import android.util.Log
import android.util.Size
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.kotlinapp.detection.Classifier
import com.kotlinapp.detection.Classifier.Recognition
import com.kotlinapp.detection.ImageClassifier
import com.kotlinapp.utils.ImageUtils.getTransformationMatrix
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import java.io.IOException
import java.util.*
import kotlin.math.abs


/** Golden test for Object Detection Reference app.  */
@RunWith(AndroidJUnit4::class)
class DetectorTest {
    private var detector: Classifier? = null
    private var croppedBitmap: Bitmap? = null
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val assetManager: AssetManager =
            InstrumentationRegistry.getInstrumentation().context.assets
        detector = ImageClassifier.create(
            assetManager,
            MODEL_FILE,
            LABELS_FILE,
            MODEL_INPUT_SIZE,
            IS_MODEL_QUANTIZED
        )
        val cropSize = MODEL_INPUT_SIZE
        val previewWidth = IMAGE_SIZE.width
        val previewHeight = IMAGE_SIZE.height
        val sensorOrientation = 0
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
        frameToCropTransform = getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, false
        )
        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)
    }

    @Test
    @Throws(Exception::class)
    fun testDetectionResultsShouldNotChange() {
        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(
            loadImage("bottle2.jpeg"),
            frameToCropTransform!!,
            null
        )
        val results = detector!!.recognizeImage(croppedBitmap)
        val expected =
            loadRecognitions("table_results.txt")
        for (target in results!!) {
            // Find a matching result in results
            var matched = false
            Log.d(
                this.javaClass.name,
                "Location " + target!!.getLocation()
            )
            for (item in results) {
                val bbox = RectF()
                cropToFrameTransform!!.mapRect(bbox, item!!.getLocation())
                if (item.title == target.title && matchBoundingBoxes(
                        bbox,
                        target.getLocation()
                    )
                    && matchConfidence(
                        item.confidence!!,
                        target.confidence!!
                    )
                ) {
                    matched = true
                    break
                }
            }
        }
    }

    companion object {
        private const val MODEL_INPUT_SIZE = 416
        private const val IS_MODEL_QUANTIZED = false
        private const val MODEL_FILE = "tiny-prn-t.tflite"
        private const val LABELS_FILE = "file:///android_asset/taco.txt"
        private val IMAGE_SIZE = Size(1200, 1600)

        // Confidence tolerance: absolute 1%
        private fun matchConfidence(a: Float, b: Float): Boolean {
            return abs(a - b) < 0.01
        }

        // Bounding Box tolerance: overlapped area > 95% of each one
        private fun matchBoundingBoxes(a: RectF, b: RectF): Boolean {
            val areaA = a.width() * a.height()
            val areaB = b.width() * b.height()
            val overlapped = RectF(
                Math.max(a.left, b.left),
                Math.max(a.top, b.top),
                Math.min(a.right, b.right),
                Math.min(a.bottom, b.bottom)
            )
            val overlappedArea = overlapped.width() * overlapped.height()
            return overlappedArea > 0.95 * areaA && overlappedArea > 0.95 * areaB
        }

        @Throws(Exception::class)
        private fun loadImage(fileName: String): Bitmap {
            val assetManager: AssetManager =
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
            val inputStream = assetManager.open(fileName)
            return BitmapFactory.decodeStream(inputStream)
        }

        // The format of result:
        // category bbox.left bbox.top bbox.right bbox.bottom confidence
        // ...
        // Example:
        // Apple 99 25 30 75 80 0.99
        // Banana 25 90 75 200 0.98
        // ...
        @Throws(Exception::class)
        private fun loadRecognitions(fileName: String): List<Recognition> {
            val assetManager: AssetManager =
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
            val inputStream = assetManager.open(fileName)
            val scanner = Scanner(inputStream)
            val result: MutableList<Recognition> =
                ArrayList()
            while (scanner.hasNext()) {
                var category = scanner.next()
                category = category.replace('_', ' ')
                if (!scanner.hasNextFloat()) {
                    break
                }
                val left = scanner.nextFloat()
                val top = scanner.nextFloat()
                val right = scanner.nextFloat()
                val bottom = scanner.nextFloat()
                val boundingBox = RectF(left, top, right, bottom)
                val confidence = scanner.nextFloat()
                val recognition = Recognition(null, category, confidence, boundingBox)
                result.add(recognition)
            }
            return result
        }
    }
}
