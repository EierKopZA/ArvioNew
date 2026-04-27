# ARVIO 1.9.9

## Major Changes
- Large Android TV smoothness pass across Home, Details, Watchlist, Collections, and Live TV, including focus-path fixes, smoother catalog/card navigation, reduced blinking, and less jank without lowering image/video quality or removing MP4 hero playback.
- Live TV/IPTV overhaul with faster full-channel loading for very large playlists, quicker EPG availability, improved channel/category focus behavior, favorite/recent startup handling, and fullscreen support for phone/tablet playback.
- Trakt fixes for Watchlist and Continue Watching: stricter ID-based matching, newest-added watchlist ordering, better cache invalidation, profile-scoped Trakt data, and cleaner handling of stale local cache.
- Cloud sync hardening for profiles, settings, subtitles, IPTV state, watchlist, catalogs, addons, local watched state, and force-sync fallback paths when Supabase schema/table access is stale.
- Profile isolation improvements so Trakt tokens, watch history, Continue Watching, settings, catalogs, and watchlist remain scoped per profile while shared addons/IPTV remain available where intended.
- Addon/source improvements, including broader CloudStream/community addon compatibility, HTTP scraper-pack manifest support, source discovery reliability fixes, and better source matching for movies/series.
- UI polish across hero metadata, clearlogos, metadata service logos, focus cropping, watchlist layout/loading state, profile creation/PIN handling, details spacing, and Top 10 row limits.
- Play Store build compliance pass: Play flavor disables self-update and CloudStream runtime while sideload builds keep updater/addon functionality.

## Contributors
Thank you to everyone who helped with this release, including:
- Sage Gavin Davids
- Himanth Reddy
- Eier Kop / EierkopZA
- chrishudson918
- mrtxiv
- And many more people who contributed smaller fixes, ideas, testing, and feedback. Thank you.

## Sources
- Metadata and discovery: TMDB, IMDb metadata/logo assets, Trakt.
- Sync/auth: Supabase and ARVIO Cloud.
- Playback/addons: IPTV M3U/Xtream/Stalker sources, Stremio-compatible addons, CloudStream/community HTTP sources.
- Smoothness references: Android TV device traces and NuvioTV public release/changelog notes for performance direction.

## Download
- AFTVnews / Downloader code: `9383706`
- Direct APK: `https://gitlab.com/arvio1/ARVIO/-/raw/main/releases/v1.9.9/ARVIO%20V1.9.9.apk?inline=false`
