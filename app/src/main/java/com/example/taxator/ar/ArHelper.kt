package com.example.taxator.ar

import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.util.EnumSet

object ArHelper {

    fun configureSession(session: Session,
                         focusMode: Config.FocusMode? = null,
                         fpsMode: CameraConfig.TargetFps? = null,
                         updateMode: Config.UpdateMode? = null): Session{
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

        if(updateMode != null){
            newSession = applyUpdateMode(
                newSession,
                updateMode
            )
        }

        return newSession

    }

    fun applyUpdateMode(session: Session,
                        updMode: Config.UpdateMode) : Session{
        val config = Config(session).apply {
            updateMode = updMode
        }

        session.configure(config)

        return session
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