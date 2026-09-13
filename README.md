# HIDeck

HIDeck は、root 化した Android 端末を、接続方式を切り替えられる小型の入力デッキに変えるアプリです。現在の対象は、LineageOS を搭載した Pixel 3（`blueline`）です。

アプリは、互いに独立した 2 種類の Transport（通信方式）を提供します。

* **USB**: Linux ConfigFS の複合 USB Gadget として、キーボード、相対移動マウス、専用の 128 MiB マスストレージイメージを公開します。
* **Bluetooth**: Android の公開 `BluetoothHidDevice` プロファイルで、キーボードとマウスをレポート ID 付きで公開します。

UI には、IME 経由のテキスト入力欄、タッチパッド操作、マウスボタンとホイール、特殊キー、Windows/Mac の IME 切り替えボタン、保存可能なマクロを用意しています。USB Gadget の構成変更には root 権限が必要です。また、USB Gadget を HIDeck が使用している間は USB ケーブル上の ADB が切断されるため、USB モードは明示的に開始した場合だけ有効になります。

## ビルド

このリポジトリには Gradle Wrapper が含まれています。Android SDK Platform 36 と Build Tools 36 をインストールした環境で、次を実行してください。

```powershell
.\gradlew.bat assembleDebug
```

デバッグ APK は `app/build/outputs/apk/debug/app-debug.apk` に出力されます。

スマートフォンと PC が同じ Wi-Fi に接続されている場合は、USB Gadget のテストでデバッグ経路まで奪われないよう、開発用 ADB を USB ケーブルから Wi-Fi に切り替えておいてください。

```powershell
adb tcpip 5555
adb connect <phone-ip>:5555
adb disconnect <usb-serial>
```

## インストールと初回起動

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Bluetooth モードを使用する場合は、まず HIDeck を起動して、要求された Bluetooth 権限を許可してください。PC 側に残っている古いスマートフォンのペアリングを削除し、HIDeck が登録済みの状態で PC とスマートフォンをもう一度ペアリングします。通常、Windows の Bluetooth 設定から PC が HID 接続を開始します。アプリは HID Device プロファイルを登録したままにして、ペアリング済みの PC からの接続を受け付けます。

USB モードには、動作する `su` プロバイダー（Magisk）と、`/config/usb_gadget` および UDC を公開するカーネルが必要です。

USB モードでは root 所有の `/data/adb/hideck-storage.img` を公開します。このイメージは Android のストレージとは意図的に分離しています。ホストに初めて表示されたら、ホスト側でフォーマットしてください。USB モード中は Android 側からこのイメージをマウントしたり変更したりしないでください。HIDeck が公開中ずっと排他ロックを保持します。

## Pixel 3 / LineageOS のセットアップ

boot パーティションを変更する前に、読み取り専用の監査を実行してください。

```powershell
.\scripts\pixel3-device-audit.ps1 -Serial 192.168.10.121:5555
```

このスクリプトは、端末コードネーム、LineageOS のビルド、アクティブスロット、Verified Boot の状態、bootloader 変数、`getprop` のコピーを記録します。ADB が承認済みで、端末が `blueline` と識別される場合だけ続行します。

Magisk の手順は [docs/pixel3-magisk.md](docs/pixel3-magisk.md) にまとめてあり、`scripts/magisk-pixel3.ps1` で一部を自動化しています。現在インストールされている LineageOS のビルドと完全に一致する boot image が必要です。スクリプトは適当なイメージを選ばず、`-ConfirmFlash` がない限り flash も実行しません。

## 制限事項

* USB HID と ConfigFS の対応状況は LineageOS のカーネルに依存します。root 化済みでも `hidg` や利用可能な UDC がない端末では動作しません。
* **Windows IME** ボタンは、101/102 キーボード向けの Microsoft 日本語 IME ショートカットである Alt+Backquote を送信します。エンコーダーは、日本語キーボード専用キーに割り当てられているホスト向けに、日本語 Zenkaku/Hankaku（LANG5）の Usage も出力します。ホストのキーボードレイアウトや変更済みショートカットによって解釈が変わる場合があります。
* Android の Bluetooth HID Device プロファイルは非同期で動作します。Windows が HID ホストになり、HIDeck が SDP レコードを登録したあとでホスト側の接続が始まるため、古いペアリングを削除してから再ペアリングが必要になる場合があります。ホスト側からの接続に失敗すると、ペアリング済みでも切断状態が残ることがあります。その場合は **切断** をタップして HIDeck を閉じて再度開き、PC の Bluetooth 設定から再試行してください。
* Android の USB Gadget を置き換えるため、USB モード中は ADB が無効になります。**切断** をタップすると、保存していた `sys.usb.config` を復元します。USB モード中にアプリを強制停止した場合は、USB を抜き差しするか再起動して Android の通常の Gadget を復元してください。

## ライセンス

Apache-2.0。詳細は [LICENSE](LICENSE) を確認してください。
