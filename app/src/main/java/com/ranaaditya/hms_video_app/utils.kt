package com.ranaaditya.hms_video_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.CameraEnumerator
import java.util.ArrayList

object utils {


    fun getDeviceList(context: Context?): List<CameraEnumerationAndroid.CaptureFormat>? {
        var formats: List<CameraEnumerationAndroid.CaptureFormat> = ArrayList()
        if (ContextCompat.checkSelfPermission(
                context!!,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val enumerator: CameraEnumerator
            enumerator = if (Camera2Enumerator.isSupported(context)) {
                Camera2Enumerator(context)
            } else {
                Camera1Enumerator(false)
            }
            val deviceInfos = enumerator.deviceNames
            for (i in deviceInfos.indices) {
                if (enumerator.isFrontFacing(deviceInfos[i])) {
                    formats = enumerator.getSupportedFormats(deviceInfos[i])
                    for (myformat in formats) {
                    }
                }
            }
        }
        return formats
    }

}