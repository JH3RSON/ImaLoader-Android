<div align="center">

# ImaLoader

### High-Performance Open-Source Media Downloader for Android

<p align="center">
  <a href="https://github.com/JH3RSON/ImaLoader-Android">
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  </a>
  <a href="https://kotlinlang.org/">
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language" />
  </a>
  <a href="https://github.com/JH3RSON/ImaLoader-Android/releases">
    <img src="https://img.shields.io/badge/Release-v2.0-007ACC?style=for-the-badge&logo=github&logoColor=white" alt="Release" />
  </a>
  <a href="https://github.com/JH3RSON/ImaLoader-Android">
    <img src="https://img.shields.io/badge/Project-Non--Profit-00C853?style=for-the-badge" alt="Non-Profit" />
  </a>
</p>

<p align="center">
  <b>ImaLoader</b> is a lightweight, non-profit, open-source utility designed to fetch video and audio streams directly from major social platforms at their highest native fidelity.
</p>

---

</div>

## Platform Support Matrix

Official support is maintained and verified across the following networks:

| Service | Status | Video Quality | Audio Quality | Integration |
| :--- | :---: | :---: | :---: | :---: |
| <img src="https://img.shields.io/badge/YouTube-FF0000?style=flat-square&logo=youtube&logoColor=white" height="20" alt="YouTube" /> | **Active** | Max Available (up to 4K UHD) | AAC (128 kbps) | Native Extractor |
| <img src="https://img.shields.io/badge/TikTok-000000?style=flat-square&logo=tiktok&logoColor=white" height="20" alt="TikTok" /> | **Active** | Highest Available (No Watermark) | AAC (128 kbps) | Direct Stream |
| <img src="https://img.shields.io/badge/Instagram-E4405F?style=flat-square&logo=instagram&logoColor=white" height="20" alt="Instagram" /> | **Active** | Source Quality (Reels / Posts) | AAC (128 kbps) | Graph API / Parser |
| <img src="https://img.shields.io/badge/X%20(Twitter)-000000?style=flat-square&logo=x&logoColor=white" height="20" alt="X" /> | **Active** | Source Stream Bitrate | AAC (128 kbps) | Stream Resolver |
| <img src="https://img.shields.io/badge/Facebook-1877F2?style=flat-square&logo=facebook&logoColor=white" height="20" alt="Facebook" /> | **Active** | Max Available (HD / 1080p) | AAC (128 kbps) | Direct Extraction |

---

## Core Architecture & Features

```
+--------------------------------------------------------------------+
|                             ImaLoader                              |
+--------------------------------------------------------------------+
|  [ URL Input ] -> [ Stream Resolver ] -> [ Multi-Platform Parser ]  |
|                                                     |              |
|        +--------------------------------------------+              |
|        v                                            v              |
|  [ Video Pipeline (Max Source Quality) ]    [ Audio Pipeline (AAC) ]|
+--------------------------------------------------------------------+
```

- **Maximum Resolution Detection:** Automatically parses available video manifests to pull the highest resolution and frame-rate provided by the source platform.
- **Optimized Audio Processing:** Audio tracks are currently processed in standard **AAC format at 128 kbps**, balancing download speed, stability, and broad device compatibility.
- **Zero Monopolization:** ImaLoader is strictly open-source, non-profit, ad-free, and contains zero tracking or analytical telemetry.
- **Android Native:** Built entirely with modern Android development standards for optimal memory efficiency and fast background downloads.

---

## Development Roadmap

> [!NOTE]  
> **Active Focus: Audio Engine Overhaul**  
> We are actively engineering an upgraded audio extraction module to deliver higher bitrate profiles (including 256 kbps, 320 kbps, and lossless conversion options).  
> Upcoming milestones also include major architecture updates, enhanced batch downloading capabilities, and critical performance patches.

---

## Supporting the Project

ImaLoader is developed and maintained in free time as a public-service utility for the community. The application is completely free and open to everyone without restrictions.

If you find ImaLoader valuable for your day-to-day use and wish to support continuous maintenance, infrastructure testing, and future updates, voluntary contributions are deeply appreciated:

<div align="center">
  <br />
  <a href="https://paypal.me/TU_USUARIO_AQUI" target="_blank" rel="noopener noreferrer">
    <img src="https://raw.githubusercontent.com/stefan-niedermann/paypal-donate-button/master/paypal-donate-button.png" width="260" alt="Donate with PayPal" />
  </a>
  <br /><br />
  <sub>Every contribution directly funds testing hardware and continuous maintenance. Thank you for your support!</sub>
</div>

---

<div align="center">

Made with ❤️ by **IMA**

</div>
