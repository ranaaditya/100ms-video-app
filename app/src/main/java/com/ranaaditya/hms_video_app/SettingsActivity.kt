package com.ranaaditya.hms_video_app


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.ranaaditya.hms_video_app.utils.getDeviceList
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid.CaptureFormat
import org.webrtc.CameraEnumerator
import java.util.*


class SettingsActivity : AppCompatActivity() {
    val TAG = "HMSSettingsActivity"
    var fromScreen: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (intent != null) {
            fromScreen = intent.getStringExtra("from")
        }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment(fromScreen!!))
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return super.dispatchTouchEvent(ev)
    }


    fun getDeviceList(context: Context?): List<CaptureFormat>? {
        var formats: List<CaptureFormat> = ArrayList()
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
                        Log.d(
                            TAG,
                            "Width: " + myformat.width + " Height: " + myformat.height + " FPS: " + myformat.framerate.max / 1000
                        )
                    }
                }
            }
            Log.d(TAG, "Device screen formats size: " + formats[0].width)
        }
        return formats
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment(var fromScreen: String) : PreferenceFragmentCompat() {
        var TAG = "SettingsActivity"
        var publishVideoSwitch: SwitchPreferenceCompat? = null
        var publishAudioSwitch: SwitchPreferenceCompat? = null
        var codecPreference: ListPreference? = null
        var resolutionPreference: ListPreference? = null
        var bitRatePreference: EditTextPreference? = null
        var deviceFormats: List<CaptureFormat>? = ArrayList()

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            publishAudioSwitch = findPreference("publish_audio") as SwitchPreferenceCompat?
            publishVideoSwitch = findPreference("publish_video") as SwitchPreferenceCompat?
            codecPreference = findPreference("codec") as ListPreference?
            resolutionPreference = findPreference("resolution") as ListPreference?
            frameRatePreference = findPreference("video_framerate") as EditTextPreference?
            bitRatePreference = findPreference("video_bitrate") as EditTextPreference?
            deviceFormats = getDeviceList(getContext())
            if (deviceFormats != null && deviceFormats!!.size > 0) Companion.setListPreferenceData(

                deviceFormats, resolutionPreference

            ) else setListPreferenceData(null, resolutionPreference)
            if (fromScreen == "launchscreen") {
                publishAudioSwitch!!.isVisible = true
                publishVideoSwitch!!.isVisible = true
                codecPreference!!.isVisible = false
                resolutionPreference!!.isVisible = true
                frameRatePreference!!.isVisible = false
                bitRatePreference!!.isVisible = true
            } else {
                //Toast.makeText(getContext(), "All settings config won't be visible during the call", Toast.LENGTH_LONG).show();
                publishAudioSwitch!!.isVisible = false
                publishVideoSwitch!!.isVisible = false
                codecPreference!!.isVisible = false
                resolutionPreference!!.isVisible = true
                frameRatePreference!!.isVisible = false
                bitRatePreference!!.isVisible = true
            }
        }

        companion object {
            var frameRatePreference: EditTextPreference? = null
            protected fun setListPreferenceData(
                formats: List<CaptureFormat>?,
                lp: ListPreference?
            ) {
                if (formats != null && formats.isNotEmpty() && lp != null) {
                    val resEntries: MutableList<String?> = ArrayList()
                    for (i in formats.indices) {
                        val width = formats[i].width
                        val height = formats[i].height
                        val frame = formats[i].framerate.max / 1000
                        resEntries.add(width.toString() + "x" + height + "@" + frame)
                    }
                    if (resEntries.size > 0) {
                        val entries = resEntries.toTypedArray<CharSequence?>()
                        val entryValues = resEntries.toTypedArray<CharSequence?>()
                        lp.entries = entries
                        lp.entryValues = entryValues
                    }
                }
                if (formats == null) {
                    val entries = arrayOf<CharSequence>(
                        "3840 x 2160",
                        "1920 x 1080",
                        "1280 x 720",
                        "640 x 480",
                        "320 x 240",
                        "160 x 120"
                    )
                    val entryValues = arrayOf<CharSequence>(
                        "3840 x 2160",
                        "1920 x 1080",
                        "1280 x 720",
                        "640 x 480",
                        "320 x 240",
                        "160 x 120"
                    )
                    lp!!.entries = entries
                    lp!!.setDefaultValue("640 x 480")
                    lp!!.entryValues = entryValues
                }
            }
        }
    }
}




