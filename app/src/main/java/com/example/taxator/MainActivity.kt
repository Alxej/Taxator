package com.example.taxator

import android.os.Bundle
import android.widget.Toast
import com.example.taxator.ar.ArCompatibilityManager
import com.example.taxator.ar.SceneFormHelper
import com.example.taxator.permissions.PermissionUtils
import com.google.ar.sceneform.ux.ArFragment
import androidx.appcompat.app.AppCompatActivity
import com.example.taxator.ar.ArHelper
import com.google.ar.core.CameraConfig
import com.google.ar.core.Config
import com.google.ar.core.Session

class MainActivity : AppCompatActivity() {
    private lateinit var arFragment: ArFragment
    private var arSession: Session? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        if (!allInstrumentsAreAvailable()) {
            finish()
        }
        else{
            startArSession()
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

    fun startArSession(){
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment

        var session = Session(this)

        arSession = ArHelper.configureSession(
            session = session,
            focusMode = Config.FocusMode.AUTO,
            fpsMode = CameraConfig.TargetFps.TARGET_FPS_30
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
}
