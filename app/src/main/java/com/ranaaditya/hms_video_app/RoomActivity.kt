package com.ranaaditya.hms_video_app

import android.Manifest
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.brytecam.lib.*
import com.brytecam.lib.payload.HMSPayloadData
import com.brytecam.lib.payload.HMSPublishStream
import com.brytecam.lib.payload.HMSStreamInfo
import com.brytecam.lib.webrtc.HMSRTCMediaStream
import com.brytecam.lib.webrtc.HMSRTCMediaStreamConstraints
import com.brytecam.lib.webrtc.HMSStream
import com.brytecam.lib.webrtc.HMSWebRTCEglUtils
import com.google.android.material.snackbar.Snackbar

import kotlinx.android.synthetic.main.activity_room.*

import org.appspot.apprtc.AppRTCAudioManager
import org.webrtc.*

import pub.devrel.easypermissions.EasyPermissions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RoomActivity : AppCompatActivity(), HMSEventListener {

    val TAG: String = "DEBUG ROOMACTIVITY"

    lateinit var serverName: String
    lateinit var roomName: String
    lateinit var userName: String
    lateinit var authToken: String
    lateinit var bitRate: String
    lateinit var envrn: String

    lateinit var appAudioManager: AppRTCAudioManager
    lateinit var handler: Handler
    lateinit var userSharedPreferences: SharedPreferences
    lateinit var userSharedPrefListener: SharedPreferences.OnSharedPreferenceChangeListener
    lateinit var peer: HMSPeer
    lateinit var userRoom: HMSRoom
    lateinit var configurations: HMSClientConfig
    lateinit var hmsClient: HMSClient
    lateinit var userMediaConstraints: HMSRTCMediaStreamConstraints

    var userMediaStream: HMSRTCMediaStream? = null
    var userVideoTrack: VideoTrack? = null
    var userAudioTrack: AudioTrack? = null
    var userSurfaceViewRenderer: SurfaceViewRenderer? = null
    var serviceExecutor: ExecutorService = Executors.newSingleThreadExecutor()


    var isCameraToggled: Boolean = false
    var isAudioEnabled: Boolean = true
    var isVideoEnabled: Boolean = true
    var isFrontCameraEnabled: Boolean = true
    var isJoined: Boolean = false
    var isPublished: Boolean = false
    var shouldReconnect: Boolean = false

    var cell = 0
    val VC: Int = 111
    var delay: Int = 0
    var retryCount: Int = 0
    var maxRetryCount: Int = 40


    private var DEFAULT_PUBLISH_VIDEO = true
    private var DEFAULT_PUBLISH_AUDIO = true
    private var DEFAULT_VIDEO_RESOLUTION = "640 x 480"
    private var DEFAULT_VIDEO_BITRATE = "256"
    private var DEFAULT_VIDEO_FRAMERATE = "30"
    private var DEFAULT_CODEC = "VP8"
    private val FRONT_FACING_CAMERA = "user"
    private val REAR_FACING_CAMERA = "environment"

    private var TOTAL_REMOTE_PEERS = 7
    private var remoteSurfaceViewRenderers = arrayOfNulls<SurfaceViewRenderer>(TOTAL_REMOTE_PEERS)
    private var remoteTextViews = arrayOfNulls<TextView>(TOTAL_REMOTE_PEERS)
    private var remoteVideoTracks = arrayOfNulls<VideoTrack>(TOTAL_REMOTE_PEERS)
    private var remoteAudioTracks = arrayOfNulls<AudioTrack>(TOTAL_REMOTE_PEERS)
    private var remoteUserIds = arrayOfNulls<String>(TOTAL_REMOTE_PEERS)
    private var remotePeers = arrayOfNulls<HMSPeer>(TOTAL_REMOTE_PEERS)
    private var isRemoteCellFree = arrayOfNulls<Boolean>(TOTAL_REMOTE_PEERS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        } else {
            val window = window
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }

        serverName = intent.getStringExtra("server").toString()
        roomName = intent.getStringExtra("room").toString()
        userName = intent.getStringExtra("user").toString()
        authToken = intent.getStringExtra("auth_token").toString()
        bitRate = intent.getStringExtra("bitrate").toString()
        envrn = intent.getStringExtra("env").toString()

        appAudioManager = AppRTCAudioManager.create(applicationContext)

        appAudioManager.start(object : AppRTCAudioManager.AudioManagerEvents {

            // this gonna called each time the number of available audio devices has changed
            override fun onAudioDeviceChanged(
                selectedAudioDevice: AppRTCAudioManager.AudioDevice?,
                availableAudioDevices: MutableSet<AppRTCAudioManager.AudioDevice>?
            ) {

            }
        })

        startRoomProcess()

    }

    fun startRoomProcess() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        if (EasyPermissions.hasPermissions(this, *perms)) {
            setUserPreferences()
            initializeHMSClient()
            initializeSurfaceViews()
            initializeToggleMenu()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "Need User permissions to proceed", VC,
                *perms
            )

        }
    }

    private fun setUserPreferences() {
        handler = Handler(Looper.getMainLooper())

        // load our shared preferences
        userSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        DEFAULT_PUBLISH_VIDEO = userSharedPreferences.getBoolean("publish_video", true)
        DEFAULT_PUBLISH_AUDIO = userSharedPreferences.getBoolean("publish_audio", true)

        DEFAULT_VIDEO_RESOLUTION =
            userSharedPreferences.getString("resolution", "640 x 480").toString()
        DEFAULT_CODEC = userSharedPreferences.getString("codec", "VP8").toString()

        DEFAULT_VIDEO_BITRATE = userSharedPreferences.getString("video_bitrate", "256").toString()
        DEFAULT_VIDEO_FRAMERATE =
            userSharedPreferences.getString("video_framerate", "30").toString()

        isAudioEnabled = DEFAULT_PUBLISH_AUDIO


        userSharedPrefListener = OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "publish_video" && userVideoTrack != null) {
                Log.d(
                    TAG,
                    "Boolean video changes: " + userSharedPreferences.getBoolean(key, true)
                )
                DEFAULT_PUBLISH_VIDEO = userSharedPreferences.getBoolean(key, true)
                if (DEFAULT_PUBLISH_VIDEO) {
                    userVideoTrack!!.setEnabled(true)
                    userVideoTrack!!.addSink(userSurfaceViewRenderer)
                } else {
                    userVideoTrack!!.setEnabled(false)
                    userVideoTrack!!.removeSink(userSurfaceViewRenderer)
                }
            }
            if (key == "publish_audio" && userAudioTrack != null) {
                DEFAULT_PUBLISH_AUDIO = userSharedPreferences.getBoolean(key, true)
                Log.d(
                    TAG,
                    "Boolean audio changes: " + userSharedPreferences.getBoolean(key, true)
                )
                if (DEFAULT_PUBLISH_AUDIO) userAudioTrack!!.setEnabled(true) else userAudioTrack!!.setEnabled(
                    false
                )
            }
            if (key == "resolution") {
                DEFAULT_VIDEO_RESOLUTION = userSharedPreferences.getString(key, "VGA (640 x 480)").toString()

                Log.d(TAG, "Resolution changes: $DEFAULT_VIDEO_RESOLUTION")

                userMediaConstraints.videoResolution = DEFAULT_VIDEO_RESOLUTION
                hmsClient.applyResolution(userMediaConstraints)

            }
            if (key == "codec") {
                DEFAULT_CODEC = userSharedPreferences.getString(key, "VP8").toString()

                Log.d(TAG, "Codec changes: $DEFAULT_CODEC")

            }
            if (key == "video_bitrate") {
                DEFAULT_VIDEO_BITRATE = userSharedPreferences.getString(key, "512").toString()

                Log.d(TAG, "Bitrate changes: $DEFAULT_VIDEO_BITRATE")

                if (hmsClient != null) hmsClient.setBitrate(
                    Integer.valueOf(
                        DEFAULT_VIDEO_BITRATE
                    ) * 1000
                )
            }
            if (key == "video_framerate") {
                DEFAULT_VIDEO_FRAMERATE = userSharedPreferences.getString(key, "30").toString()
                Log.d(TAG, "Framerate changes: $DEFAULT_VIDEO_FRAMERATE")
            }
        }
        userSharedPreferences.registerOnSharedPreferenceChangeListener(userSharedPrefListener)
    }


    private fun initializeHMSClient() {

        //Create a 100ms peer
        peer = HMSPeer(userName, authToken)

        //Create a room
        userRoom = HMSRoom(roomName)

        //Create client configuration
        configurations = HMSClientConfig(serverName)

        //Create a 100ms client
        hmsClient = HMSClient(this, applicationContext, peer, configurations)
        hmsClient.setLogLevel(HMSLogger.LogLevel.LOG_DEBUG)
        hmsClient.connect()
    }

    private fun initializeSurfaceViews() {
        if (HMSWebRTCEglUtils.getRootEglBaseContext() == null) HMSWebRTCEglUtils.getRootEglBase()

        //A small below actionbar which appears when there is no connection

        reconnect_progressview.setVisibility(View.GONE)
        //localPeerTextView = findViewById<View>(R.id.firstpeer_textview) as TextView
        remoteTextViews[0] = findViewById<View>(R.id.secondpeer_textview) as TextView
        remoteTextViews[1] = findViewById<View>(R.id.thirdpeer_textview) as TextView
        remoteTextViews[2] = findViewById<View>(R.id.fourthpeer_textview) as TextView
        remoteTextViews[3] = findViewById<View>(R.id.fifthpeer_textview) as TextView
        remoteTextViews[4] = findViewById<View>(R.id.sixthpeer_textview) as TextView
        remoteTextViews[5] = findViewById<View>(R.id.seventhpeer_textview) as TextView
        remoteTextViews[6] = findViewById<View>(R.id.eighthpeer_textview) as TextView


        //Init all the remote views are free
        for (i in 0 until TOTAL_REMOTE_PEERS) {
            isRemoteCellFree[i] = true
        }
        runOnUiThread {
            try {

                //Setting local peer name
                firstpeer_textview.text = userName

                //Init view
                //userSurfaceViewRenderer = findViewById<View>(R.id.surface_view1) as SurfaceViewRenderer
                remoteSurfaceViewRenderers[0] =
                    findViewById<View>(R.id.remote_view2) as SurfaceViewRenderer
                remoteSurfaceViewRenderers[1] =
                    findViewById<View>(R.id.remote_view3) as SurfaceViewRenderer
                remoteSurfaceViewRenderers[2] =
                    findViewById<View>(R.id.remote_view4) as SurfaceViewRenderer
                remoteSurfaceViewRenderers[3] =
                    findViewById<View>(R.id.remote_view5) as SurfaceViewRenderer
                remoteSurfaceViewRenderers[4] =
                    findViewById<View>(R.id.remote_view6) as SurfaceViewRenderer
                remoteSurfaceViewRenderers[5] =
                    findViewById<View>(R.id.remote_view7) as SurfaceViewRenderer
                remoteSurfaceViewRenderers[6] =
                    findViewById<View>(R.id.remote_view8) as SurfaceViewRenderer

                user_view.init(HMSWebRTCEglUtils.getRootEglBaseContext(), null)
                user_view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                user_view.setEnableHardwareScaler(true)
                user_view.setMirror(true)
                for (i in 0 until TOTAL_REMOTE_PEERS) {
                    remoteSurfaceViewRenderers[i]!!
                        .init(HMSWebRTCEglUtils.getRootEglBaseContext(), null)
                    remoteSurfaceViewRenderers[i]!!
                        .setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    remoteSurfaceViewRenderers[i]!!.setEnableHardwareScaler(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun initializeToggleMenu() {
        //disconnectButton = findViewById<ImageButton>(R.id.button_call_disconnect)
        //cameraSwitchButton = findViewById<ImageButton>(R.id.button_call_switch_camera)
        //toggleMuteButton = findViewById<ImageButton>(R.id.button_call_toggle_mic)
        //toggleCameraButton = findViewById<ImageButton>(R.id.button_call_toggle_video)

        // disconnect from the call
        disconnect_button.setOnClickListener(View.OnClickListener {
            disconnectFromRoom()
            finish()
        })

        //Switch camera
        switch_camera_button.setOnClickListener(View.OnClickListener {
            isCameraToggled = true
            hmsClient.switchCamera()
        })

        //Mute/Unmute button
        toggle_mic_button.setOnClickListener(View.OnClickListener {
            toggleUserMic()
            toggle_mic_button.setAlpha(if (isAudioEnabled) 1.0f else 0.2f)
        })
        toggle_video_button.setOnClickListener(View.OnClickListener {
            toggleUserVideo()
            toggle_video_button.setAlpha(if (isVideoEnabled) 1.0f else 0.2f)
        })
    }


    fun toggleUserMic() {
        if (userAudioTrack != null) {
            if (userAudioTrack!!.enabled()) {
                isAudioEnabled = false
                userAudioTrack!!.setEnabled(false)
            } else {
                isAudioEnabled = true
                userAudioTrack!!.setEnabled(true)
            }
        }
    }

    fun toggleUserVideo() {
        if (userVideoTrack != null) {
            if (userVideoTrack!!.enabled()) {
                isVideoEnabled = false
                userVideoTrack!!.setEnabled(false)
                handler.postDelayed({
                    if (!isVideoEnabled) {
                        HMSStream.getCameraCapturer().stop()
                    }
                }, 500)
            } else {
                isVideoEnabled = true
                userVideoTrack!!.setEnabled(true)
                HMSStream.getCameraCapturer().start()
            }
        }
    }


    fun onParticipantsClicked(view: View) {
        val tileIndex = view.tag.toString().toInt()
        val selectedPeer: HMSPeer?
        selectedPeer = if (tileIndex == -1) {
            peer
        } else {
            remotePeers[tileIndex]
        }
        if (selectedPeer == null) {
            return
        }
        Snackbar.make(
            findViewById(R.id.videoCoordinatorLayout),
            "User role: " + selectedPeer.role + " customer id: " + selectedPeer.customerUserId,
            Snackbar.LENGTH_SHORT
        )
            .show()
    }

    fun disconnectFromRoom() {
        isJoined = false
        isPublished = false
        try {
            hmsClient.leave(object : HMSRequestHandler {
                override fun onSuccess(s: String) {
                    Log.d(TAG, "On leave success")
                }

                override fun onFailure(l: Long, s: String) {
                    Log.d(TAG, "On leave failure")
                }
            })
            HMSStream.stopCapturers()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }


        //Clearing all the views
        clearUserView()
        clearRemoteViews()

        //Clean up the local streams
        if (userMediaStream != null) userMediaStream = null
        hmsClient.disconnect()
    }


    fun clearUserView() {
        runOnUiThread {
            if (userSurfaceViewRenderer != null) {
                user_view.visibility = View.INVISIBLE
            }
            if (userSurfaceViewRenderer != null) {
                user_view.release()
                user_view.clearImage()
            }
            userSurfaceViewRenderer = null
        }
    }


    fun clearRemoteViews() {
        printAllUserdata()
        runOnUiThread {
            try {
                cell = 0
                while (cell < TOTAL_REMOTE_PEERS) {
                    if (remoteSurfaceViewRenderers[cell] != null) {
                        remoteSurfaceViewRenderers[cell]!!.visibility = View.INVISIBLE
                    }
                    remoteUserIds[cell] = null
                    remotePeers[cell] = null
                    isRemoteCellFree[cell] = true
                    if (remoteSurfaceViewRenderers[cell] != null) {
                        remoteSurfaceViewRenderers[cell]!!.release()
                        remoteSurfaceViewRenderers[cell]!!.clearImage()
                    }
                    remoteSurfaceViewRenderers[cell] = null
                    cell++
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun printAllUserdata() {
        for (i in 0 until TOTAL_REMOTE_PEERS) {
            Log.d(
                TAG,
                "Pos: " + i + " status: " + isRemoteCellFree[i] + "  uid:" + remoteUserIds[i]
            )
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        disconnectFromRoom()
    }

    fun handleReconnectInRoom() {
        initializeHMSClient()
    }

    fun makeDelay(n: Int): Int {
        val maxDelay = 300000 // 5 minutes
        val delay = Math.pow(2.0, java.lang.Double.valueOf(n.toDouble())).toInt() * 1000
        val jitter = (Math.random() * (1000 - 1 + 1) + 1).toInt()
        return Math.min(delay + jitter, maxDelay)
    }

    override fun onConnect() {
        shouldReconnect = false
        runOnUiThread {
            if (reconnect_progressview.getVisibility() == View.VISIBLE)
                reconnect_progressview.visibility = View.GONE
        }

        retryCount = 0
        Log.d(TAG, "Connect success")
        Log.d(
            TAG,
            "You should be able to see local camera feed once the network connection is established and the user is able to join the room"
        )

        if (!isJoined && hmsClient != null) {
            hmsClient.join(object : HMSRequestHandler {
                override fun onSuccess(data: String) {
                    isJoined = true
                    Log.d(TAG, "join success")
                    getUserMedia(isFrontCameraEnabled, DEFAULT_PUBLISH_AUDIO, isCameraToggled)
                }

                override fun onFailure(error: Long, errorReason: String) {
                    Log.d(TAG, "join failure")
                }
            })
        }
    }


    fun getUserMedia(frontCamEnabled: Boolean, audioEnabled: Boolean, cameraToggle: Boolean) {
        userMediaConstraints = HMSRTCMediaStreamConstraints(true, DEFAULT_PUBLISH_VIDEO)
        userMediaConstraints.videoCodec = DEFAULT_CODEC
        userMediaConstraints.videoFrameRate = Integer.valueOf(DEFAULT_VIDEO_FRAMERATE)
        userMediaConstraints.videoResolution = DEFAULT_VIDEO_RESOLUTION
        userMediaConstraints.videoMaxBitRate = Integer.valueOf(DEFAULT_VIDEO_BITRATE)
        if (frontCamEnabled) {
            isFrontCameraEnabled = true
            userMediaConstraints.cameraFacing = FRONT_FACING_CAMERA
        } else {
            isFrontCameraEnabled = false
            userMediaConstraints.cameraFacing = REAR_FACING_CAMERA
        }
        hmsClient.getUserMedia(
            this,
            userMediaConstraints,
            object : HMSClient.GetUserMediaListener {
                override fun onSuccess(mediaStream: HMSRTCMediaStream) {
                    Log.d(TAG, "getusermedia success")
                    userMediaStream = mediaStream
                    if (userSurfaceViewRenderer == null) {
                        initializeSurfaceViews()
                    }
                    if (mediaStream.stream.videoTracks.size > 0) {
                        userVideoTrack = mediaStream.stream.videoTracks[0]
                        if (DEFAULT_PUBLISH_VIDEO) userVideoTrack!!.setEnabled(true) else userVideoTrack!!.setEnabled(
                            false
                        )
                    }
                    if (mediaStream.stream.audioTracks.size > 0) {
                        userAudioTrack = mediaStream.stream.audioTracks[0]
                        if (DEFAULT_PUBLISH_AUDIO) userAudioTrack!!.setEnabled(true) else userAudioTrack!!.setEnabled(
                            false
                        )
                    }

                    //When you make UI changes, make sure to do it inside applications UI thread.
                    runOnUiThread {
                        try {
                            user_view.visibility = View.VISIBLE
                            userVideoTrack!!.addSink(userSurfaceViewRenderer)
                        } catch (e: java.lang.Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (!isPublished) {
                        hmsClient.publish(
                            userMediaStream,
                            userRoom,
                            userMediaConstraints,
                            object : HMSStreamRequestHandler {
                                override fun onSuccess(data: HMSPublishStream) {
                                    Log.d(TAG, "publish success " + data.mid)
                                    isPublished = true
                                }

                                override fun onFailure(error: Long, errorReason: String) {
                                    Log.d(TAG, "publish failure")
                                }
                            })
                    }
                }

                override fun onFailure(errorcode: Long, errorreason: String) {
                    Log.d("getUserMedia failure", "yes")
                }
            })
    }

    fun getFreePosition(): Int {
        for (i in 0 until TOTAL_REMOTE_PEERS) {
            Log.d(TAG, "printing free pos:$i")
            if (isRemoteCellFree[i] == true) return i
        }
        return -1
    }

    override fun onDisconnect(errorMessage: String?) {
        Log.d(TAG, "ondisconnected: $errorMessage")
        isJoined = false
        isPublished = false

        // clear all views on user screen
        clearUserView()
        clearRemoteViews()

        //Clean up the user streams
        userMediaStream = null
        userAudioTrack = null
        userVideoTrack = null

        if (shouldReconnect) {
            runOnUiThread { reconnect_progressview.visibility = View.VISIBLE }
            if (retryCount > maxRetryCount) {
                Log.d(TAG, "Still disconnected")
            }
            Log.d(TAG, "Connection retry:: delay:$delay retry count: $retryCount")
            delay = makeDelay(retryCount)
            retryCount += 1
            handler.postDelayed({ handleReconnectInRoom() }, delay.toLong())
        }
    }


    override fun onPeerJoin(hmsPeer: HMSPeer?) {
        Log.d(
            TAG,
            "App peer join event " + hmsPeer!!.uid + " role: " + hmsPeer!!.role + " user_id: " + hmsPeer!!.customerUserId
        )
    }

    override fun onPeerLeave(hmsPeer: HMSPeer?) {
        Log.d(TAG, "App peer leave event" + hmsPeer!!.uid)
        Log.d(TAG, "On peer leave")
    }

    override fun onStreamAdd(hmsPeer: HMSPeer?, hmsStreamInfo: HMSStreamInfo?) {
        Log.d(
            TAG,
            "App stream add  event" + hmsPeer!!.uid + " role: " + hmsPeer!!.role + " user_id: " + hmsPeer!!.customerUserId
        )
        //Handling all the on stream add events inside a single thread to avoid race condition during rendering
        //Handling all the on stream add events inside a single thread to avoid race condition during rendering
        val subscribeRunnable = Runnable {
            hmsClient.subscribe(hmsStreamInfo, userRoom, object : HMSMediaRequestHandler {
                override fun onSuccess(data: MediaStream) {
                    val pos: Int = getFreePosition()
                    Log.d(TAG, "Current free positions: $pos")
                    if (pos == -1) Log.d(
                        TAG,
                        "No more UI space for additional users but you can hear the audio"
                    ) else {
                        remoteUserIds[pos] = hmsStreamInfo!!.uid
                        isRemoteCellFree[pos] = false
                        remotePeers[pos] = hmsPeer
                        Log.d(TAG, "On subscribe success")
                        Log.d(TAG, "position: $pos")
                        Log.d(TAG, "user id: " + hmsStreamInfo!!.uid)
                        setTracks(data, pos, hmsStreamInfo!!.userName)
                        printAllUserdata()
                    }
                }

                override fun onFailure(error: Long, errorReason: String) {
                    Log.d("HMSClient", "Onsubsuccess")
                }
            })
        }
        serviceExecutor.execute(subscribeRunnable)
    }

    fun setTracks(data: MediaStream, position: Int, name: String?) {
        if (data.videoTracks.size > 0) {
            remoteVideoTracks[position] = data.videoTracks[0]
            remoteVideoTracks[position]!!.setEnabled(true)
        }
        if (data.audioTracks.size > 0) {
            remoteAudioTracks[position] = data.audioTracks[0]
            remoteAudioTracks[position]!!.setEnabled(true)
        }
        runOnUiThread {
            if (name != null) {
                if (data.videoTracks.size > 0 && data.audioTracks.size > 0) remoteTextViews[position]!!.text =
                    name
            }
            if (position == 0) remoteSurfaceViewRenderers[position] =
                findViewById<View>(R.id.remote_view2) as SurfaceViewRenderer
            if (position == 1) remoteSurfaceViewRenderers[position] =
                findViewById<View>(R.id.remote_view3) as SurfaceViewRenderer
            if (position == 2) remoteSurfaceViewRenderers[position] =
                findViewById<View>(R.id.remote_view4) as SurfaceViewRenderer
            if (position == 3) remoteSurfaceViewRenderers[position] =
                findViewById<View>(R.id.remote_view5) as SurfaceViewRenderer
            if (position == 4) remoteSurfaceViewRenderers[position] =
                findViewById<View>(R.id.remote_view6) as SurfaceViewRenderer
            if (position == 5) remoteSurfaceViewRenderers[position] =
                findViewById<View>(R.id.remote_view7) as SurfaceViewRenderer
            if (position == 6) remoteSurfaceViewRenderers[position] =
                findViewById<View>(R.id.remote_view8) as SurfaceViewRenderer
            remoteSurfaceViewRenderers[position]!!.visibility = View.VISIBLE

            if (data.videoTracks.size > 0) {
                if (remoteSurfaceViewRenderers[position] == null) {

                    remoteSurfaceViewRenderers[position]!!.init(HMSWebRTCEglUtils.getRootEglBaseContext(), null)
                    remoteSurfaceViewRenderers[position]!!.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    remoteSurfaceViewRenderers[position]!!.setEnableHardwareScaler(true)

                }

                remoteSurfaceViewRenderers[position]!!.visibility = View.VISIBLE
                remoteVideoTracks[position]!!.addSink(remoteSurfaceViewRenderers[position])

            }
        }
    }

    override fun onStreamRemove(hmsStreamInfo: HMSStreamInfo) {
        Log.d(TAG, "onstream remove:" + hmsStreamInfo.uid)

        printAllUserdata()
        var i = 0
        i = 0
        while (i < TOTAL_REMOTE_PEERS) {

            if (remoteUserIds[i] != null && remoteUserIds[i].equals(
                    hmsStreamInfo.uid,
                    ignoreCase = true
                )
            ) {
                isRemoteCellFree[i] = true
                remoteUserIds[i] = null
                remotePeers[i] = null
                val temp = i

                runOnUiThread {
                    if (remoteSurfaceViewRenderers[temp] != null) remoteSurfaceViewRenderers[temp]!!.visibility =
                        View.INVISIBLE
                }
                if (remoteSurfaceViewRenderers[i] != null) {
                    remoteSurfaceViewRenderers[i]!!.clearImage()
                }
            }
            i++
        }
    }

    override fun onBroadcast(p0: HMSPayloadData?) {
        TODO("Not yet implemented")
    }
}