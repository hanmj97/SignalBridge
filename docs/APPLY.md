# SignalBridge Motion V2.4 — Fade Up 450ms + Natural Stagger

This patch is for the existing **SignalBridge Motion V2.3 (400ms)** frontend.

Only `frontend/src/styles.css` is changed. `App.tsx`, API code, layout,
React components, database and server are not modified.

## Updated animation settings

- Page navigation Fade Up: **450ms** (`.view-motion`).
- Dashboard statistics: **65ms** start stagger; each card animates for 280ms.
- Dashboard main panels: roughly 65ms difference in start timing.
- Inner-page rows: **40ms** start stagger, capped for later rows.
- Item movement: gentle 14px upward, with easing.
- Accessibility: `prefers-reduced-motion: reduce` disables animations.

**Note:** the page Fade Up takes 450ms, while delayed child elements may
finish afterwards (~600–730ms). This is intentional: all child animations
cannot be spaced visibly apart while also fully finishing inside 450ms.

## Apply with PowerShell

```powershell
# Move to the SignalBridge project
Set-Location 'D:\Projects\SignalBridge'

# Backup your current motion CSS
Copy-Item '.\frontend\src\styles.css' '.\frontend\src\styles.css.bak-motion-v23' -Force

# Extract into a temporary folder
Expand-Archive -Path "$HOME\Downloads\SignalBridge-Motion-V2.4-450ms.zip" -DestinationPath "$HOME\Downloads\SignalBridge-Motion-V2.4-450ms" -Force

# Replace only the CSS file; API and React files are untouched
Copy-Item "$HOME\Downloads\SignalBridge-Motion-V2.4-450ms\frontend\src\styles.css" '.\frontend\src\styles.css' -Force

# Verify that the new 450ms transition is present in your file
Select-String -Path '.\frontend\src\styles.css' -Pattern 'animation: sb-fade-up 450ms'

# This compose setup bind-mounts ./frontend so file edits generally reload
# automatically; if not, recreate only the web service, not the database/API.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml up -d --no-deps --force-recreate web

# Check frontend logs for startup problems
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml logs --tail=30 web
```

Visit `http://localhost:5173`, refresh with `Ctrl + Shift + R`, then navigate
Dashboard → Release list → History → Sources → System status.

## Revert

```powershell
# Restore just the original CSS
Copy-Item '.\frontend\src\styles.css.bak-motion-v23' '.\frontend\src\styles.css' -Force
```
