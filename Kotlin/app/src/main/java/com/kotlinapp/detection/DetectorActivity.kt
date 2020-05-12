
package com.kotlinapp.detection

import android.graphics.*
import android.media.ImageReader.OnImageAvailableListener
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.widget.Toast
import com.kotlinapp.R
import com.kotlinapp.design.LabelsBorderText
import com.kotlinapp.design.OverlayView
import com.kotlinapp.utils.ImageUtils
import java.io.IOException
import java.util.*


class DetectorActivity :
    CameraActivity(),
    OnImageAvailableListener {

    // Configuration values for the prepackaged SSD model.
    private val inputSize = 416
    private val isQuantized = false
    private val modelFileName = "tiny-prn.tflite"
    private val labelsFileName = "file:///android_asset/labels.txt"

    private val MODE = DetectorMode.TF_OD_API

    private val minConfidence = 0.1f
    private val MAINTAIN_ASPECT = false
    private val previewInputSize = Size(640, 480)
    private val SAVE_PREVIEW_BITMAP = false
    private val TEXT_SIZE_DIP = 10f
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

    var tracker: MultiBoxTracker? = null

    private var borderedText: LabelsBorderText? = null

    override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        borderedText = LabelsBorderText(textSizePx)
        tracker = MultiBoxTracker(this)
        Log.d(javaClass.name, "Tracked... ${tracker!!.trackedObjects}")

        var cropSize = inputSize
        try {
            detector = ImageClassifier.create(
                assets,
                modelFileName,
                labelsFileName,
                inputSize,
                isQuantized
            )
            cropSize = inputSize
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
            sensorOrientation!!, MAINTAIN_ASPECT
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
        if (SAVE_PREVIEW_BITMAP) {
//            ImageUtils.saveBitmap(croppedBitmap)
        }
         runInBackground(
             Runnable {
                 Log.i(this.javaClass.name, "Running detection on image $currTimestamp")
                 val startTime = SystemClock.uptimeMillis()
                 val results: List<Classifier.Recognition?>? =
                     detector!!.recognizeImage(croppedBitmap)
                 lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                 cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
                 val canvas = Canvas(cropCopyBitmap!!)
                 val paint = Paint()
                 paint.color = Color.RED
                 paint.style = Paint.Style.STROKE
                 paint.strokeWidth = 2.0f
                 var minimumConfidence: Float =
                     minConfidence
                 when (MODE) {
                     DetectorMode.TF_OD_API -> minimumConfidence = minConfidence
                 }
                 val mappedRecognitions: MutableList<Classifier.Recognition?> =
                     LinkedList<Classifier.Recognition?>()
                 for (result in results!!) {
                     Log.d(javaClass.name, "Detected object:  $result")
                     val location: RectF = result!!.getLocation()
                     // detect nothing
                     if(location.left == 0f &&
                         location.right == 0f &&
                         location.top == 0f &&
                         location.bottom == 0f)
                         continue

                     if (result.confidence!! >= minimumConfidence) {
                         canvas.drawRect(location, paint)
                         cropToFrameTransform!!.mapRect(location)
                         result.setLocation(location)
                         mappedRecognitions.add(result)
                     }
                 }
                 tracker!!.trackResults(mappedRecognitions.toList(), currTimestamp)
                 trackingOverlay!!.postInvalidate()
                 computingDetection = false
                 runOnUiThread {
                     showInference(lastProcessingTimeMs.toString() + "ms")
                     setTracked(tracker)
                 }
             })
    }

    override fun getLayoutId(): Int {
        return R.layout.camera_tracking
    }

    override fun getDesiredPreviewFrameSize(): Size? {
        return previewInputSize
    }


    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum class DetectorMode {
        TF_OD_API
    }

    override fun setUseNNAPI(isChecked: Boolean) {
        runInBackground(Runnable { detector!!.setUseNNAPI(isChecked) })
    }

    override fun setNumThreads(numThreads: Int) {
        runInBackground(Runnable { detector!!.setNumThreads(numThreads) })
    }
}