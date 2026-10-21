# MD3 Agent as a system app (KernelSU / Magisk module)

Mounts MD3 Agent into `/system/priv-app`, so it runs as a privileged system app
on a rooted phone. This only changes *where* the app lives; it does not keep the
accessibility service on. You still enable the service yourself in Settings, and
you can turn it off there at any time.

## Build

```sh
./gradlew assembleDebug           # or assembleRelease, then pass the apk path
tools/ksu-module/build.sh         # writes tools/ksu-module/md3agent-system.zip
```

## Install

- KernelSU / Magisk app → Modules → Install from storage → pick the zip → reboot.
- Or over adb with KernelSU: `adb push md3agent-system.zip /data/local/tmp/` then
  `adb shell su -c 'ksud module install /data/local/tmp/md3agent-system.zip'` and reboot.

A user-installed copy with the same signing key is upgraded cleanly; with a
different key, uninstall the user copy first.

## Notes

- The app declares no `signature|privileged` permissions, so no
  `privapp-permissions` whitelist is needed. If you later add one, drop an XML
  into `module/system/etc/permissions/` and rebuild.
- To remove: delete the module in the KernelSU/Magisk app and reboot.
