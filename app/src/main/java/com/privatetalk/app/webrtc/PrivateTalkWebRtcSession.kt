package com.privatetalk.app.webrtc

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

class PrivateTalkWebRtcSession(
    private val context: Context,
    private val onLocalSdp: (String) -> Unit,
    private val onLocalIce: (String) -> Unit,
    private val onStateChanged: (String) -> Unit
) {
    val eglBase: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private var capturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var peerConnection: PeerConnection? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var started = false

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
        audioDeviceModule.release()
    }

    fun start(
        localRenderer: SurfaceViewRenderer?,
        remoteRenderer: SurfaceViewRenderer?,
        videoEnabled: Boolean,
        audioEnabled: Boolean
    ) {
        if (started) return
        started = true
        this.localRenderer = localRenderer
        this.remoteRenderer = remoteRenderer
        localRenderer?.init(eglBase.eglBaseContext, null)
        localRenderer?.setMirror(true)
        remoteRenderer?.init(eglBase.eglBaseContext, null)
        remoteRenderer?.setMirror(false)

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: MediaStream?) = Unit
                override fun onRemoveStream(stream: MediaStream?) = Unit
                override fun onDataChannel(channel: DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    onStateChanged(
                        when (state) {
                            PeerConnection.IceConnectionState.CONNECTED,
                            PeerConnection.IceConnectionState.COMPLETED -> "Connected"
                            PeerConnection.IceConnectionState.CHECKING -> "Connecting..."
                            PeerConnection.IceConnectionState.DISCONNECTED -> "Reconnecting..."
                            PeerConnection.IceConnectionState.FAILED -> "Call connection failed"
                            PeerConnection.IceConnectionState.CLOSED -> "Call ended"
                            else -> "Starting call..."
                        }
                    )
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    onLocalIce(
                        JSONObject()
                            .put("sdpMid", candidate.sdpMid)
                            .put("sdpMLineIndex", candidate.sdpMLineIndex)
                            .put("candidate", candidate.sdp)
                            .toString()
                    )
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        remoteRenderer?.let { track.addSink(it) }
                    }
                }
            }
        ) ?: run {
            onStateChanged("Could not start WebRTC")
            return
        }

        if (audioEnabled) {
            audioSource = factory.createAudioSource(MediaConstraints())
            localAudioTrack = factory.createAudioTrack("private-audio", audioSource).also { track ->
                track.setEnabled(true)
                peerConnection?.addTrack(track, listOf("private-stream"))
            }
        }

        if (videoEnabled) {
            val activeCapturer = createCameraCapturer()
            if (activeCapturer != null) {
                runCatching {
                    val source = factory.createVideoSource(false)
                    val textureHelper = SurfaceTextureHelper.create("PrivateTalkCamera", eglBase.eglBaseContext)
                    activeCapturer.initialize(textureHelper, context, source.capturerObserver)
                    activeCapturer.startCapture(640, 480, 24)
                    val track = factory.createVideoTrack("private-video", source)
                    track.setEnabled(true)
                    localRenderer?.let { track.addSink(it) }
                    peerConnection?.addTrack(track, listOf("private-stream"))
                    capturer = activeCapturer
                    videoSource = source
                    localVideoTrack = track
                }.onFailure {
                    onStateChanged("Camera failed: ${it.message ?: "unknown"}")
                }
            } else {
                onStateChanged("No camera found")
            }
        }
        onStateChanged("Ready")
    }

    fun createOffer() {
        val connection = peerConnection ?: return
        connection.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription?) {
                description ?: return
                connection.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        onLocalSdp(encodeSdp(description))
                    }
                    override fun onSetFailure(error: String?) {
                        onStateChanged("Offer failed: ${error ?: "set failed"}")
                    }
                }, description)
            }
            override fun onCreateFailure(error: String?) {
                onStateChanged("Offer failed: ${error ?: "create failed"}")
            }
        }, mediaConstraints())
    }

    fun acceptOffer(remoteSdpJson: String) {
        val connection = peerConnection ?: return
        val offer = decodeSdp(remoteSdpJson)
        connection.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                connection.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(description: SessionDescription?) {
                        description ?: return
                        connection.setLocalDescription(object : SdpObserverAdapter() {
                            override fun onSetSuccess() {
                                onLocalSdp(encodeSdp(description))
                            }
                            override fun onSetFailure(error: String?) {
                                onStateChanged("Answer failed: ${error ?: "set failed"}")
                            }
                        }, description)
                    }
                    override fun onCreateFailure(error: String?) {
                        onStateChanged("Answer failed: ${error ?: "create failed"}")
                    }
                }, mediaConstraints())
            }
            override fun onSetFailure(error: String?) {
                onStateChanged("Offer read failed: ${error ?: "set failed"}")
            }
        }, offer)
    }

    fun acceptAnswer(remoteSdpJson: String) {
        peerConnection?.setRemoteDescription(
            object : SdpObserverAdapter() {
                override fun onSetFailure(error: String?) {
                    onStateChanged("Answer read failed: ${error ?: "set failed"}")
                }
            },
            decodeSdp(remoteSdpJson)
        )
    }

    fun addIceCandidate(candidateJson: String) {
        val json = JSONObject(candidateJson)
        peerConnection?.addIceCandidate(
            IceCandidate(
                json.optString("sdpMid"),
                json.optInt("sdpMLineIndex"),
                json.optString("candidate")
            )
        )
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun stop() {
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        peerConnection?.dispose()
        runCatching { localRenderer?.release() }
        runCatching { remoteRenderer?.release() }
        factory.dispose()
        eglBase.release()
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val frontCamera = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val anyCamera = frontCamera ?: enumerator.deviceNames.firstOrNull()
        return anyCamera?.let { enumerator.createCapturer(it, null) }
    }

    private fun encodeSdp(description: SessionDescription): String {
        return JSONObject()
            .put("type", description.type.canonicalForm())
            .put("sdp", description.description)
            .toString()
    }

    private fun decodeSdp(payload: String): SessionDescription {
        val json = JSONObject(payload)
        val type = when (json.optString("type")) {
            "offer" -> SessionDescription.Type.OFFER
            "answer" -> SessionDescription.Type.ANSWER
            else -> SessionDescription.Type.OFFER
        }
        return SessionDescription(type, json.optString("sdp"))
    }

    private fun mediaConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
    }
}

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}
