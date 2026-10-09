package com.localpipper.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.localpipper.app.ui.PlayerScreen
import com.localpipper.app.ui.VideoItem
import com.localpipper.app.ui.VideoListScreen
import com.localpipper.app.ui.getNextVideoInFolder
import com.localpipper.app.ui.theme.LocalPipperTheme
import com.localpipper.app.PlaybackPositionRepository

class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null
    private var mediaControllerListener: Player.Listener? = null
    private val isInPipMode = mutableStateOf(false)
    private val isFullscreenMode = mutableStateOf(false)
    // 画面状態をActivityレベルで保持し、ホームボタン押下時のPiP遷移を確実に動画プレーヤーに切り替える
    private val currentScreenState = mutableStateOf("list") // "list" or "player"
    private val currentVideoState = mutableStateOf<VideoItem?>(null)
    private lateinit var positionRepository: PlaybackPositionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        positionRepository = PlaybackPositionRepository.getInstance(applicationContext)
        requestNotificationPermission()
        requestStoragePermission()

        setContent {
            val controllerState = remember { mutableStateOf<Player?>(null) }
            var isPlayerPlaying by remember { mutableStateOf(false) }
            var hasStoragePermission by remember {
                mutableStateOf<Boolean>(checkStoragePermission())
            }

            // MediaSessionService に接続
            LaunchedEffect(Unit) {
                val token = SessionToken(
                    this@MainActivity,
                    ComponentName(this@MainActivity, PlaybackService::class.java)
                )
                val future = MediaController.Builder(this@MainActivity, token).buildAsync()
                future.addListener(
                    {
                        try {
                            val mc = future.get()
                            mediaController = mc
                            controllerState.value = mc
                            isPlayerPlaying = mc.isPlaying

                            // 初期接続時の再生状態に応じてスリープ防止およびPiPパラメータを設定
                            setKeepScreenOn(mc.isPlaying)
                            updatePipParams(mc.isPlaying)

                            // リスナーを追加し、参照を保存して後で削除できるようにする
                            val listener = object : Player.Listener {
                                // 動画再生中はスリープしないよう制御 & 再生状態の同期 & PiP自動進入更新
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    isPlayerPlaying = isPlaying
                                    setKeepScreenOn(isPlaying)
                                    updatePipParams(isPlaying)
                                }

                                // 動画終了時に同一フォルダの次の動画を自動再生する
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Player.STATE_ENDED) {
                                        setKeepScreenOn(false)
                                        updatePipParams(false)
                                        val video = currentVideoState.value ?: return
                                        lifecycleScope.launch {
                                            try {
                                                // Save the current video's position (which is at the end)
                                                val currentPosition = mc.currentPosition
                                                positionRepository.savePosition(video.uri.toString(), currentPosition)
                                                // Save last video URI
                                                positionRepository.saveLastVideoUri(video.uri.toString())

                                                val nextVideo = getNextVideoInFolder(
                                                    this@MainActivity, video
                                                )
                                                if (nextVideo != null) {
                                                    currentVideoState.value = nextVideo
                                                    // Save last video URI for next video
                                                    positionRepository.saveLastVideoUri(nextVideo.uri.toString())
                                                    val mediaItem = MediaItem.Builder()
                                                        .setUri(nextVideo.uri)
                                                        .setMediaId(nextVideo.uri.toString())
                                                        .build()
                                                    val startPosition = positionRepository.getPosition(nextVideo.uri.toString())
                                                    if (startPosition >= 0) {
                                                        mc.setMediaItem(mediaItem, startPosition)
                                                    } else {
                                                        mc.setMediaItem(mediaItem)
                                                    }
                                                    mc.prepare()
                                                    mc.play()
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                }
                            }
                            mc.addListener(listener)
                            mediaControllerListener = listener
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    ContextCompat.getMainExecutor(this@MainActivity)
                )
            }

            // ファイルピッカー
            val filePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let { selectedUri ->
                    // Save the current video's position if there is a current video and mediaController is not null
                    val currentUri = currentVideoState.value?.uri
                    if (currentUri != null && mediaController != null) {
                        val currentPosition = mediaController?.currentPosition
                        if (currentPosition != null) {
                            positionRepository.savePosition(currentUri.toString(), currentPosition)
                        }
                    }
                    // URI権限を永続化
                    try {
                        contentResolver.takePersistableUriPermission(
                            selectedUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // 永続化が利用不可でも再生は試行する
                    }

                    // ファイル名を取得
                    val displayName = try {
                        contentResolver.query(
                            selectedUri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    } catch (_: Exception) {
                        null
                    } ?: "選択した動画"

                    currentVideoState.value = VideoItem(
                        id = -1L,
                        uri = selectedUri,
                        title = displayName,
                        duration = 0L,
                        dateAdded = System.currentTimeMillis() / 1000,
                        folderPath = "",
                        data = ""
                    )
                    // Save last video URI
                    positionRepository.saveLastVideoUri(selectedUri.toString())
                    val mediaItem = MediaItem.Builder()
                        .setUri(selectedUri)
                        .setMediaId(selectedUri.toString())
                        .build()
                    mediaController?.let { player ->
                        val startPosition = positionRepository.getPosition(selectedUri.toString())
                        if (startPosition >= 0) {
                            player.setMediaItem(mediaItem, startPosition)
                        } else {
                            player.setMediaItem(mediaItem)
                        }
                        player.prepare()
                        player.play()
                    }
                    currentScreenState.value = "player"
                }
            }

            // ストレージ権限リクエスト結果
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                hasStoragePermission = permissions.values.any { it }
            }

            // 権限がなければリクエスト
            LaunchedEffect(hasStoragePermission) {
                if (!hasStoragePermission) {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionLauncher.launch(perms)
                }
            }

            LocalPipperTheme {
                when (currentScreenState.value) {
                    "list" -> {
                        // 一覧に戻った際は全画面を解除
                        if (isFullscreenMode.value) {
                            setFullscreen(false)
                        }
                        VideoListScreen(
                            player = controllerState.value,
                            currentVideo = currentVideoState.value,
                            isPlaying = isPlayerPlaying,
                            onReturnToPlayer = { currentScreenState.value = "player" },
                            onTogglePlay = {
                                mediaController?.let { mc ->
                                    if (mc.isPlaying) mc.pause() else mc.play()
                                }
                            },
                            onClosePlayer = {
                                mediaController?.stop()
                                currentVideoState.value = null
                            },
                            onVideoSelected = { video ->
                                // Save the current video's position if there is a current video and mediaController is not null
                                val currentUri = currentVideoState.value?.uri
                                if (currentUri != null && mediaController != null) {
                                    val currentPosition = mediaController?.currentPosition
                                    if (currentPosition != null) {
                                        positionRepository.savePosition(currentUri.toString(), currentPosition)
                                    }
                                }
                                // Restore position for the new video and play it
                                currentVideoState.value = video
                                // Save last video URI
                                positionRepository.saveLastVideoUri(video.uri.toString())
                                val mediaItem = MediaItem.Builder()
                                    .setUri(video.uri)
                                    .setMediaId(video.uri.toString())
                                    .build()
                                mediaController?.let { player ->
                                    val startPosition = positionRepository.getPosition(video.uri.toString())
                                    if (startPosition >= 0) {
                                        player.setMediaItem(mediaItem, startPosition)
                                    } else {
                                        player.setMediaItem(mediaItem)
                                    }
                                    player.prepare()
                                    player.play()
                                }
                                currentScreenState.value = "player"
                            },
                            onVideoDeleted = { deletedVideo ->
                                // 削除された動画が現在再生中だった場合、再生を停止して選択解除
                                if (currentVideoState.value?.id == deletedVideo.id) {
                                    mediaController?.stop()
                                    currentVideoState.value = null
                                }
                            }
                        )
                    }
                    "player" -> PlayerScreen(
                        player = controllerState.value,
                        isInPipMode = isInPipMode.value,
                        isFullscreen = isFullscreenMode.value,
                        onSelectVideo = { filePicker.launch(arrayOf("video/*")) },
                        onEnterPip = { enterPipMode() },
                        onToggleFullscreen = { setFullscreen(!isFullscreenMode.value) },
                        onBackToList = {
                            if (isFullscreenMode.value) {
                                setFullscreen(false)
                            }
                            currentScreenState.value = "list"
                        },
                        videoTitle = currentVideoState.value?.title
                    )
                }
            }
        }
    }

    /** 再生中の画面スリープ防止フラグを切り替え */
    private fun setKeepScreenOn(keep: Boolean) {
        runOnUiThread {
            if (keep) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    /** Android 12+ のジェスチャー/自動PiPパラメータ更新 */
    private fun updatePipParams(isPlaying: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(isPlaying)
                    .build()
                setPictureInPictureParams(params)
            } catch (_: Exception) {}
        }
    }

    /** 全画面表示モードの切り替え (システムバー非表示 & 横画面) */
    private fun setFullscreen(enable: Boolean) {
        isFullscreenMode.value = enable
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (enable) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** ストレージ権限チェック */
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** ストレージ権限リクエスト */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VIDEO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
                    200
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    200
                )
            }
        }
    }

    /** Android 13+ の通知権限リクエスト */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    /** PiPモードに入る */
    private fun enterPipMode() {
        if (isFullscreenMode.value) {
            setFullscreen(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    /**
     * ホームボタン押下時 — 再生中の動画がある場合、
     * 一覧画面にいても自動的にプレーヤー画面に切り替えてPiPを開始する。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (mediaController?.isPlaying == true) {
            currentScreenState.value = "player"
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
        // PiPモードに入った際は、確実に動画プレーヤー画面を表示させる
        if (isInPictureInPictureMode) {
            currentScreenState.value = "player"
        }
    }

    override fun onPause() {
        super.onPause()
        val player = mediaController
        if (player != null) {
            val currentMediaItem = player.currentMediaItem
            if (currentMediaItem != null) {
                val position = player.currentPosition
                if (position != null) {
                    val mediaId = currentMediaItem.mediaId
                    if (mediaId.isNotEmpty()) {
                        positionRepository.savePosition(mediaId, position)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        setKeepScreenOn(false)
        if (isFullscreenMode.value) {
            setFullscreen(false)
        }
        // リスナーを削除してからメディアコントローラーを解放
        mediaControllerListener?.let {
            mediaController?.removeListener(it)
            mediaControllerListener = null
        }
        mediaController?.release()
        mediaController = null
        // If the activity is finishing (user pressed back), stop the playback service
        if (isFinishing) {
            val intent = Intent(this, PlaybackService::class.java)
            stopService(intent)
        }
        super.onDestroy()
    }
}