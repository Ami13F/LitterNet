
package com.kotlinapp.detection

import android.graphics.*
import android.media.ImageReader.OnImageAvailableListener
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import com.kotlinapp.R
import com.kotlinapp.design.OverlayView
import com.kotlinapp.utils.ImageUtils
import java.io.IOException
import java.util.*


class DetectorActivity : CameraActivity(),
    OnImageAvailableListener {
    var trackingOverlay: OverlayView? = null
    private var sensorOrientation: Int? = null
    private var detector: Classifier? = null
    private var lastProcessingTimeMs: Long = 0
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null
    private var computingDetection = false
    private var timestamp: Long = 0
    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null
    private var tracker: MultiBoxTracker? = null

    override fun onPreviewSizeChosen(size: Size?, rotation: Int) {

        tracker = MultiBoxTracker(this)
        var cropSize = INPUT_SIZE
        try {
            detector = ImageClassifier.create(
                assets,
                MODEL_FILENAME,
                LABELS_FILENAME,
                INPUT_SIZE,
                IS_QUANTIZED
            )
            cropSize = INPUT_SIZE
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(javaClass.name ,"Exception initializing classifier!", e)
            val toast = Toast.makeText(
                applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            finish()
        }
        previewWidth = size!!.width
        previewHeight = size.height
        sensorOrientation = rotation - screenOrientation
        Log.i(javaClass.name,
            "Camera orientation relative to screen canvas: %d $sensorOrientation"
        )
        Log.i(javaClass.name,
            "Initializing at size %dx%d $previewWidth $previewHeight"
        )
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation!!, IS_MAINTAIN_ASPECT
        )
        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)
        trackingOverlay = findViewById(R.id.tracking_overlay)
        trackingOverlay!!.addCallback(
            object : OverlayView.DrawCallback {
                override fun drawCallback(canvas: Canvas?) {
                    tracker!!.draw(canvas!!)
                    if (isDebug) {
                        tracker!!.drawDebug(canvas)
                    }
                }
            })
        tracker!!.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation!!)
    }

     override fun processImage() {
        ++timestamp
        val currTimestamp = timestamp
        trackingOverlay!!.postInvalidate()

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage()
            return
        }
        computingDetection = true
        Log.i(this.javaClass.name, "Preparing image $currTimestamp for detection in bg thread.")
        rgbFrameBitmap!!.setPixels(
            getRgbBytes(),
            0,
            previewWidth,
            0,
            0,
            previewWidth,
            previewHeight
        )
        readyForNextImage()
        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        // For examining the actual TF input.
//        if (SAVE_PREVIEW_BITMAP) {
//            ImageUtils.saveBitmap(croppedBitmap!!)
//        }
        runInBackground(
            Runnable {
                Log.i(javaClass.name, "Running detection on image $currTimestamp")
                val startTime = SystemClock.uptimeMillis()
                val results: List<Classifier.Recognition> =
                    detector!!.recognizeImage(croppedBitmap) as List<Classifier.Recognition>
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
                val canvas = Canvas(cropCopyBitmap!!)
                val paint = Paint()
                paint.color = Color.RED
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.0f
                var minimumConfidence = MIN_CONFIDENCE

                val mappedRecognitions: MutableList<Classifier.Recognition> =
                    LinkedList<Classifier.Recognition>()
                for (result in results) {
                    val location: RectF = result.getLocation()
                    if (result.confidence!! >= minimumConfidence) {
                        canvas.drawRect(location, paint)
                        cropToFrameTransform!!.mapRect(location)
                        result.setLocation(location)
                        mappedRecognitions.add(result)
                    }
                }
                tracker!!.trackResults(mappedRecognitions, currTimestamp)
                trackingOverlay!!.postInvalidate()
                computingDetection = false
                runOnUiThread(
                    Runnable { showInference(lastProcessingTimeMs.toString() + "ms") })
            })
    }


    override val layoutId: Int
         get() = R.layout.camera_tracking

    override fun onClick(v: View?) {}


    override fun setUseNNAPI(isChecked: Boolean) {
        runInBackground(Runnable { detector!!.setUseNNAPI(isChecked) })
    }

    protected fun setNumThreads(numThreads: Int) {
        runInBackground(Runnable { detector!!.setNumThreads(numThreads) })
    }

    override val desiredPreviewFrameSize: Size?
        get() = DESIRED_PREVIEW_SIZE

    companion object {

        private const val MODEL_FILENAME = "tiny-prn.tflite"
        private const val LABELS_FILENAME = "file:///android_asset/labels.txt"
        private const val IS_QUANTIZED = false
        private const val INPUT_SIZE = 416

        // Minimum detection confidence to recognize an object.
        private const val MIN_CONFIDENCE = 0.3f
        private const val IS_MAINTAIN_ASPECT = false
        protected val DESIRED_PREVIEW_SIZE = Size(640, 480)

    }
}