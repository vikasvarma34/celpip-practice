# CELPIP macOS Shortcuts

The Shortcuts CLI supports run/list/view/sign, not creating shortcut actions. These four shortcuts are prepared for manual creation; they are not installed automatically. No existing shortcuts or Chrome bookmarks were changed.

In Shortcuts, create each named shortcut, add **Run Shell Script**, choose **/bin/bash**, and paste its command below. If macOS requests permission to run scripts, allow it for these shortcuts. In Shortcuts Settings → Advanced, enable Allow Running Scripts if required. No Terminal window needs to remain open. Add a Show Alert action on errors if desired; scripts return meaningful errors and log locations.

| Shortcut name | Run Shell Script command |
|---|---|
| CELPIP Start Server | `/bin/bash "/Users/vikasvarmapokala/Repositories/celpip-practice/scripts/start-local.sh"` |
| CELPIP Stop Server | `/bin/bash "/Users/vikasvarmapokala/Repositories/celpip-practice/scripts/stop-local.sh"` |
| CELPIP Restart Server | `/bin/bash "/Users/vikasvarmapokala/Repositories/celpip-practice/scripts/restart-local.sh"` |
| CELPIP Practice Files | `/bin/bash "/Users/vikasvarmapokala/Repositories/celpip-practice/scripts/practice-files.sh"` |

Start checks owned process identity, both application markers, and backend health. Healthy processes are reused. Missing/unhealthy owned services alone are started/replaced; no build unless the backend artifact or frontend dependencies are missing. To rebuild updated source deliberately, use Restart. Both scripts serialize changes with the same lock and reject unfamiliar port listeners. Restart still performs the existing full builds (including backend tests), waits for readiness and opens Chrome. Stop retains the existing graceful ownership-checked shutdown.

Ports: backend **127.0.0.1:47321**, frontend **127.0.0.1:47322**. Prerequisites: macOS, Java 21, Node/npm, wrapper dependencies, lsof/curl, Chrome, writable local data. FFmpeg is required for AAC conversion. Logs: repository `.local/backend.log` and `.local/frontend.log`. Custom backend configuration must be supplied through the same environment/configuration when starting/restarting; Practice Files reads the running backend's process lock path, or its last successful resolved-root log when stopped. It never guesses a new data directory or creates/migrates preparation files.

## Chrome bookmarks

Import `docs/CELPIP-bookmarks.html` using Chrome's bookmark HTML import, or create a **CELPIP** folder and add these URLs manually. Import adds bookmarks; do not replace the Chrome Bookmarks file. Chrome may ask to open Shortcuts when clicked. Create the four correctly named shortcuts first.

- `shortcuts://run-shortcut?name=CELPIP%20Start%20Server`
- `shortcuts://run-shortcut?name=CELPIP%20Stop%20Server`
- `shortcuts://run-shortcut?name=CELPIP%20Restart%20Server`
- `shortcuts://run-shortcut?name=CELPIP%20Practice%20Files`

Shortcut execution permissions, Chrome external-protocol prompts and actual launches from Chrome await user verification. No browser automation was performed.

Bookmark artwork: the import HTML embeds four transparent 32px navy icons (#243B60): play, stop, restart arrow and folder. Editable generation source and PNGs are in docs/bookmark-icons/. Chromium supports PNG data-URL ICON attributes in bookmark imports, but appearance for shortcuts:// URLs must be verified after import. Existing installed bookmarks are not modified by editing this file. Import the updated HTML into Chrome; it may create another CELPIP/imported folder rather than update existing entries. Keep the preferred set manually; nothing is automatically removed. No extension required.
