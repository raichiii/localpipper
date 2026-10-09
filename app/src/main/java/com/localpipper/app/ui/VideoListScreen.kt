package com.localpipper.app.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ローカルの動画情報を保持するデータクラス。
 */
data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,      // ミリ秒
    val dateAdded: Long,     // エポック秒
    val folderPath: String,  // 親フォルダのパス
    val data: String         // ファイルの絶対パス
)

/**
 * MediaStoreからローカル動画一覧を取得する。
 */
suspend fun loadVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
    val videos = mutableListOf<VideoItem>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.RELATIVE_PATH
    )

    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        collection, projection, null, null, sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Unknown"
            val duration = cursor.getLong(durationCol)
            val dateAdded = cursor.getLong(dateAddedCol)
            val data = cursor.getString(dataCol) ?: ""
            val folderPath = data.substringBeforeLast("/", "")

            val contentUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
            )

            videos.add(
                VideoItem(
                    id = id,
                    uri = contentUri,
                    title = name,
                    duration = duration,
                    dateAdded = dateAdded,
                    folderPath = folderPath,
                    data = data
                )
            )
        }
    }
    videos
}

/**
 * 同一フォルダ内の動画を DATE_ADDED 降順で取得し、
 * 指定動画の次に追加日が遅い動画を返す。
 */
suspend fun getNextVideoInFolder(
    context: Context,
    currentVideo: VideoItem
): VideoItem? = withContext(Dispatchers.IO) {
    val allVideos = loadVideos(context)
    val sameFolder = allVideos
        .filter { it.folderPath == currentVideo.folderPath }
        .sortedByDescending { it.dateAdded }

    val currentIndex = sameFolder.indexOfFirst { it.id == currentVideo.id }
    if (currentIndex >= 0 && currentIndex < sameFolder.size - 1) {
        sameFolder[currentIndex + 1]
    } else {
        null
    }
}

/**
 * ミリ秒を "H:MM:SS" または "M:SS" 形式にフォーマットする。
 */
fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * エポック秒を "yyyy/MM/dd" 形式にフォーマットする。
 */
fun formatDate(epochSec: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochSec * 1000))
}

/**
 * 動画サムネイルを読み込む Composable。
 */
@Composable
fun VideoThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(480, 270), null)
                } else {
                    null
                }
            } catch (_: Exception) {
                bitmap = null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("🎬", fontSize = 36.sp)
        }
    }
}

