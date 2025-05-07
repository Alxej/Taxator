package com.example.taxator

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.taxator.ar.ArCompatibilityManager
import com.example.taxator.ar.SceneFormHelper
import com.example.taxator.permissions.PermissionUtils
import com.google.ar.sceneform.ux.ArFragment
import androidx.appcompat.app.AppCompatActivity
import com.example.taxator.ar.ArHelper
import com.example.taxator.camera.ImageHelper
import com.example.taxator.camera.TreeHighlighter
import com.google.ar.core.CameraConfig
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Vector3
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var arFragment: ArFragment
    private var arSession: Session? = null

    private lateinit var mlLabeler: ImageLabeler
    private val executor = Executors.newSingleThreadExecutor()
    private var lastFrame: Image? = null
    private var lastAnalysisTime = 0L
    private val analysisInterval = 500L
    private val frameProcessingLock = Any()

    private lateinit var treeHighlighter: TreeHighlighter

    private val defaultTreeSize = Vector3(1.0f, 2.0f, 0.5f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        if (!allInstrumentsAreAvailable()) {
            finish()
        }
        else{
            startArSession()
            initMl()
            treeHighlighter = TreeHighlighter(this, arFragment)
        }
    }

    fun allInstrumentsAreAvailable() : Boolean{

        if (!ArCompatibilityManager.checkArCoreCompatibility(this)){
            return false
        }

        if (!SceneFormHelper.isSupportedDevice(this)) {
            return false
        }

        if (!PermissionUtils.checkCameraPermission(this)){
            PermissionUtils.requestCameraPermission(this)
        }

        return true
    }

    private fun initMl(){
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.1f)
            .build()

        mlLabeler = ImageLabeling.getClient(options)
    }

    private fun startArSession() {
        try {
            // 1. Безопасное получение фрагмента
            arFragment = (supportFragmentManager.findFragmentById(R.id.arFragment) as? ArFragment
                ?: throw IllegalStateException("AR Fragment not found in layout"))

            arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
                if (arSession == null) {
                    val session = Session(this)

                    arSession = ArHelper.configureSession(
                        session = session,
                        focusMode = Config.FocusMode.AUTO,
                        fpsMode = CameraConfig.TargetFps.TARGET_FPS_30,
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    )

                    arFragment.arSceneView.setupSession(arSession!!)
                }
                onFrameUpdate(frameTime)
            }

        } catch (e: Exception) {
            Log.e("AR", "Failed to start AR session", e)
            Toast.makeText(this, "AR initialization failed", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun onFrameUpdate(frameTime: FrameTime){
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < analysisInterval) return

        val frame = arFragment.arSceneView.arFrame ?: return

        synchronized(frameProcessingLock) {
            try {
                lastFrame?.close()

                lastFrame = frame.tryAcquireCameraImage()?.also {
                    image -> processImageSafely(image, frame)
                }
            }
            catch (e: Exception){
                Log.e("AR", "Frame processing error", e)
            }
            finally {
                lastFrame?.close()
                lastAnalysisTime = currentTime
            }
        }

    }

    private fun processImageSafely(image: Image, frame: Frame){
        try{
            val bitmap = ImageHelper.convertAndScale(image, 1024, 768)

            CoroutineScope(Dispatchers.Default).launch {
                withContext(Dispatchers.Main) {
                    analyzeImageForTrees(bitmap){ treeRect ->
                        treeRect?.let { rect ->
                            val hits = frame.hitTest(
                                rect.centerX().toFloat(),
                                rect.centerY().toFloat()
                            )
                            hits.firstOrNull { hit ->
                                hit.trackable is Plane &&
                                        (hit.trackable as Plane).isPoseInPolygon(hit.hitPose)
                            }?.let{hit ->
                                runOnUiThread {
                                    treeHighlighter.highlightTree(
                                        Vector3(
                                            hit.hitPose.tx(),
                                            hit.hitPose.ty(),
                                            hit.hitPose.tz()
                                        ),
                                        calculateTreeSize(hit)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            // Закрываем изображение в любом случае
            image.close()
        }


    }

    private fun analyzeImageForTrees(bitmap: Bitmap, callback: (Rect?) -> Unit) {
        val treeKeywords = listOf("Tree", "Forest", "Wood", "Plant",
            "Palm", "Oak", "Pine", "Leaf", "Foliage", "Branch")
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        mlLabeler.process(inputImage)
            .addOnSuccessListener { labels ->
                val treeLabel = labels.firstOrNull { label ->
                    treeKeywords.contains(label.text)
                }
                Log.i("Trees", labels.toString())
                if (treeLabel != null){
                    Log.i("Trees", treeLabel.toString())
                    callback(Rect(0, 0, bitmap.width, bitmap.height))
                }

                else{
                    Log.i("Trees", "null")
                    callback(null)
                }

            }
            .addOnFailureListener { e ->
                Log.e("MLKit", "Ошибка анализа", e)
                callback(null)
            }
    }

    private fun calculateTreeSize(hit: HitResult): Vector3 {
        // Рассчитываем предполагаемый размер дерева на основе расстояния
        val distance = hit.distance
        val scaleFactor = distance / 2.0f // Эмпирический коэффициент

        return Vector3(
            defaultTreeSize.x * scaleFactor,
            defaultTreeSize.y * scaleFactor,
            defaultTreeSize.z * scaleFactor
        )
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionUtils.onRequestPermissionsResult(requestCode, grantResults,
            onPermissionGranted = {
                // Разрешение предоставлено, можно начинать работу с ARCore.
                startArSession()
            },
            onPermissionDenied = {
                // Разрешение отклонено, показываем сообщение пользователю.
                Toast.makeText(this, "Permission is required", Toast.LENGTH_LONG).show()
                finish() // Желательно закрыть приложение, если камера необходима.
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        mlLabeler.close()
        executor.shutdown()
        treeHighlighter.clearHighlights()
    }

    private fun Frame.tryAcquireCameraImage(): Image? {
        return try {
            acquireCameraImage()
        } catch (e: ResourceExhaustedException) {
            Log.w("AR", "Camera resources exhausted, skipping frame")
            null
        } catch (e: NotYetAvailableException) {
            Log.d("AR", "Frame not ready yet")
            null
        }
    }
}
