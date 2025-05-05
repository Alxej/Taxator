package com.example.taxator.ar

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast

object SceneFormHelper {
    private const val TAG = "SceneformHelper"
    private const val MIN_OPENGL_VERSION = 3.0

    /**
     * Проверяет, поддерживается ли устройство Sceneform.  Если нет, завершает Activity.
     *
     * @param activity Activity, которую нужно проверить.
     * @return `true`, если устройство поддерживается, `false` в противном случае.
     */

    fun isSupportedDevice(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later")
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show()
            return false
        }

        val openGlVersionString =
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion

        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 or later")
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
            return false
        }

        return true
    }
}