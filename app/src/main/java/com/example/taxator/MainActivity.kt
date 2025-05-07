package com.example.taxator

import android.graphics.Bitmap
import android.graphics.Rect
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
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Vector3
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var arFragment: ArFragment
    private var arSession: Session? = null

    private lateinit var mlLabeler: ImageLabeler
    private val executor = Executors.newSingleThreadExecutor()
    private var lastAnalysisTime = 0L
    private val analysisInterval = 100L

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
            .setConfidenceThreshold(0.6f)
            .build()

        mlLabeler = ImageLabeling.getClient(options)
    }

    private fun startArSession(){
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment

        arFragment.arSceneView.scene.addOnUpdateListener(this::onFrameUpdate)

        var session = Session(this)

        arSession = ArHelper.configureSession(
            session = session,
            focusMode = Config.FocusMode.AUTO,
            fpsMode = CameraConfig.TargetFps.TARGET_FPS_30
        )
    }

    private fun onFrameUpdate(frameTime: FrameTime){
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < analysisInterval) return

        val frame = arFragment.arSceneView.arFrame ?: return
        val image = frame.acquireCameraImage()

        try {
            // Конвертация YUV в RGB
            val bitmap = ImageHelper.convertAndScale(image, 640, 480)
            analyzeImageForTrees(bitmap) { treeRect ->
                treeRect?.let { rect ->
                    val hits = frame.hitTest(rect.centerX().toFloat(), rect.centerY().toFloat())
                    hits.firstOrNull { hit ->
                        hit.trackable is Plane &&
                                (hit.trackable as Plane).isPoseInPolygon(hit.hitPose)
                    }?.let{hit ->
                        runOnUiThread {
                            treeHighlighter.highlightTree(
                                Vector3(hit.hitPose.tx(), hit.hitPose.ty(), hit.hitPose.tz()),
                                calculateTreeSize(hit)
                            )
                        }
                    }
                }
            }
        } finally {
            image.close()
            lastAnalysisTime = currentTime
        }
    }

    private fun analyzeImageForTrees(bitmap: Bitmap, callback: (Rect?) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        mlLabeler.process(inputImage)
            .addOnSuccessListener { labels ->
                val treeLabel = labels.firstOrNull { label ->
                    label.text.contains("tree", true)
                }

                if (treeLabel != null)
                    callback(Rect(0, 0, bitmap.width, bitmap.height))
                else
                    callback(null)
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
}
