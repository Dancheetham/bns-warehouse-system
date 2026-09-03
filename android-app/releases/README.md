# APK releases

Drop your built APK here as **`app-debug.apk`** (or edit the filename in
`HandheldLogin.tsx`'s download link if you rename it - e.g. for a signed
release build instead of a debug one).

This folder is bind-mounted straight into the frontend container
(see docker-compose.yml), so a new file here is available for download from
the handheld login page immediately - no rebuild, no restart, just copy the
file in.

This folder itself isn't tracked in git (see .gitignore) - the APK is a
build output, not source, same reasoning as node_modules or target/.
