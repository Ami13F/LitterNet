package com.kotlinapp.detection.ui


import android.content.Context
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kotlinapp.R
import com.kotlinapp.utils.Permissions
import com.kotlinapp.utils.TAG
import com.kotlinapp.utils.ui.AutoFitTextureView
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.math.max
import kotlin.math.min


class CameraFragment private constructor(
    connectionCallback: ConnectionCallback,
    // Listener for frames
    imageListenerConst: OnImageAvailableListener,
    layoutConst: Int,
    inputSizeVal: Size
) : Fragment() {
    companion object {

        private const val MINIMUM_PREVIEW_SIZE = 416

        /** Conversion from screen rotation to JPEG orientation.  */
        private val ORIENTATION_MOOD = SparseIntArray()
        fun chooseOptimalSize(
            choices: Array<Size>,
            width: Int,
            height: Int
        ): Size {
            val minSize = max(
                min(width, height),
                MINIMUM_PREVIEW_SIZE
            )
            val desiredSize = Size(width, height)

            // Collect the supported resolutions that are at least as big as the preview Surface
            var exactSizeFound = false
            val bigEnough: MutableList<Size?> =
                ArrayList()
            val tooSmall: MutableList<Size?> =
                ArrayList()
            for (option in choices) {
                if (option == desiredSize) {
                    // Set the size but don't return yet so that remaining sizes will still be logged.
                    exactSizeFound = true
                }
                if (option.height >= minSize && option.width >= minSize) {
                    bigEnough.add(option)
                } else {
                    tooSmall.add(option)
                }
            }
            Log.i(TAG, "Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize)
            Log.i(TAG,
                "Valid preview sizes: [" + TextUtils.join(
                    ", ",
                    bigEnough
                ) + "]"
            )
            Log.i(TAG,
                "Rejected preview sizes: [" + TextUtils.join(
                    ", ",
                    tooSmall
                ) + "]"
            )
            if (exactSizeFound) {
                Log.i(TAG, "Exact size match found.")
                return desiredSize
            }

            // Pick the smallest of those, assuming we found any
            return if (bigEnough.size > 0) {
                val chosenSize = Collections.min(
                    bigEnough,
                    CompareSizesByArea()
                )!!
                Log.i(TAG, "Chosen size: " + chosenSize.width + "x" + chosenSize.height)
                chosenSize
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }

        init {
            ORIENTATION_MOOD.append(
                Surface.ROTATION_0,
                90
            )
            ORIENTATION_MOOD.append(
                Surface.ROTATION_90,
                0
            )
            ORIENTATION_MOOD.append(
                Surface.ROTATION_180,
                270
            )
            ORIENTATION_MOOD.append(
                Surface.ROTATION_270,
                180
            )
        }

        fun newInstance(callback: ConnectionCallback,
                        imageListener: OnImageAvailableListener,
                        layout: Int,
                        inputSize: Size?
        ): CameraFragment? {
            return CameraFragment(
                callback,
                imageListener,
                layout,
                inputSize!!
            )
        }

    }

    /** A [Semaphore] to prevent the app from exiting before closing the camera.  */
    private val cameraOpenCloseLock =
        Semaphore(1)

    private val cameraConnectionCallback: ConnectionCallback = connectionCallback
    private val imageListener : OnImageAvailableListener = imageListenerConst
    private val layout = layoutConst
    private val inputSize = inputSizeVal

    private val captureCallback: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
        }
    }


    private var cameraId: String? = null

    /** An [AutoFitTextureView] for camera preview.  */
    private var textureView: AutoFitTextureView? = null

    /** A [CameraCaptureSession] for camera preview.  */
    private var captureSession: CameraCaptureSession? = null

    /** A reference to the opened [CameraDevice].  */
    private var cameraDevice: CameraDevice? = null

    /** The rotation in degrees of the camera sensor from the display.  */
    private var sensorOrientation: Int? = null

    /** The [Size] of camera preview.  */
    private var previewSize: Size? = null

    /** An additional thread for running tasks that shouldn't block the UI.  */
    private var backgroundThread: HandlerThread? = null

    /** A [Handler] for running tasks in the background.  */
    private var backgroundHandler: Handler? = null

    /** An [ImageReader] that handles preview frame capture.  */
    private var previewReader: ImageReader? = null

    /** [CaptureRequest.Builder] for the camera preview  */
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    /** [CaptureRequest] generated by [.previewRequestBuilder]  */
    private var previewRequest: CaptureRequest? = null

    /** [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.  */
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cd: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release()
            cameraDevice = cd
            createCameraPreviewSession()
        }

        override fun onDisconnected(cd: CameraDevice) {
            cameraOpenCloseLock.release()
            cd.close()
            cameraDevice = null
        }

        override fun onError(cd: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cd.close()
            cameraDevice = null
            val activity = activity
            activity?.finish()
        }
    }

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a [ ].
     */
    private val surfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture, width: Int, height: Int
        ) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        textureView = view.findViewById<View>(R.id.texture) as AutoFitTextureView
    }


    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Resume...")
        startBackgroundThread()

        if (textureView!!.isAvailable) {
            Log.d(TAG, "Camera open...")
            openCamera(textureView!!.width, textureView!!.height)
        } else {
            textureView!!.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    /** Sets up member variables related to camera.  */
    private fun setUpCameraOutputs() {
        val manager =
            requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = manager.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

            previewSize =
                chooseOptimalSize(
                    map!!.getOutputSizes(SurfaceTexture::class.java),
                    inputSize.width,
                    inputSize.height
                )

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView!!.setAspectRatio(previewSize!!.width, previewSize!!.height)
            } else {
                textureView!!.setAspectRatio(previewSize!!.height, previewSize!!.width)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Exception!")
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
        }
        cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation!!)
    }

    private fun openCamera(width: Int, height: Int) {
        setUpCameraOutputs()
        configureTransform(width, height)
        val manager =
            requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
           Log.d(TAG,"Open camera with id: $cameraId")
            if (Permissions.checkPermission(this.context))
                manager.openCamera(cameraId!!, stateCallback, backgroundHandler!!)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Exception!")
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (null != captureSession) {
                captureSession!!.close()
                captureSession = null
            }
            if (null != cameraDevice) {
                cameraDevice!!.close()
                cameraDevice = null
            }
            if (null != previewReader) {
                previewReader!!.close()
                previewReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /** Starts a background thread and its [Handler].  */
    private fun startBackgroundThread() {
        Log.d(TAG,"Start background thread....")
        backgroundThread = HandlerThread("ImageListener")
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /** Stops the background thread and its [Handler].  */
    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Exception!")
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture: SurfaceTexture = textureView!!.surfaceTexture!!

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder!!.addTarget(surface)
            Log.i(TAG, "Opening camera preview: " + previewSize!!.width + "x" + previewSize!!.height)

            // Create the reader for the preview frames.
            previewReader = ImageReader.newInstance(
                previewSize!!.width, previewSize!!.height, ImageFormat.YUV_420_888, 2
            )
            previewReader!!.setOnImageAvailableListener(imageListener, backgroundHandler)
            previewRequestBuilder!!.addTarget(previewReader!!.surface)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice!!.createCaptureSession(
                listOf(surface, previewReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == cameraDevice) {
                            return
                        }

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            previewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // Flash is automatically enabled when necessary.
                            previewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder!!.build()
                            captureSession!!.setRepeatingRequest(
                                previewRequest!!, captureCallback, backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Exception!")
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Exception!")
        }
    }
    fun setCamera(cameraId: String?) {
        this.cameraId = cameraId
    }


    /**
     * Configures the necessary [Matrix] transformation to `mTextureView`. This method should be
     * called after the camera preview size is determined in setUpCameraOutputs and also the size of
     * `mTextureView` is fixed.
     *
     * @param viewWidth The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val activity = activity
        if (null == textureView || null == previewSize || null == activity) {
            return
        }
        //If emulator
        var rotation = activity.windowManager.defaultDisplay.rotation
//        if (Build.MODEL.contains("SDK")){
//            rotation = Surface.ROTATION_90
//        }
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0f, 0f,
            previewSize!!.height.toFloat(),
            previewSize!!.width.toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                viewHeight.toFloat() / previewSize!!.height,
                viewWidth.toFloat() / previewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView!!.setTransform(matrix)
    }

    interface ConnectionCallback {
        fun onPreviewSizeChosen(size: Size?, cameraRotation: Int)
    }

    /** Compares two `Size`s based on their areas.  */
    internal class CompareSizesByArea : Comparator<Size?> {
        override fun compare(o1: Size?, o2: Size?): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                o1!!.width.toLong() * o1.height - o2!!.width.toLong() * o2.height
            )
        }
    }

}