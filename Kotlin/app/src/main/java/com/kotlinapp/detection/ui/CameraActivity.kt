package com.kotlinapp.detection.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image.Plane
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Trace
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.kotlinapp.MainActivity
import com.kotlinapp.R
import com.kotlinapp.detection.MultiBoxTracker
import com.kotlinapp.utils.ImageUtils
import kotlinx.android.synthetic.main.camera_fragment.*

abstract class CameraActivity : AppCompatActivity(),
    OnImageAvailableListener{
    private var classScores: Map<String, Int> = mapOf( "Bottle" to 35, "Bottle cap" to 10,
            "Can" to 15, "Carton" to 7, "Cup" to 15, "Other" to 13, "Paper" to 24,
            "Plastic bag + wrapper" to 43, "Straw" to 21, "Styrofoam piece" to 11)

    protected var previewWidth = 0
    protected var previewHeight = 0
    val isDebug = false
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0

    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var bottomSheetLayout: LinearLayout? = null
    private var gestureLayout: LinearLayout? = null
    private var sheetBehavior: BottomSheetBehavior<LinearLayout>? = null
    private var inferenceTimeTextView: TextView? = null
    private var objectsDetected: MultiBoxTracker? = null

    protected var bottomSheetArrowImageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(javaClass.name,"onCreate $this")
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.camera_fragment)

        supportActionBar!!.hide()

        setFragment()

        captureBtn.setOnClickListener {
            onDetectionPressed()
        }
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout)
        gestureLayout = findViewById(R.id.gesture_layout)
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout!!)
        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow)
        val vto = gestureLayout!!.viewTreeObserver
        vto.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    gestureLayout!!.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val height = gestureLayout!!.measuredHeight
                    sheetBehavior!!.peekHeight = height
                }
            })
        sheetBehavior!!.isHideable = false
        // Style bottom view
        val bt = object : BottomSheetCallback() {
            override fun onStateChanged(
                bottomSheet: View,
                newState: Int
            ) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN, BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        bottomSheetArrowImageView!!.setImageResource(R.drawable.arrow)
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        bottomSheetArrowImageView!!.setImageResource(R.drawable.arrow)
                    }
                    BottomSheetBehavior.STATE_SETTLING -> bottomSheetArrowImageView!!.setImageResource(
                        R.drawable.arrow
                    )
                }
            }

            override fun onSlide(
                bottomSheet: View,
                slideOffset: Float
            ) {
            }
        }
        sheetBehavior!!.bottomSheetCallback = bt

        inferenceTimeTextView = findViewById(R.id.inference_info)
    }

    private fun onDetectionPressed(){
        val t = Toast.makeText(this, "Analyzing detection...", Toast.LENGTH_SHORT)
        t.show()

        Log.d(javaClass.name,"Objects.... $objectsDetected")
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)

//        If something is detected...
        if(objectsDetected != null && objectsDetected!!.trackedObjects.isNotEmpty()){
            Log.d(javaClass.name,"Objects.... ${objectsDetected!!.trackedObjects}")

            val types = arrayOf<CharSequence>(
                "Save score", "Try again"
            )
            var score = 0
            for ( result in objectsDetected!!.trackedObjects.toList()){
                Log.d(javaClass.name, "${result.title}")
                Log.d(javaClass.name, "${classScores[result.title]}")
                score += classScores[result.title]!!
            }
            val title = "Congratulations your score is: $score"
            builder.setTitle(title)
            builder.setItems(types) { dialog, item ->
                if (types[item] == "Save score") {
                    runOnUiThread{
                        val startIntent = Intent(baseContext, MainActivity::class.java)
                        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startIntent.putExtra("Score", score)
                        setResult(Activity.RESULT_OK, startIntent)
                        baseContext.startActivity(startIntent)

                        onBackPressed()
                    }
                } else if (types[item] == "Cancel") {
                    dialog.dismiss()
                }
            }
            builder.show()
        }else{
            val title = "There is no garbage, please try again"
            val types = arrayOf<CharSequence>(
                "Try again"
            )
            builder.setTitle(title)
            builder.setItems(types) { dialog, item ->
                if (types[item] == "Try again") {
                    dialog.dismiss()
                }
            }
            builder.show()
        }
    }

    protected fun getRgbBytes(): IntArray? {
        imageConverter!!.run()
        return rgbBytes
    }


    /** Callback for Camera2 API  */
    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            Trace.beginSection("imageAvailable")
            val planes = image.planes
            fillBytes(planes, yuvBytes)

            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }

            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
        } catch (e: Exception) {
            Log.e(this.javaClass.name, "Exception!", e)
            Trace.endSection()
            return
        }
        Trace.endSection()
    }

    @Synchronized
    public override fun onStart() {
        Log.d(this.javaClass.name,"onStart $this")
        super.onStart()
    }

    @Synchronized
    public override fun onResume() {
        Log.d(this.javaClass.name, "onResume $this")
        super.onResume()
        handlerThread = HandlerThread("inference")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized
    public override fun onPause() {
        Log.d(this.javaClass.name, "onPause $this")
        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            Log.e(this.javaClass.name, "Exception!", e)
        }
        super.onPause()
    }

    @Synchronized
    public override fun onStop() {
        Log.d(this.javaClass.name, "onStop $this")
        super.onStop()
    }

    @Synchronized
    public override fun onDestroy() {
        Log.d(this.javaClass.name, "onDestroy $this")
        super.onDestroy()
    }

    @Synchronized
    protected fun runInBackground(r: Runnable?) {
        if (handler != null) {
            handler!!.post(r!!)
        }
    }

    private fun chooseCamera(): String? {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics =
                    manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                return cameraId
            }
        } catch (e: CameraAccessException) {
            Log.e(this.javaClass.name, "Not allowed to access camera", e)
        }
        return null
    }

    override fun onBackPressed() {

        super.onBackPressed()
        super.onBackPressed()
        Log.d(this.javaClass.name,"Classs  ${this.localClassName}")
    }


    protected open fun setFragment() {
        val cameraId = chooseCamera()
        val fragment : Fragment
            val callback = object:
                CameraFragment.ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int) {
                    previewHeight = size!!.height
                    previewWidth = size.width
                    this@CameraActivity.onPreviewSizeChosen(size, cameraRotation)
                }
            }
            val camera2Fragment: CameraFragment? =
                CameraFragment.newInstance(
                    callback,
                    this,
                    getLayoutId(),
                    getDesiredPreviewFrameSize()
                )
            camera2Fragment!!.setCamera(cameraId)
            fragment = camera2Fragment
        supportFragmentManager.beginTransaction().replace(R.id.container,fragment).addToBackStack("name").commit()
    }

    private fun fillBytes(
        planes: Array<Plane>,
        yuvBytes: Array<ByteArray?>
    ) {

        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                Log.d(this.javaClass.name,
                    "Initializing buffer %d at size %d"+
                     i + buffer.capacity()
                )
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]!!]
        }
    }

    protected fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

    protected val screenOrientation: Int
        get() = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    protected fun showInference(inferenceTime: String?) {
        inferenceTimeTextView!!.text = inferenceTime
    }
    protected fun setTracked(tracked: MultiBoxTracker?) {
        objectsDetected = tracked
    }
    protected abstract fun processImage()
    protected abstract fun onPreviewSizeChosen(size: Size?, rotation: Int)
    protected abstract fun getLayoutId(): Int


    protected abstract fun getDesiredPreviewFrameSize(): Size?

}