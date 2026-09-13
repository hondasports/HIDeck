# Pixel 3 と Magisk の手順

この手順は、監査、パッチ、flash の 3 段階に分けています。Pixel 3 は A/B 端末で `boot` パーティションもスロットごとに分かれているため、別の日付の LineageOS 用 boot image を使うと soft-brick する可能性があります。

## 1. 最初に監査する

監査はワイヤレス ADB エンドポイントを受け付けます。あとで USB ポートを HIDeck が使うときも、端末を操作できるようにするためです。

```powershell
.\scripts\pixel3-device-audit.ps1 -Serial 192.168.10.121:5555
```

生成された監査ディレクトリは保存してください。そこには `getprop.txt`、現在のスロット、fastboot 変数が含まれます。スクリプトが `unauthorized` と報告した場合は、端末のロックを解除して USB デバッグの RSA 確認ダイアログを許可してから、もう一度実行してください。

イメージは次の値すべてに一致している必要があります。

* `ro.product.device=blueline`
* インストール済み LineageOS のブランチとビルド日
* その LineageOS ブランチが要求する現在のファームウェア
* 現在アクティブな A/B スロット（`_a` または `_b`）

公式 LineageOS のダウンロードページには、各署名済みビルドと一緒に `boot.img` が掲載されています。監査で確認したビルドと完全に一致するものをダウンロードして、SHA-256 を検証してください。別の日付の recovery image は使用しないでください。

## 2. Magisk でパッチする

公式 Magisk APK を端末にインストールし、正しい `boot.img` を `/sdcard/Download/` にコピーします。Magisk で **インストール → パッチするファイルを選択** を選び、そのファイルを指定してパッチが終わるまで待ちます。生成された `magisk_patched*.img` をこの PC に取り出してください。

```powershell
adb -s 192.168.10.121:5555 pull /sdcard/Download/magisk_patched*.img .\work\
```

元のイメージとパッチ済みイメージの SHA-256 を両方記録してください。変更していない元イメージは復旧用に必ず保存しておいてください。

## 3. fastboot の状態を確認してから flash する

bootloader を起動し、シリアルと現在のスロットをもう一度確認してください。

```powershell
adb -s 192.168.10.121:5555 reboot bootloader
fastboot devices
fastboot getvar product 2>&1
fastboot getvar current-slot 2>&1
fastboot getvar unlocked 2>&1
```

product は `blueline`、bootloader は unlocked であり、監査で記録したスロットが引き続きアクティブである必要があります。そのうえで、次のガード付きヘルパーを使用してください。

```powershell
.\scripts\magisk-pixel3.ps1 `
  -Serial 192.168.10.121:5555 `
  -PatchedBoot .\work\magisk_patched.img `
  -OriginalBoot .\work\lineage-exact-boot.img `
  -ConfirmFlash
```

このヘルパーは、product の誤り、unlock 状態の不足、スロット不一致、元イメージの欠落がある場合は処理を拒否します。再起動後は Wi-Fi 経由で ADB に再接続（`adb connect 192.168.10.121:5555`）して、`adb shell su -c id` を確認し、Magisk を開いて HIDeck の root チェックを実行してください。うまくいかない場合は、保存しておいた変更前のイメージを、対応するスロットへ fastboot で起動してから次の確認に進めてください。
