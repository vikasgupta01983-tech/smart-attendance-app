# Smart Student Attendance - Android app (WebView wrapper)

This is a separate folder. It does NOT modify your Flask project.
The app shows your Flask website inside a WebView, adds a "Scan QR" button
(native camera scanner) and a "Server" button to set the Flask address.

Build without installing anything: push this folder to GitHub, open the
Actions tab -> "Build Android APK" -> download the artifact.

Your own icon:  pip install pillow ; python tools/make_icons.py my_logo.png
Flask change:   see flask_patch/app_py_change.txt
