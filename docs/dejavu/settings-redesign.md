# Settings redesign

The root settings screen becomes a Compose home: a search field (opens the settings finder) and category cards with
an icon and a one line summary. On a phone a category opens in place and back returns to the home. On a window wider
than 600dp the categories sit on the left and the selected category on the right.

A category is a top level `PreferenceCategory` of `res/xml/preferences.xml`, so every root preference keeps its key,
title, click handling and destination. Only the grouping changes.

## Groups

| Group | Key | Root preferences |
|---|---|---|
| Account and sync | `pref_key_dejavu_group_account` | sign in, account category, sync debug |
| Look and layout | `pref_key_dejavu_group_look` | customize (look section), home, accessibility, language |
| Tabs and sidebar | `pref_key_dejavu_group_tabs` | tabs, tab bar and sidebar (new, tabs section of Customize), tab actions, tab sleep, containers |
| Gestures and input | `pref_key_dejavu_group_input` | gestures and mouse (new, input section of Customize), keyboard shortcuts (moved from Customize) |
| Search and links | `pref_key_dejavu_group_search` | search, default browser, links from other apps, open links in apps, link sharing |
| Privacy and security | `pref_key_privacy_security_category` (kept) | private browsing, IP protection, HTTPS only, DoH, tracking protection, site permissions, delete data, delete on quit, passwords, email masks, payment methods, notifications, data choices |
| Downloads and extensions | `pref_key_dejavu_group_downloads` | downloads, extensions, install local extension, extension collection |
| Advanced | `pref_key_advanced` (kept) | translations, page summaries, AI controls, import bookmarks, Firefox Labs, LeakCanary, remote improvements, remote debugging, Gecko logs, local network access |
| About Dejavu | `pref_key_dejavu_group_about` | Dejavu category (what's new, author, coffee), rate, about, secret settings, Nimbus, profiler |

## Customize split

`CustomizationFragment` takes a `section` argument and shows only that section's categories. Without it (settings
finder results) it shows everything, so no result is lost.

1. `look`: app icon, theme, toolbar, toolbar size, actions bar, screen (system bars, immersive), toolbar layout, toolbar shortcut.
2. `tabs`: tab strip and sidebar, tab strip location.
3. `input`: gestures (toolbar gestures, mouse support).

Keyboard shortcuts moved from `customization_preferences.xml` to the input group of `preferences.xml` with the same key
and title.
