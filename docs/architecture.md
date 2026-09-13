# HIDeck のアーキテクチャ

```text
MainActivity
   |
HidDeckController -------- KeyboardReportEncoder / MouseReportEncoder
   |                                  |
HidTransport                         report bytes
   |------------------|
UsbHidTransport   BluetoothHidTransport
   |                  |
UsbGadgetController  BluetoothHidDevice
   |
ConfigFS: hid.usb0 + hid.usb1 + mass_storage.0
   |
StorageCoordinator (/data/adb image + app lock file)
```

`HidTransport` は意図的に小さなインターフェースにしています。コントローラーは、キーボードのテキストとキー入力を 1 本のキューで順番に処理します。USB マウスの移動は、上限付きで最新値にまとめるキューを使用するため、タッチパッドを素早くドラッグしても反応が遅れにくく、指を離したあとに古い移動レポートを送信しません。クリックとホイールのレポートは順番を維持します。

USB Transport は `hideck` という名前の独立した ConfigFS Gadget を作成します。`hid.usb0` にキーボードのレポートディスクリプター、`hid.usb1` にマウスのディスクリプターを設定し、`mass_storage.0/lun.0/file` から root 所有の `/data/adb/hideck-storage.img` を参照します。開始時に既存の `sys.usb.config` を保存して Android の Gadget を切り離し、切断時に保存値を復元します。アプリのプライベートディレクトリにあるロックファイルでイメージへのアクセスを直列化し、Gadget を解体するまでロックを保持します。

`StorageCoordinator` は、イメージの横に置く助言ロック（advisory lock）を管理します。今後 Android 側のファイルブラウザーやマウント機能を追加する場合も、イメージに触る前にこのロックを取得する必要があります。マスストレージ機能がリンクされている間は、ホスト側だけが書き込みを行います。

Bluetooth はキーボードにレポート ID 1、マウスにレポート ID 2 を使用します。HIDeck は公開されている `BluetoothHidDevice` プロファイルを、キーボードとマウスをまとめた SDP レコードとして登録します。ホストはシステム設定でペアリング済みにしておく必要があります。Windows では、HIDeck を開いた状態でペアリングを更新すると、通常はホスト側から HID 接続を開始します。
