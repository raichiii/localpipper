メモ: ユーザーのリクエストに応じて、アプリ起動時の動画自動再生を無効にしました。
- MainActivity.kt から `restoreAttempted` 変数と、最後に再生していた動画を復元して自動再生する LaunchedEffect (行146-195) を削除。
- これにより、アプリ起動時に動画一覧画面が表示され、動画を選択するまで再生が開始されなくなります。
- ただし、各動画の最後の再生位置を記憶し、同じ動画を再選択時に続きから再生する機能 (PlaybackPositionRepository) は変更しておらず、引き続き動作します。
- ビルドコマンド: ./gradlew assembleDebug (成功、警告ありだが無害)
- 生成APK: プロジェクトルートの LocalPipper.apk