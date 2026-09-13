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

`HidTransport` は意図的に小さなインターフェースにしてる。コントローラーは、キーボードのテキストとキー入力を 1 本のキューで順番に処理する。USB マウスの移動は、上限付きで最新値にまとめるキューを使うから、タッチパッドを素早くドラッグしても反応が遅れにくいし、指を離したあとに古い移動レポートを送らへん。クリックとホイールのレポートは順番を維持するで。

USB Transport は `hideck` という名前の独立した ConfigFS Gadget を作る。`hid.usb0` にキーボードのレポートディスクリプター、`hid.usb1` にマウスのディスクリプターを設定し、`mass_storage.0/lun.0/file` から root 所有の `/data/adb/hideck-storage.img` を参照する。開始時に既存の `sys.usb.config` を保存して Android の Gadget を切り離し、切断時に保存値を復元する。アプリのプライベートディレクトリにあるロックファイルでイメージへのアクセスを直列化し、Gadget を解体するまでロックを保持する仕組みや。

`StorageCoordinator` は、イメージの横に置く助言ロック（advisory lock）を管理する。今後 Android 側のファイルブラウザーやマウント機能を追加する場合も、イメージに触る前にこのロックを取得せなあかん。マスストレージ機能がリンクされている間は、ホスト側だけが書き込みを行うで。

Bluetooth はキーボードにレポート ID 1、マウスにレポート ID 2 を使う。HIDeck は公開されている `BluetoothHidDevice` プロファイルを、キーボードとマウスをまとめた SDP レコードとして登録する。ホストはシステム設定でペアリング済みにしておく必要がある。Windows では、HIDeck を開いた状態でペアリングを更新すると、通常はホスト側から HID 接続を開始するで。
