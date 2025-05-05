package com.example.taxator.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

object ArCompatibilityManager {

    fun checkArCoreCompatibility(context: Context): Boolean{
        return when (ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                true
            }

            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                try {
                    when (ArCoreApk.getInstance().requestInstall(context as Activity?, true)) {
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                            // ARCore установка запрошена.  Приложение должно дождаться результата установки
                            // и повторить вызов `checkARCoreCompatibility` позже.
                            false
                        }
                        ArCoreApk.InstallStatus.INSTALLED -> {
                            true
                        }

                        else -> false
                    }
                } catch (ex: UnavailableUserDeclinedInstallationException) {
                    false
                } catch (ex: UnavailableApkTooOldException){
                    false
                } catch (ex: UnavailableDeviceNotCompatibleException){
                    false
                } catch (ex: UnavailableSdkTooOldException){
                    false
                }
            }

            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                false
            }

            ArCoreApk.Availability.UNKNOWN_CHECKING -> {
                false
            }

            ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> {
                false
            }

            else -> false
        }
    }
}