package eu.wroblewscy.marcin.floating.floating

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Rational
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.util.*
import kotlin.concurrent.fixedRateTimer

/** FloatingPlugin */
class FloatingPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel: MethodChannel
  private lateinit var context: Context
  private lateinit var activity: Activity
  private lateinit var mediaSession: MediaSessionCompat

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "floating")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
  }

  @RequiresApi(Build.VERSION_CODES.N)
  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == "enablePip") {
      enablePip(call, result)
    } else if (call.method == "pipAvailable") {
      result.success(
          activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
      )
    } else if (call.method == "inPipAlready") {
      result.success(
          activity.isInPictureInPictureMode
      )
    } else if (call.method == "cancelAutoEnable") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        activity.setPictureInPictureParams(PictureInPictureParams.Builder()
          .setAutoEnterEnabled(false).build())
      }
      result.success(true)
    } else
     {
      result.notImplemented()
    }
  }

  private fun enablePip(call: MethodCall, result: Result) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val builder = PictureInPictureParams.Builder()
        .setAspectRatio(
          Rational(
            call.argument("numerator") ?: 16,
            call.argument("denominator") ?: 9
          )
        )
        // Only for Android 12 and above, enable auto PiP entry
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }

        val sourceRectHintLTRB = call.argument<List<Int>>("sourceRectHintLTRB")
      if (sourceRectHintLTRB?.size == 4) {
        val bounds = Rect(
          sourceRectHintLTRB[0],
          sourceRectHintLTRB[1],
          sourceRectHintLTRB[2],
          sourceRectHintLTRB[3]
        )
        builder.setSourceRectHint(bounds)
      }


      val autoEnable = call.argument<Boolean>("autoEnable") ?: false
      if (autoEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(true)
        activity.setPictureInPictureParams(builder.build())
        result.success(true)
        return
      } else if (autoEnable && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        result.error(
          "OnLeavePiP not available",
          "OnLeavePiP is only available on SDK higher than 31",
          "Current SDK: ${Build.VERSION.SDK_INT}, required: >=31"
        )
        return
      }
      activity.enterPictureInPictureMode(builder.build())

      initializeMediaSession()

      result.success(true)
    } else {
      activity.enterPictureInPictureMode()
      result.success(false)
    }
  }

  private fun initializeMediaSession() {
      mediaSession = MediaSessionCompat(activity, "PipSession").apply {
          setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

          val stateBuilder = PlaybackStateCompat.Builder()
              .setActions(
                  PlaybackStateCompat.ACTION_REWIND or
                  PlaybackStateCompat.ACTION_PLAY or
                  PlaybackStateCompat.ACTION_PAUSE or
                  PlaybackStateCompat.ACTION_FAST_FORWARD
              )
              .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)

          setPlaybackState(stateBuilder.build())

          // Set callback to handle play/pause/seek actions in PiP mode
          setCallback(object : MediaSessionCompat.Callback() {
              override fun onPlay() {
                  // Comment: Notify Dart to play the video
                  channel.invokeMethod("onPlayPressed", null)
                  updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
              }

              override fun onPause() {
                  // Comment: Notify Dart to pause the video
                  channel.invokeMethod("onPausePressed", null)
                  updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
              }

              override fun onFastForward() {
                  // Comment: Notify Dart to seek forward
                  channel.invokeMethod("onSeekForwardPressed", null)
              }

              override fun onRewind() {
                  // Comment: Notify Dart to seek backward
                  channel.invokeMethod("onSeekBackwardPressed", null)
              }
          })

          isActive = true
      }
  }

  private fun updatePlaybackState(state: Int) {
    val stateBuilder = PlaybackStateCompat.Builder()
      .setActions(
          PlaybackStateCompat.ACTION_REWIND or
          PlaybackStateCompat.ACTION_PLAY or
          PlaybackStateCompat.ACTION_PAUSE or
          PlaybackStateCompat.ACTION_FAST_FORWARD
      )
      .setState(state, 0L, 1f)
    mediaSession.setPlaybackState(stateBuilder.build())
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onDetachedFromActivity() {}

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    useBinding(binding)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    useBinding(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {}

  private fun useBinding(binding: ActivityPluginBinding) {
    activity = binding.activity
  }
}
