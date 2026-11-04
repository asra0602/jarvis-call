# Jarvis Call — v1 (debug)

Aplikasi pendamping Android untuk Jarvis.

**Yang bisa dilakukan v1 ini (sengaja dibatasi, supaya bisa langsung dicoba):**
- Layar panggilan masuk full-screen ala telepon: nama "Jarvis", tombol Angkat / Tolak, dering, getar.
- Setelah Angkat: layar "dalam panggilan" dengan timer, tombol Mute / Speaker (visual + speakerphone), dan tombol Akhiri.
- Setelah panggilan berakhir / ditolak: bisa membuka layar Chat di dalam app.
- Layar Chat v1 adalah **chat lokal di app saja** — belum terhubung ke Muse. Pesan balasan di v1 hanya simulasi, untuk membuktikan alur UI-nya.
- Dua cara mengetes panggilan dari layar utama:
  1. **Simulasikan Panggilan (langsung)** — langsung membuka layar panggilan.
  2. **Tes Notifikasi Panggilan (full-screen)** — mengirim notifikasi prioritas tinggi dengan full-screen intent, seperti cara Android menampilkan panggilan masuk. Di HP yang mengizinkan, layar panggilan muncul sendiri meski layar terkunci; kalau tidak, ketuk notifikasinya.

**Yang BELUM ada di v1:**
- Trigger jarak jauh dari Muse (FCM / polling backend). Itu tahap berikutnya, butuh Firebase project dan backend chat.
- Chat Muse asli di dalam app.
- Suara / voice call beneran (mikrofon belum dipakai di v1).

## Cara install (begitu APK tersedia)

1. Buka tab **Releases** repo ini di HP, unduh APK debug terbaru.
2. Ketuk file APK-nya, izinkan "Install dari sumber tak dikenal" untuk browser/file manager itu sekali saja.
3. Buka app **Jarvis Call**, izinkan notifikasi.
4. Untuk layar panggilan full-screen: di pengaturan app, aktifkan "Tampilkan di atas aplikasi lain" / "Full-screen intent" bila diminta Android, dan matikan optimasi baterai untuk app ini supaya tidak dimatikan di background.
5. Ketuk **Tes Notifikasi Panggilan** untuk mencoba.

## Cara build

Build otomatis lewat GitHub Actions (`.github/workflows/android-debug.yml`) setiap push ke `main`:
`gradle assembleDebug` → APK di `app/build/outputs/apk/debug/app-debug.apk`, diunggah sebagai artifact Actions.

Build lokal (butuh Android SDK + JDK 17):
```
gradle assembleDebug
```

## Struktur

- `app/src/main/java/com/jarvis/call/MainActivity.java` — layar utama + tombol tes + izin notifikasi
- `app/src/main/java/com/jarvis/call/CallActivity.java` — layar panggilan masuk / dalam panggilan
- `app/src/main/java/com/jarvis/call/CallNotificationHelper.java` — notifikasi full-screen intent
- `app/src/main/java/com/jarvis/call/ChatActivity.java` — chat lokal v1

## Privasi

v1 tidak mengirim data apa pun ke server mana pun. Tidak ada token, nomor telepon, atau kunci di dalam kode ini.
