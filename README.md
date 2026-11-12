# LAN Notification Bridge

One-way Android notification forwarding over the same Wi-Fi LAN.

- Install the same APK on both phones.
- Galaxy A10: open app → Start receiver → Show this phone's Wi-Fi IP.
- Artia: open app → enter Galaxy A10 IP → Save receiver address → Enable notification access.
- In Android's Notification access screen, enable LAN Notification Bridge.
- Leave the receiver service running on the Galaxy A10.

Receiver listens on TCP port 8765 and accepts `POST /notify` JSON payloads.
No account, cloud service, encryption, or authentication is used.

## Build

Requires JDK 17 and Android SDK platform 35.

```bash
gradle assembleDebug
```

APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## Limitations

- Failed sends are dropped; there is deliberately no polling or persistent retry queue.
- This first version forwards title/text/package only. It does not forward original icons, actions, inline replies, media, or dismissal state.
- Android may stop background services under aggressive OEM battery management. Set the receiver app to unrestricted battery use if the receiver stops listening while idle.
- Both phones must be on the same Wi-Fi subnet and client isolation must be disabled.
- This is unauthenticated HTTP. Any device with network access to the receiver's port 8765 can submit notifications.
- Notification contents are sensitive; use only on a trusted private Wi-Fi network.

## GitHub Actions build

The included workflow builds a debug APK automatically when this project is pushed to GitHub.
