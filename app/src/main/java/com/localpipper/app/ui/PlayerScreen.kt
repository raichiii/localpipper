package com.localpipper.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.compose.ui.platform.LocalContext
import com.localpipper.app.PlaybackPositionRepository

/**
 * PlayerControlView の再生速度メニュー（歯車アイコン内）を
 * 0.2倍〜4.0倍（0.2倍刻み）にカスタマイズする。
 */
private fun customizePlaybackSpeedMenu(playerView: PlayerView) {
    try {
        // PlayerView の controller (PlayerControlView) を取得
        val controllerField = PlayerView::class.java.getDeclaredField("controller")
        controllerField.isAccessible = true
        val controller = controllerField.get(playerView) ?: return

        // 0.2〜4.0 (0.2刻み、20段階)
        val speeds = FloatArray(20) { i -> ((i + 1) * 2) / 10f }
        val texts = speeds.map { speed ->
            if (speed == 1.0f) "1.0x (標準)" else String.format(java.util.Locale.US, "%.1fx", speed)
        }.toTypedArray()

        val adapterField = controller.javaClass.getDeclaredField("playbackSpeedAdapter")
        adapterField.isAccessible = true
        val oldAdapter = adapterField.get(controller)

        if (oldAdapter != null) {
            // 既存アダプターの配列を差し替え
            val textsField = oldAdapter.javaClass.getDeclaredField("playbackSpeedTexts")
            textsField.isAccessible = true
            textsField.set(oldAdapter, texts)

            val speedsField = oldAdapter.javaClass.getDeclaredField("playbackSpeeds")
            speedsField.isAccessible = true
            speedsField.set(oldAdapter, speeds)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * メインプレーヤー画面。
 *
 * @param player              MediaController (Player 実装)。null の場合はローディング表示。
 * @param isInPipMode         PiP モード中かどうか。true の場合コントロールを非表示にする。
 * @param isFullscreen        全画面モード中かどうか。
 * @param onSelectVideo       動画選択ボタン押下コールバック (互換用)。
 * @param onEnterPip          PiP ボタン押下コールバック (互換用)。
 * @param onToggleFullscreen  全画面切り替えボタン押下コールバック。
 * @param onBackToList        一覧に戻るボタン押下コールバック。
 * @param videoTitle          現在再生中の動画タイトル (nullの場合は非表示)。
 */
@Composable
fun PlayerScreen(
    player: Player?,
    isInPipMode: Boolean,
    isFullscreen: Boolean = false,
    onSelectVideo: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onBackToList: () -> Unit = {},
    videoTitle: String? = null
) {
    val context = LocalContext.current
    // 全画面表示中は「戻る」操作で全画面を解除
    BackHandler(enabled = isFullscreen) {
        onToggleFullscreen()
    }

    // 再生速度の保存値を読み込み
    val savedSpeed: Float? = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        .getFloat("last_playback_speed", -1.0f).takeIf { it != -1.0f }
    // 速度状態 (0.2〜4.0, 0.2刻み)
    var playbackSpeed by remember {
        mutableFloatStateOf(
            savedSpeed ?: player?.playbackParameters?.speed?.let {
                ((it / 0.2f).roundToInt() * 0.2f).coerceIn(0.2f, 4.0f)
            } ?: 1.0f
        )
    }

    // 保存された再生速度をプレイヤーに適用
    savedSpeed?.let { speed ->
        player?.let { p ->
            p.setPlaybackSpeed(speed)
        }
    }

    // Player の再生速度変更イベントを監視して双方向同期 (歯車メニューでの変更も検知)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                val speed = playbackParameters.speed ?: 1.0f
                val rounded = ((speed / 0.2f).roundToInt() * 0.2f)
                playbackSpeed = ((rounded * 10).roundToInt() / 10f).coerceIn(0.2f, 4.0f)
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
        }
    }

    // 下ドラッグ移動量の累積
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- 動画プレーヤー (下ドラッグで一覧画面へ移動) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isInPipMode || isFullscreen) Modifier.fillMaxSize()
                    else Modifier.aspectRatio(16f / 9f)
                )
                .pointerInput(isFullscreen, isInPipMode) {
                    if (!isInPipMode) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragAccumulator > 60f) {
                                    if (isFullscreen) {
                                        onToggleFullscreen()
                                    } else {
                                        onBackToList()
                                    }
                                }
                                dragAccumulator = 0f
                            },
                            onDragCancel = {
                                dragAccumulator = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0) { // 下方向ドラッグ
                                    dragAccumulator += dragAmount
                                    if (dragAccumulator > 100f) {
                                        change.consume()
                                        if (isFullscreen) {
                                            onToggleFullscreen()
                                        } else {
                                            onBackToList()
                                        }
                                        dragAccumulator = 0f
                                    }
                                }
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = !isInPipMode
                            keepScreenOn = player?.isPlaying ?: false
                            if (player != null) {
                                customizePlaybackSpeedMenu(this)
                            }
                            // コントローラー内の全画面ボタン押下でもトグル
                            setFullscreenButtonClickListener { _ ->
                                onToggleFullscreen()
                            }
                        }
                    },
                    update = { view ->
                        view.player = player
                        view.useController = !isInPipMode
                        view.keepScreenOn = player?.isPlaying ?: false
                        if (player != null) {
                            customizePlaybackSpeedMenu(view)
                        }
                        view.setFullscreenButtonClickListener { _ ->
                            onToggleFullscreen()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // --- コントロール (PiP および 全画面 中は非表示) ---
        if (!isInPipMode && !isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 動画タイトル表示
                if (videoTitle != null) {
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }
                // ====== スキップコントロールカード ======
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF1A1040),
                                    Color(0xFF0D2040)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "10秒スキップ",
                            color = Color(0xFFAAAACC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 10秒巻き戻しボタン
                            FilledIconButton(
                                onClick = {
                                    player?.let { currentPlayer ->
                                        val currentPos = currentPlayer.currentPosition
                                        val newPos = (currentPos - 10_000).coerceIn(0L, currentPlayer.duration)
                                        currentPlayer.seekTo(newPos)
                                        // Save position after skip
                                        val uriString = currentPlayer.currentMediaItem?.mediaId
                                        uriString?.let { uri ->
                                            val repository = PlaybackPositionRepository.getInstance(context)
                                            repository.savePosition(uri, newPos)
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF2A2A5A)
                                )
                            ) {
                                Text("⏪", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }

                            // 10秒早送りボタン
                            FilledIconButton(
                                onClick = {
                                    player?.let { currentPlayer ->
                                        val currentPos = currentPlayer.currentPosition
                                        val newPos = (currentPos + 10_000).coerceIn(0L, currentPlayer.duration)
                                        currentPlayer.seekTo(newPos)
                                        // Save position after skip
                                        val uriString = currentPlayer.currentMediaItem?.mediaId
                                        uriString?.let { uri ->
                                            val repository = PlaybackPositionRepository.getInstance(context)
                                            repository.savePosition(uri, newPos)
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF2A2A5A)
                                )
                            ) {
                                Text("⏩", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ====== 速度調整カード (サイズ0.6倍、バー不要) ======
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF1A1040),
                                    Color(0xFF0D2040)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "再生速度 (0.2x〜4.0x)",
                                color = Color(0xFFAAAACC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "⚙ 歯車も連動",
                                color = Color(0xFF00DDFF),
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // − ボタン / 速度表示 / ＋ ボタン
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // − ボタン (0.2刻みで減算)
                            FilledIconButton(
                                onClick = {
                                    val currentSteps = (playbackSpeed / 0.2f).roundToInt()
                                    val newSteps = currentSteps - 1
                                    if (newSteps >= 1) { // 最小 0.2x (1 * 0.2)
                                        val newSpeed = (newSteps * 2) / 10f
                                        playbackSpeed = newSpeed
                                        player?.setPlaybackSpeed(playbackSpeed)
                                        // Save the new speed
                                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                                        try {
                                            prefs.edit().putFloat("last_playback_speed", playbackSpeed).apply()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF2A2A5A)
                                )
                            ) {
                                Text("−", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // 速度表示
                            Text(
                                text = "%.1fx".format(playbackSpeed),
                                color = Color(0xFF00DDFF),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            // ＋ ボタン (0.2刻みで加算)
                            FilledIconButton(
                                onClick = {
                                    val currentSteps = (playbackSpeed / 0.2f).roundToInt()
                                    val newSteps = currentSteps + 1
                                    if (newSteps <= 20) { // 最大 4.0x (20 * 0.2)
                                        val newSpeed = (newSteps * 2) / 10f
                                        playbackSpeed = newSpeed
                                        player?.setPlaybackSpeed(playbackSpeed)
                                        // Save the new speed
                                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                                        try {
                                            prefs.edit().putFloat("last_playback_speed", playbackSpeed).apply()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF2A2A5A)
                                )
                            ) {
                                Text("+", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // プリセットボタン (0.2刻みの代表値)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(0.6f, 1.0f, 1.4f, 2.0f, 3.0f, 4.0f).forEach { preset ->
                                val isActive = (playbackSpeed * 10).roundToInt() == (preset * 10).roundToInt()
                                FilledTonalButton(
                                    onClick = {
                                        playbackSpeed = preset
                                        player?.setPlaybackSpeed(preset)
                                        // Save the new speed
                                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                                        try {
                                            prefs.edit().putFloat("last_playback_speed", playbackSpeed).apply()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier.height(26.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 0.dp
                                    ),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isActive) Color(0xFF00DDFF) else Color(0xFF2A2A5A),
                                        contentColor = if (isActive) Color.Black else Color(0xFFAAAACC)
                                    )
                                ) {
                                    Text(
                                        "%.1fx".format(preset),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "動画を下にドラッグすると一覧画面に戻り、左下で再生が継続します",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00DDFF)
                )

                Text(
                    text = "再生中にホームボタンを押すと自動的に PiP に切り替わります",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Text(
                    text = "動画終了後、同じフォルダ内の次の動画を自動再生します",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6688AA)
                )
            }
        }
    }
}