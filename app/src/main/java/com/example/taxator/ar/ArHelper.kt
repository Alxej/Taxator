package com.example.taxator.ar

import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.util.EnumSet

object ArHelper {

    fun configureSession(session: Session,
                         focusMode: Config.FocusMode? = null,
                         fpsMode: CameraConfig.TargetFps? = null): Session{
        var newSession = session

        if (focusMode != null){
            newSession = applyFocusModeSettings(
                session = newSession,
                focusMode
            )
        }

        if(fpsMode != null){
            newSession = applyFPSMode(
                session = newSession,
                fpsMode
            )
        }

        return newSession

    }

    fun applyFocusModeSettings(session: Session,
                               mode: Config.FocusMode): Session{
        val config = Config(session)

        val isFocusModeSupported = session.isSupported(config.apply {
            focusMode = mode
        })

        if (!isFocusModeSupported){
            return session
        }

        config.setFocusMode(mode)

        return session
    }

    fun applyFPSMode(session: Session,
                     fpsMode: CameraConfig.TargetFps): Session{
        val filter = CameraConfigFilter(session).apply {
            setTargetFps(EnumSet.of(fpsMode))
        }

        val cameraConfigs = session.getSupportedCameraConfigs(filter)

        if (cameraConfigs.isNotEmpty()) {
            session.cameraConfig = cameraConfigs[0]
        }

        return session
    }

}