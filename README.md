# Hidden Settings Injector (LSPosed / Xposed module)

設定アプリ (`com.android.settings`) の **開発者向けオプション** 画面と
**システム** 画面に、通常はメニューから到達できない隠しActivityへの
ショートカット項目を動的に追加する Xposed モジュールです。

- **対象 Android:** 9 (API 28)
- **フレームワーク:** LSPosed (Xposed API 82)
- **フック対象:** `com.android.settings` のみ
- **root:** Magisk (30.7 で確認想定)
- **作者:** Nyan<(Nyan4)
- **ライセンス:** MIT または Apache-2.0（デュアルライセンス）

> ⚠️ このモジュールは、自己所有・root/LSPosed 済み端末での
> システム内部構造の調査・研究を目的としています。追加される項目は
> **設定アプリに既に存在するActivity** を起動するだけで、システムの
> 挙動を書き換えたり、`com.android.settings` 以外をフックしたりはしません。

---

## 追加される項目

### 開発者向けオプション画面
| 表示名 | 起動先 |
|--------|--------|
| 上位互換システムUIデモモード | `com.android.systemui/.DemoMode` |
| WebView DevUI | `com.google.android.webview/org.chromium.android_webview.devui.MainActivity` |
| 画面ロックテスト | `com.android.settings/com.mediatek.settings.inputmethod.VowKeyguardConfirm` |
| Smart Call Forwarding | `com.android.settings/.Settings$SmartCallFwdActivity` |
| Advanced Calling | `com.android.settings/.Settings$AdvancedCallingOptionsActivity` |
| Wi-Fi Calling | `com.android.settings/.Settings$AdvancedWifiCallingActivity` |
| テスト中 | `com.android.settings/.Settings$TestingSettingsActivity` |

### システム画面
| 表示名 | 起動先 |
|--------|--------|
| 使用統計情報 | `com.android.settings/.UsageStatsActivity` |
| DRMリセット | `com.android.settings/.Settings$DrmResetActivity` |

存在しない・起動できないActivityをタップした場合は Toast でエラーを表示し、
**設定アプリはクラッシュしません**（全起動処理を try-catch でラップ）。

---

## 実装の要点

- `IXposedHookLoadPackage#handleLoadPackage` で `com.android.settings` だけを処理。
- Android 9 の以下 Fragment の `onActivityCreated(Bundle)` を
  `afterHookedMethod` でフック（AOSP `android-9.0.0_r46` で確認）：
  - `com.android.settings.development.DevelopmentSettingsDashboardFragment`
  - `com.android.settings.system.SystemDashboardFragment`
- この時点で `DashboardFragment` の `PreferenceScreen` は
  `onCreatePreferences` により構築済みなので、`getPreferenceScreen()` に
  自作 `Preference` を `addPreference` で追加します。
- AndroidX の `androidx.preference.Preference` はホスト（設定アプリ）の
  ClassLoader からリフレクションで解決し、`OnPreferenceClickListener` は
  動的プロキシで実装しています（モジュール側で AndroidX を同梱しない）。
- 回転などによる再入で二重追加しないよう、`PreferenceScreen` に付与した
  追加フィールドでガードしています。

---

## ビルド手順

### 必要環境
- Android Studio (Giraffe 以降) もしくは CLI の Android SDK
- JDK 11+
- `compileSdk` / `targetSdk` = 28（`minSdk` = 28）
- Xposed API はネットワーク経由 (`https://api.xposed.info/`) から取得
  （`compileOnly`。APK には同梱されません）

### Android Studio
1. 本ディレクトリを Android Studio で開く。
2. Gradle 同期が完了したら **Build > Build Bundle(s) / APK(s) > Build APK(s)**。
3. 生成物: `app/build/outputs/apk/debug/app-debug.apk`

### コマンドライン
```bash
# Gradle wrapper がある場合
./gradlew :app:assembleDebug

# 手元に gradle があれば
gradle :app:assembleDebug
```
> `gradle-wrapper.jar` を同梱していないため、初回は
> `gradle wrapper --gradle-version 7.5` を一度実行して wrapper を生成するか、
> ローカルの `gradle` を直接使ってください。

生成された `app-debug.apk` を端末に転送します。

---

## インストール & 有効化

1. APK を端末にインストール（`adb install app-debug.apk`）。
2. **LSPosed マネージャ** を開く。
3. 「モジュール」から **Hidden Settings Injector** を有効化。
4. スコープで **設定 (`com.android.settings`)** にチェック
   （`xposedscope` により初期選択済み）。
5. **設定アプリを強制停止**（または端末を再起動）してフックを反映。
6. 設定 → システム → 開発者向けオプション / システム 画面を開くと
   追加項目が表示されます。

### うまく動かないときの確認
- LSPosed のログ (`Hidden Settings Injector` タグ) を確認。
- MediaTek 固有クラス（`VowKeyguardConfirm` 等）は ROM により
  存在しない場合があります。その際はタップ時に Toast が出ます（正常）。
- Fragment クラス名が ROM 独自に変更されている場合はフックされません。
  ログの `could not hook ...` を確認してください。

---

## プロジェクト構成
```
.
├── build.gradle                # ルート
├── settings.gradle             # api.xposed.info リポジトリ設定含む
├── gradle.properties
├── app/
│   ├── build.gradle            # compileOnly Xposed API 82 / SDK 28
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml # Xposed メタデータ
│       ├── assets/xposed_init  # エントリクラス宣言
│       ├── res/values/arrays.xml # xposedscope (com.android.settings)
│       └── java/com/nyan4/hiddensettings/
│           └── HiddenSettingsInjector.java
├── LICENSE-MIT
└── LICENSE-APACHE
```

---

## ライセンス
MIT または Apache-2.0 のいずれかを選択可能（デュアルライセンス）。
Copyright (c) 2026 Nyan<(Nyan4)
