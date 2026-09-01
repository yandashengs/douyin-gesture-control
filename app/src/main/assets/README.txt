此目录用于存放 MediaPipe HandLandmarker 模型文件。

==========================================
如何获取 hand_landmarker.task
==========================================

方法 1（推荐）：运行项目根目录的下载脚本
  - 双击 download_model.bat
  - 或在 PowerShell 中执行：
      powershell -ExecutionPolicy Bypass -File download_model.ps1

方法 2：手动下载
  - 浏览器打开：
      https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
  - 下载后重命名为 hand_landmarker.task
  - 放到本目录（app/src/main/assets/）

注意：
  - 国内网络访问 storage.googleapis.com 通常需要代理
  - 文件约 7-8 MB
  - 若下载失败可使用镜像或代理后重试
  - 文件就位后即可在 Android Studio 中 Run 项目
