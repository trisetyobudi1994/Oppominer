# OPPO A5 Bitcoin Miner (ARM64 C++ NDK + Stratum V1)

Project Android Studio siap build menjadi file APK untuk perangkat OPPO A5 (Snapdragon 665 / Helio P35) atau perangkat ARM64 lainnya.

---

## 🚀 3 Cara Menghasilkan File APK

### Cara 1: Menggunakan GitHub Actions (Paling Mudah, Tanpa Install Android Studio)
1. Buat repository baru di akun GitHub Anda (misal: `oppo-a5-miner`).
2. Upload seluruh file dari file ZIP ini ke repository GitHub Anda.
3. Buka tab **Actions** di GitHub. Workflow `Build OPPO A5 Mining APK` akan otomatis berjalan dan meng-compile source C++ NDK menjadi file APK!
4. Klik workflow yang selesai, lalu download file `oppo-a5-bitcoin-miner-arm64-apk.zip` yang berisi file `app-release.apk`.
5. Install langsung ke HP OPPO A5 Anda!

### Cara 2: Menggunakan Android Studio di PC / Laptop
1. Download dan ekstrak ZIP project ini.
2. Buka **Android Studio** -> Pilih **Open** -> Arahkan ke folder hasil ekstrak.
3. Pastikan **NDK (Side by side)** dan **CMake** terinstall di SDK Manager (Tools -> SDK Manager -> SDK Tools).
4. Tunggu Gradle Sync selesai.
5. Klik menu **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
6. File APK akan tersimpan di: `app/build/outputs/apk/release/app-release-unsigned.apk`.

### Cara 3: Menggunakan Terminal / Command Line
```bash
chmod +x gradlew
./gradlew assembleRelease
```

---

## ⚙️ Spesifikasi & Fitur Utama

1. **C++ NDK Native Engine (SHA-256d)**:
   - Menggunakan instruksi ARMv8-A dengan kompilasi Clang `-O3 -flto -march=armv8-a+crypto`.
   - Midstate precalculation untuk menghemat 50% komputasi hash pada blok 80-byte.
2. **Konfigurasi 6 Thread**:
   - Khusus OPPO A5 2020 (4x Kryo 260 Gold + 4x Kryo 260 Silver): memakai 4 Gold + 2 Silver cores, meninggalkan 2 core Silver untuk sistem Android agar HP tidak lemot/hang.
3. **Stratum V1 Client**:
   - Protokol standar JSON-RPC TCP: `mining.subscribe`, `mining.authorize`, `mining.notify`, `mining.submit`.
   - Payout langsung ke alamat BTC Anda tanpa memerlukan private key.
4. **Foreground Service & Screen-off**:
   - Memakai `PARTIAL_WAKE_LOCK` agar CPU tetap menambang saat layar HP dimatikan.
5. **Proteksi Baterai & Suhu (Thermal Safety)**:
   - Otomatis pause jika suhu baterai mencapai 43°C dan resume saat dingin.
   - Opsi hanya menambang saat charger tercolok (AC Power).
