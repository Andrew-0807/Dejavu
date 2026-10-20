# Settings redesign (round 4, Vivaldi model)

Round 3 grouped the root rows into 9 categories, but a category still showed 2 or 3 rows that each opened another
screen. Round 4 puts the real settings of every category on the settings page itself, like Vivaldi's desktop settings.

## Layout

1. Left: the settings home (search field, then the 9 categories with icon and summary). Kept from round 3.
2. Wide window (more than 600dp): the categories stay on the left, the right side is ONE list of every category, one
   after another, each under a big heading and split into sections with accent headers. Tapping a category jumps the
   list to its heading. Scrolling highlights the category at the top of the list, or the last one at the end (scroll
   spy).
3. Phone: the category list is the entry point. A category opens one page with all of its settings inline, back
   returns to the list. A single long page with a chip row was rejected: about 150 rows in one scroll is hard to use
   on a phone, and the category list is the part the user liked.
4. Rows that open a screen show their current value as a summary. Nothing is more than one level deep.

## How it is built

`SettingsFragment` stays the one `PreferenceFragmentCompat`. `res/xml/preferences.xml` holds the 9 top level
categories (big headings, `dejavu_preference_category_heading.xml`), the rows that open screens (first in each
category), and anchor categories. An anchor reuses the key of the row it replaced (`pref_key_home`, `pref_key_tabs`,
...), so old search results and recent searches still land on it; link sharing has its own anchor key because its
switch already uses the row key. At creation `SettingsSection.kt` adds the preference XML of each inlined screen (`addPreferencesFromResource`) and moves it to
its anchor: titled categories of that XML become sections after the anchor, other preferences go into the anchor,
whose title is the section header. Each XML stays the only definition of its keys, so the standalone screens, the
settings search index and the keys are unchanged.

The logic of each inlined screen moved out of its fragment into a `SettingsSection` class next to it (same file). The
standalone fragment and `SettingsFragment` both run the same section, so listeners, visibility rules, summaries,
Nimbus gates and navigation are written once. The standalone screens stay because menus, panels, deep links and
`GlobalDirections` open them directly.

Navigation used by a section works from both hosts: `settingsFragment` declares the same action ids as the original
screen (tracking protection exceptions and blocking info, search engine list and shortcuts). The search engine list,
search shortcuts and save engine screens moved out of the nested `search_engine_graph` so they can be reached from
`settingsFragment`.

Accessibility settings are stored in `fenix_preferences`, not in the default file the settings page uses, so its
inlined preferences get a `PreferenceDataStore` on that file.

Sleeping tabs, links from other apps and HTTPS-Only are Compose (`InlineComposePreference`), shared with their own
screens (`TabSleepSettings`, `ExternalLinksSettings`) or new (`HttpsOnlySettings`, same preferences as
`HttpsOnlyFragment`).

Sections whose preferences are all hidden (for example Firefox's homepage switches while Dejavu's home is on) are
hidden with their header.

## Categories

Inline = settings shown on the page. Screen = a row that opens a screen.

| Category | Inline | Screen |
|---|---|---|
| Account and sync | | sign in, account, account problem |
| Look and layout | theme, toolbar position, toolbar size, screen (system bars, swipe down full screen), opening screen (Firefox homepage switches when Dejavu's home is off), accessibility (automatic font size, font size, zoom) | language, actions bar (more menu editor) |
| Tabs and sidebar | tab bar and sidebar switches, tab bar position, close unpinned tabs, sleeping tabs | tab actions (editor), containers (list) |
| Gestures and input | pull to refresh, hide toolbar on scroll, toolbar swipes, swipe, double tap and long press gestures, mouse support | keyboard shortcuts (remapping) |
| Search and links | default browser, search engine options, suggestions, address bar, open links in apps, links from other apps (private, workspace, container), link sharing (Nimbus) | default search engine (engine list), search shortcuts |
| Privacy and security | tracking protection (switch, standard, strict, custom and its options, global privacy control), HTTPS-Only, private browsing (shortcut, screenshots, lock), delete browsing data on quit | passwords, payment methods and addresses, site permissions, delete browsing data, email masks, IP protection, DNS over HTTPS (provider and exception list), notifications (system), tracking protection exceptions and mode details |
| Downloads and extensions | downloads (app for downloads, location, delete behaviour) | extensions, extension collection and local extension (secret menu only) |
| Advanced | remote debugging | translations, page summaries, AI controls, import bookmarks, local network access. Gecko logs, LeakCanary and sync debug, shown only with the secret menu (or debug builds for LeakCanary) |
| About Dejavu | | what's new, author, coffee, rate, about; secret settings, Nimbus and profiler with the secret menu |

Not inlined, with the reason:

1. DNS over HTTPS: a Compose screen with its own navigation, a custom provider dialog and an exception list.
2. Data choices, Firefox Labs, remote improvements: hidden in Dejavu, unchanged.
3. Translations, page summaries, AI controls: Compose screens with download managers or per feature lists.

## Scrolling

A tapped category scrolls the one list so its heading is at the top, and the scroll spy pauses until the user drags
the list. Otherwise the category of the first row shown is highlighted, or of the last row once the list cannot scroll
further (`spiedGroup`). Both lists have bottom padding so the last row clears the screen edge.

## Search

The settings finder keeps indexing every XML. Results from inlined XMLs now open `settingsFragment`, which shows the
category of the key (phone) or jumps to it (wide window), scrolls to the setting and flashes it. Sleeping tabs and
links from other apps are found through `DejavuSettingsSearchProvider` and open inline too. It also indexes the titles
of Home, Accessibility, HTTPS-Only, Private browsing and Downloads, which were only findable as their old rows. Default
search engine results open the engine list.
