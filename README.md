# PocketBridge Android MVP

An Android app that exposes one folder explicitly selected by the device owner through Android's Storage Access Framework.

## Android privacy boundary
Android isolates apps from each other's private data. PocketBridge can manage the selected shared folder and its contents; it cannot read other apps' private/internal data.

## Build APK with GitHub Actions
1. Create a GitHub repository and upload this project.
2. Push to `main` or `master`, or run the workflow manually from **Actions**.
3. Download the `pocketbridge-debug-apk` artifact. It contains `app-debug.apk`.

Local build: JDK 17, Android SDK 35, then `gradle :app:assembleDebug`.

## Use
1. Install APK; choose a folder in Android's folder picker.
2. Tap Start file server and keep the foreground notification active.
3. Connect phone and PC to the same private VPN (for example Tailscale).
4. Use the phone's VPN IP and port `8765`. Every request needs `Authorization: Bearer YOUR_ACCESS_TOKEN`.

Do not port-forward or expose this HTTP service directly to the public internet. HTTP traffic should travel only inside a trusted encrypted VPN. The token is not a substitute for transport encryption.

## API
- `GET /api/status`
- `GET /api/files?path=`
- `GET /api/download?path=folder/file.ext`
- `DELETE /api/delete?path=folder/file.ext`
- `POST /api/mkdir?path=folder&name=NewFolder`
- `POST /api/rename?path=folder/old.ext&name=new.ext`
- `POST /api/upload?path=folder&name=file.ext` with raw file bytes in the request body.

Paths are relative to the selected folder. URL-encode query values.

## MVP limitations
Single user-selected folder; no PC app or cloud relay yet. Android battery management may affect long-running background operation. Test on a physical device before relying on it.