/**
 * ローカル動画一覧画面。
 *
 * @param player           再生中の Player (左下の継続再生ミニプレイヤー用)
 * @param currentVideo     現在読み込み/再生中の動画
 * @param isPlaying        再生中かどうか
 * @param onReturnToPlayer プレーヤー画面に戻るコールバック
 * @param onTogglePlay     再生/一時停止切り替えコールバック
 * @param onClosePlayer    左下ミニプレイヤーを閉じる/再生停止コールバック
 * @param onVideoSelected  動画タップ時コールバック
 * @param onVideoDeleted   動画削除完了時コールバック
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    player: Player? = null,
    currentVideo: VideoItem? = null,
    isPlaying: Boolean = false,
    onReturnToPlayer: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onClosePlayer: () -> Unit = {},
    onVideoSelected: (VideoItem) -> Unit,
    onVideoDeleted: (VideoItem) -> Unit = {}
) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 削除対象の保持 & 確認ダイアログ状態
    var showConfirmDialog by remember { mutableStateOf<VideoItem?>(null) }
    var pendingDeleteVideo by remember { mutableStateOf<VideoItem?>(null) }

    // Android 10/11+ 用のシステム削除パーミッションランチャー
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteVideo?.let { deleted ->
                videos = videos.filter { it.id != deleted.id }
                onVideoDeleted(deleted)
            }
        }
        pendingDeleteVideo = null
    }

    // 動画ファイルの削除リクエスト
    fun requestDeleteVideo(video: VideoItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pendingDeleteVideo = video
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(video.uri)
                )
                deleteLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // 直接削除フォールバック
                try {
                    val count = context.contentResolver.delete(video.uri, null, null)
                    val fileDeleted = File(video.data).delete()
                    if (count > 0 || fileDeleted) {
                        videos = videos.filter { it.id != video.id }
                        onVideoDeleted(video)
                    }
                } catch (_: Exception) {}
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val count = context.contentResolver.delete(video.uri, null, null)
                if (count > 0) {
                    videos = videos.filter { it.id != video.id }
                    onVideoDeleted(video)
                }
            } catch (e: SecurityException) {
                val recoverable = e as? RecoverableSecurityException
                if (recoverable != null) {
                    pendingDeleteVideo = video
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build()
                    )
                }
            }
        } else {
            try {
                val count = context.contentResolver.delete(video.uri, null, null)
                val fileDeleted = File(video.data).delete()
                if (count > 0 || fileDeleted) {
                    videos = videos.filter { it.id != video.id }
                    onVideoDeleted(video)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        videos = loadVideos(context)
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "LocalPipper",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    },
                    actions = {
                        if (currentVideo != null) {
                            TextButton(
                                onClick = onReturnToPlayer,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = Color(0xFF00DDFF)
                                )
                            ) {
                                Text(
                                    "▶ 再生中画面へ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D0D1A),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF0D0D1A)
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("読み込み中...", color = Color.Gray)
                }
            } else if (videos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎬", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("動画が見つかりませんでした", color = Color.Gray, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    // 左下ミニプレイヤーと重ならないよう下部に広めのパディング
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (currentVideo != null) 140.dp else 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(videos, key = { it.id }) { video ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    // 左スワイプ検知 -> 削除確認ダイアログを表示
                                    showConfirmDialog = video
                                    false
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false, // 右スワイプは無効
                            enableDismissFromEndToStart = true,  // 左スワイプのみ有効
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFDC2626))
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "削除",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "🗑️",
                                            fontSize = 20.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            VideoListItem(
                                video = video,
                                isCurrentlyPlaying = currentVideo?.id == video.id,
                                onClick = { onVideoSelected(video) }
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 動画一覧画面の左下で再生継続するミニプレイヤー
        // ==========================================
        if (currentVideo != null && player != null) {
            FloatingVideoPlayer(
                player = player,
                video = currentVideo,
                isPlaying = isPlaying,
                onClick = onReturnToPlayer,
                onClose = onClosePlayer,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // 削除確認ダイアログ
        if (showConfirmDialog != null) {
            val targetVideo = showConfirmDialog!!
            AlertDialog(
                onDismissRequest = { showConfirmDialog = null },
                title = {
                    Text(
                        "動画の削除",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "「${targetVideo.title}」を削除しますか？\n端末のストレージから完全に削除されます。",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmDialog = null
                            requestDeleteVideo(targetVideo)
                        }
                    ) {
                        Text(
                            "削除する",
                            color = Color(0xFFFF5555),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = null }) {
                        Text("キャンセル", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E2E),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/**
 * 動画一覧画面の左下に浮遊してリアルタイム再生を継続するミニプレイヤー。
 */
@Composable
fun FloatingVideoPlayer(
    player: Player,
    video: VideoItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(start = 16.dp, bottom = 20.dp)
            .size(width = 180.dp, height = 101.dp) // 16:9 比率
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(onClick = onClick)
    ) {
        // 実際の映像を再生継続
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = player.isPlaying
                }
            },
            update = { view ->
                view.player = player
                view.useController = false
                view.keepScreenOn = player.isPlaying
            },
            modifier = Modifier.fillMaxSize()
        )

        // 外枠グラデーションボーダー（シアン系アクセント）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Transparent)
        )

        // 右上の閉じる(✕)ボタン
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            FilledIconButton(
                onClick = onClose,
                modifier = Modifier.size(26.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.75f)
                )
            ) {
                Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 下部の動画タイトルバー
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isPlaying) "▶ " else "⏸ ",
                    color = Color(0xFF00DDFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 動画リストの各項目。
 * サムネイルを従来の倍（幅240dp以上×高さ136dp以上の迫力カード型）に変更。
 */
@Composable
fun VideoListItem(
    video: VideoItem,
    isCurrentlyPlaying: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrentlyPlaying) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF261852), Color(0xFF18183E))
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF16163A), Color(0xFF13132E))
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        // --- サムネイル（従来の120x68の倍：幅いっぱい x 高さ140dp） ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            VideoThumbnail(
                uri = video.uri,
                modifier = Modifier.fillMaxSize()
            )

            // 再生中バッジ
            if (isCurrentlyPlaying) {
                Text(
                    text = "再生中 ▶",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            Color(0xFF00DDFF),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 再生時間バッジ
            if (video.duration > 0) {
                Text(
                    text = formatDuration(video.duration),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(
                            Color.Black.copy(alpha = 0.75f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 動画情報
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = video.title,
                color = if (isCurrentlyPlaying) Color(0xFF00DDFF) else Color.White,
                fontSize = 15.sp,
                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 ${formatDate(video.dateAdded)}",
                    color = Color(0xFF8888AA),
                    fontSize = 12.sp
                )
                Text(
                    text = "📁 " + video.folderPath.substringAfterLast("/", video.folderPath),
                    color = Color(0xFF6666AA),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
