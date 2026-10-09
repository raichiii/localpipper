# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 開発環境のセットアップ

このプロジェクトはAndroidアプリです。開発にはAndroid Studioが必要です。

1. Android Studioでプロジェクトを開く
2. 必要なSDKが自動でダウンロードされます（compileSdk 34, minSdk 26）
3. デバイスまたはエミュレータで実行

## よく使うコマンド

### ビルド

```bash
# デバッグビルド（APKがプロジェクトルートにコピーされます）
./gradlew assembleDebug

# リリースビルド
./gradlew assembleRelease

# クリーンビルド
./gradlew clean
```

### アプリのインストールと実行

```bash
# デバッグAPKを接続されたデバイスにインストールして実行
./gradlew installDebug

# または、Android StudioのRunボタンを使用
```

### APKの場所

- デバッグAPK: `LocalPipper.apk` (プロジェクトルートにコピーされます)
- リリースAPK: `app/build/outputs/apk/release/app-release.apk`

## プロジェクト構成とアーキテクチャ

### 概要

LocalPipperは、ローカル動画のバックグラウンド再生とピクチャー・イン・ピクチャー（PiP）に対応したAndroidアプリです。
Jetpack ComposeをUIに、Media3 (ExoPlayer)をメディア再生に使用しています。

### 主要コンポーネント

1. **MainActivity** (`app/src/main/java/com/localpipper/app/MainActivity.kt`)
   - アプリのエントリポイント
   - Media3 MediaSessionServiceとの連携
   - PiPモード、フルスクリーン、バックグラウンド再生の制御
   - 動画選択とフォルダー内の次の動画への自動遷移

2. **PlaybackService** (`app/src/main/java/com/localpipper/app/PlaybackService.kt`)
   - バックグラウンド再生を担うMediaSessionService
   - ExoPlayerをラップし、MediaSession経由で再生制御と通知を管理
   - アプリがタスクから削除されたときに再生位置を保存

3. **PlaybackPositionRepository** (`app/src/main/java/com/localpipper/app/PlaybackPositionRepository.kt`)
   - SharedPreferencesを使用した動画の再生位置の保存・取得
   - シングルトンパターンで実装

4. **UI画面** (`app/src/main/java/com/localpipper/app/ui/`)
   - **VideoListScreen**: 動画一覧を表示し、サムネイル、タイトル、日付を表示
     - 左スワイプで削除確認ダイアログ
     - 動画タップでプレーヤー画面に遷移
     - 左下にミニプレイヤーを常駐表示（現在再生中の動画）
   - **PlayerScreen**: フルスクリーンプレーヤー（※実際のコードではMainActivity内で状態管理され、別ファイルとしては存在しないが、UIロジックはVideoListScreenと共有）
   - **VideoListItem**: 動画一覧の各項目（サムネイル、タイトル、日付、フォルダー名）
   - **VideoThumbnail**: 動画のサムネイルをロード
   - **FloatingVideoPlayer**: 動画一覧画面の左下に表示されるミニプレイヤー

5. **データモデル**
   - **VideoItem** (`ui/VideoListScreen.kt`): 動画のメタデータを保持するデータクラス
     - id, uri, title, duration, dateAdded, folderPath, data

### 主な機能の実装詳細

- **バックグラウンド再生**: `PlaybackService` がフォアグラウンドサービスとして動作し、ExoPlayerを保持
- **PiP (Picture-in-Picture)**: AndroidのPictureInPictureParamsとActivityのenterPictureInPictureModeを使用
- **再生位置の保存**: `PlaybackPositionRepository` がSharedPreferencesに動画URIをキーとして位置を保存
- **フォルダー内の次の動画**: `getNextVideoInFolder` 関数が同じフォルダー内の日付降順ソートから次の動画を取得
- **ストレージアクセス**: AndroidのStorage Access FrameworkとMediaStoreを使用してローカル動画にアクセス
- **権限処理**: ストレージ権限（READ_EXTERNAL_STORAGEまたはREAD_MEDIA_VIDEO）と通知権限（POST_NOTIFICATIONS）をランタイムでリクエスト

## ディレクトリ構成

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/localpipper/app/
│   │   │   ├── MainActivity.kt          # メインアクティビティ
│   │   │   ├── PlaybackService.kt       # バックグラウンド再生サービス
│   │   │   ├── PlaybackPositionRepository.kt # 再生位置リポジトリ
│   │   │   └── ui/
│   │   │       ├── VideoListScreen.kt   # 動画一覧画面と関連コンポーネント
│   │   │       └── Theme.kt             # Material3テーマ設定
│   │   └── res/                         # リソースファイル（レイアウトはComposeなので少ない）
│   └── test/                            # テストディレクトリ（現在未作成）
└── build.gradle.kts                     # アプリモジュールのGradle設定
build.gradle.kts                         # ルートプロジェクトのGradle設定
settings.gradle.kts                      # Gradle設定
LocalPipper.apk                          # デバッグビルド時に自動コピーされるAPK
LocalPipper_.apk                         # 署名済みAPK(?)
```

## 開発のヒント

1. **状態管理**: MainActivityでビデオプレーヤーの状態（MediaController）、現在のスクリーン状態（「list」または「player」）、現在のビデオ、PiP/フルスクリーンフラグを管理
2. **バックグラウンド通信**: ActivityとService間の通信はMediaController経由で行われる
3. **UI更新**: Jetpack Composeの状態（mutableStateOf、remember）を使用してUIを再構成
4. **コルーチン**: 非同期操作（動画リストのロード、フォルダー内の次の動画取得）にはDispatchers.IOを使用
5. **エラーハンドリング**: 権限リクエストやメディアロードの失敗については、適切にキャッシュしてユーザー体験を維持

## 注意事項

- このプロジェクトには現在ユニットテストまたはインストルメンテーテッドテストが設定されていません。
- ビルド後のAPKはプロジェクトルートに `LocalPipper.apk` としてコピーされます（build.gradle.ktsのタスクによる）。
- Android 13（APIレベル33）以降では通知権限が必要です。
- Android 10（APIレベル29）以降ではスコープストレージが適用され、MediaStoreを介したアクセスが必要です。