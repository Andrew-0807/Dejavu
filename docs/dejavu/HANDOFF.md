# DejaVu handoff (2026-10-19)

## Layout

- Repo: `~/projects/dejavu-src` (sparse checkout of by-architect/Dejavu, a Fenix fork). Remotes: `origin` upstream, `fork` = `Andrew-0807/Dejavu`.
- The tablet cannot build (aarch64). Builds run on GitHub Actions: push `ci/build-apk` to `fork`. A run takes 25 to 45 min, artifact `dejavu-debug-apk`.
- `ci/build-apk` = `integration/all` + `.github/workflows/build-apk.yml` (artifact mode, `fetch-depth: 300`).
- Commit identity: `git -c user.name=Andrew-0807 -c user.email=andreeeeivlad0807@gmail.com`. No co-author lines.
- Install: `S=$(adbon)`, `gh run download <id> -R Andrew-0807/Dejavu -n dejavu-debug-apk -D $TMPDIR/x`, `adb -s $S install -r -g <arm64 apk>`. Package `org.mozilla.fenix.debug`. Open with `am start -n org.mozilla.fenix.debug/org.mozilla.fenix.HomeActivity` (the monkey launcher opens LeakCanary).
- Each CI build signs with a new random debug key, so install needs `adb uninstall org.mozilla.fenix.debug` first (R38).

## Done and built

- Upstream PR #186 (hide system bars) is open. 3 newer local commits on `feature/hide-system-bars` are NOT pushed. Push only after device testing.
- `integration/all` @ `004f6eb0`, CI run 39290750967 passed and is installed on the tablet. Contains:
  - Round 1: bars return on scroll up, swipe down immersive mode with floating button, compact bottom bar, vertical tab sidebar, toolbar size, split view.
  - Round 2: gestures (O19/O20), restore closed tab button, remappable keyboard shortcuts (O24), mouse support (O25), full page screenshot (O26), search keywords (O28). O27 downloads needed no code.

## Written, uncommitted, never compiled: `~/projects/dejavu-next` (branch `feature/next` from `004f6eb0`)

Staged plus unstaged changes, about 45 files. Contents:
1. Tablet sidebar shows the real home screen, on by default on tablets, Home button hidden while shown, tab search on top.
2. Sidebar pull handle (6x120dp, left edge), replaces the edge swipe so Android back works (R29 fixed). Setting "Show sidebar handle", hidden automatically in swipe down immersive. Applies live (R46 fixed).
3. Address bar keeps the URL selected. Tablet dropdown suggestions in browser and home. Enter loads in current tab, Shift+Enter in a new tab (real key flag, R41 fixed). Tests updated (R42).
4. Ctrl+T Zen style centered pill with suggestions. Settings finder by fuzzy match, prefix `o` (editable in Settings > Search > Default search engine). Default engine keywords `g` Google, `d` DuckDuckGo. DejaVu screens indexed.
5. Settings redesign: Compose home with 9 categories, two pane on tablets, Customize split into sections. Plan in `docs/dejavu/settings-redesign.md`. Phone header no longer repeats title (R57), category survives process death (R58).
6. Phone: compact bar claims swipe up early and follows the finger (I1). No placeholder toolbar on tab switch (I2).

## Stopped mid work (check these diffs first)

- Agent A (toolbar and input) was stopped while editing `browser/SwipeGestureLayout.kt` and `components/toolbar/gestures/ToolbarVerticalGesturesHandler.kt` for R50/R48. These may be half done. Review `git diff` of both files, finish or revert them. Also check whether its other targets landed: R49 (`TabPreview.kt` offset), R51 (double tap default "nothing" in `Settings.kt`), R36 (Ctrl+L on home in `KeyboardShortcuts.kt`), R44 (clear Shift flag in `BrowserToolbarSearchMiddleware.kt`).
- Agent B (round 2 risks) was stopped with no report. Its files show changes: `content.js` (R33), `DejavuMouse.kt` (R53), `DejavuSplitView.kt`, `FullPageScreenshot.kt`, `DejavuTabCommands.kt` (R31, R32, R34), `SearchKeywords.kt`, `BookmarksScreen.kt`, `SearchKeywordsTest.kt` (R54), `UseCases.kt`. R55 (force download folder picker on) unknown. Review each diff for completeness.

## Next steps

1. Review the two half edited files and agent B's diffs. Check for conflict markers and XML validity.
2. Commit `dejavu-next`, fast forward `integration/all`, merge into `ci/build-apk` (temp worktree with `.github` only sparse), push `fork ci/build-apk`.
3. If the build fails, read `gh run view <id> --log-failed`, grep `e: file://`, fix in place, rebuild.
4. Uninstall the old debug app, install, test on tablet.
5. After testing, decide on pushing the 3 commits to PR #186 and separate upstream PRs.

## Open risks

- R60 nothing in `dejavu-next` compiled. R61 CI runs no unit tests.
- R38 new signing key per build. Fix needs a GitHub secret the user sets: `keytool -genkeypair -keystore ~/.android/dejavu-debug.jks -storepass android -keypass android -alias dejavu -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Dejavu Debug"` then `base64 -w0 ~/.android/dejavu-debug.jks | gh secret set DEBUG_KEYSTORE_B64 -R Andrew-0807/Dejavu`, then add an `apksigner` re-sign step to the workflow.
- Untested on device: R3 to R5 bar flicker, R31 screenshot pane in split view, R32 Android 8 and 9 screenshot folder, R33 double tab on middle click, R34 restore order, R36 Ctrl+L on home, R44 and R45 Shift+Enter, R48 diagonal swipe, R49 preview offset, R50 non compact bar swipe, R51 double tap delay, R52 scroll to top via javascript URL, R53 hover pill position, R54 bookmark keyword after URL edit, R55 Nimbus flag.
- Accepted: R56 DejaVu screens missing from recent settings searches, R59 settings toolbar search icon removed.

## Audit findings (2026-10-19)

### Agent A & Agent B diff status (`dejavu-next`)

- **Agent A (`SwipeGestureLayout.kt`, `ToolbarVerticalGesturesHandler.kt`, `TabPreview.kt`, `Settings.kt`, `customization_preferences.xml`):**
  - `ToolbarVerticalGesturesHandler.kt` added `fired = true` latch (fixing the 10–30x multi-fire per swipe bug in `dejavu-src`), `claimsEarly`, and `isVerticalSwipe` (`abs(dy) > abs(dx) * 0.75f`).
  - `SwipeGestureLayout.kt` added `claimsEarly` in `dispatchTouchEvent`. **Bug in `SwipeGestureLayout.kt`:** When `claimsEarly` returns `true`, `onSwipeStarted(start, start)` is called with `currentSwipeXDistance = 0f, currentSwipeYDistance = 0f`, losing the initial `next - start` delta (`halfSlop`), and on `MotionEvent.ACTION_CANCEL` (lines 166–170), `activeListener?.onSwipeFinished(0f, 0f)` still fires (`ToolbarSwipeDownHandler` triggers immersive mode on cancelled drags if `swipeYDistance >= bar.height / 2f`).
  - R49 (`TabPreview.kt` offset and `showsToolbar = false` in `BrowserFragment.kt`) and R51 (`toolbarDoubleTapGesture` default `"none"` in `Settings.kt` and `customization_preferences.xml`) landed in the working tree.
  - R36 (`Ctrl+L` on home in `KeyboardShortcuts.kt`) and R44 (clear Shift flag in `BrowserToolbarSearchMiddleware.kt`) have **not** been modified in `dejavu-next`.
- **Agent B (`content.js`, `DejavuMouse.kt`, `DejavuSplitView.kt`, `FullPageScreenshot.kt`, `DejavuTabCommands.kt`, `SearchKeywords.kt`, `BookmarksScreen.kt`, `SearchKeywordsTest.kt`, `UseCases.kt`):**
  - R31 (`SplitPanes.active?.engineViewOf(tabId)` in `FullPageScreenshot.kt` / `DejavuSplitView.kt`), R32 (pre-Android 10 `DIRECTORY_PICTURES` fallback in `FullPageScreenshot.kt`), R33 (`mousedown` `preventDefault()` on middle click in `content.js`), R34 (`closedTabs.maxByOrNull { it.lastAccess }` in `DejavuTabCommands.kt`), R53 (`bottomToolbarHeight()` pill offset in `DejavuMouse.kt`), and R54 (bookmark keyword keyed by `bookmarkItem.guid` in `BookmarksScreen.kt`, `SearchKeywords.kt`, `UseCases.kt`, `SearchKeywordsTest.kt`) are all complete and syntax-valid in the working tree.
  - R55 (download folder picker / `feature/downloads` O27) was never touched.

### Critical & high-severity bugs (not previously documented)

1. **Private Browsing & Split-View state leaks across 4 features:**
   - **Ctrl+T `NewTabPrompt` leaks normal history in Private Mode (`NewTabPrompt.kt:147-172`):** `SearchSuggestionsProvidersBuilder` is initialized with `showAllHistorySuggestions = settings.shouldShowHistorySuggestions` without checking `browsingModeManager.mode.isPrivate` or `shouldShowSearchSuggestionsInPrivate`.
   - **"Open in split view" leaks private links (`DejavuLinkMenu.kt:89-101`, `DejavuSplitView.kt:191-194`):** `DejavuSplitView.targetOf` blocks private tabs (`selected.content.private`), but "Open in split view" is still shown in Private Mode and calls `tabsUseCases.addTab` without `private = parent.content.private`, opening a normal tab in the background while split view silently fails to appear.
   - **Mouse middle-click ignores `source: EngineSession?` (`DejavuMouse.kt:109-130`):** `handler.onMessage` ignores `source: EngineSession?` and `openInBackground` always reads `store.state.selectedTab`. Middle-clicking a link in the inactive Split View pane attaches the wrong `parentId`, `contextId`, and `private` flag.
   - **Restore closed tab ignores Private Mode & `undoHistory` (`DejavuTabCommands.kt:92-102`):** `restoreClosedTab()` does not check `tab.content.private` when triggered via the Actions Bar or shortcut (restoring normal tabs into a private session), ignores recently closed tabs still sitting in `store.state.undoHistory` while the undo snackbar is active, and can restore duplicate copies on rapid taps before `RemoveClosedTabAction` dispatches.
2. **Arbitrary scheme injection & stuck hover pill in `DejavuMouse` (`DejavuMouse.kt:109-130`, `content.js:1-21`):**
   - `content.js` only checks `a.protocol !== "javascript:"` and `DejavuMouse.kt` performs zero URL validation before calling `tabsUseCases.addTab(url = url)`. Web content can trigger privileged/local schemes (`file:///`, `about:config`, `chrome://`, `intent://`) on middle-click, and SVG `<svg><a href="javascript:...">` bypasses `a.protocol !== "javascript:"` because `SVGAElement.protocol` is `undefined` and `a.href` is an `SVGAnimatedString`.
   - **Stuck hover pill (`content.js:14`):** `pointerout` checks `!e.relatedTarget && hover("")`. When moving the mouse from a link onto adjacent text/elements on the same page, `e.relatedTarget` is non-null, so `hover("")` is never sent and the URL pill stays stuck on screen until the cursor leaves the window.
   - `event.target.closest("a[href]")` misses links inside Shadow DOM (`event.composedPath()` needed) and triggers full background tab loads on SPA `<a href="#">` anchors.
3. **Shortcut remapping dialog cannot record any already-bound shortcut (`HomeActivity.kt:1364-1372`, `DejavuSettingsFragments.kt:770-775`):**
   - `HomeActivity.dispatchKeyEvent` intercepts and executes bound `KeyCombo` shortcuts before `ShortcutDialog`'s `onPreviewKeyEvent` receives them (pressing `Ctrl+W` or `Ctrl+T` to remap closes the tab or opens `NewTabPrompt` instead of recording the key).
   - `dispatchKeyEvent` also steals `Ctrl+...` shortcuts from focused web editors/inputs, consumes `ACTION_DOWN` without consuming `ACTION_UP`, and `KeyCombo.of` (`KeyboardShortcuts.kt:42-47`) does not call `KeyEvent.normalizeMetaState(event.metaState)` (breaking Bluetooth keyboards that emit `META_CTRL_LEFT_ON` without `META_CTRL_ON`).
4. **Compact bar in `dejavu-next` swallows button taps & snaps on 1px movement (`DejavuCompactBar.kt:76, 89-149`):**
   - `val slop = viewConfiguration.touchSlop / 2` (~4–8px) plus `change.consume()` inside `verticalDrag` cancels normal taps on the Home button and Address Bar when the finger jitters slightly.
   - `onDragStopped` (`when { distance < 0f && start < 1f -> settle(true) ... }`) has no distance or velocity threshold, so a 1px drag snaps the bar open/closed or triggers `MenuRequested`.
   - Mutates Compose state (`buttonsHeight = placeable.height`) directly inside `.layout { ... }` during measurement.
   - Ignores `settings.toolbarSwipeGesture` when compact toolbar is enabled, and does not call `reinitializeEngineView()` when expanding/collapsing the buttons row (clipping the bottom of web pages).
5. **Split View missing `PromptsFeature`, `setActivityContext`, and Back routing (`DejavuSplitView.kt:228-256`, `BaseBrowserFragment.kt:2500-2507`):**
   - Secondary split `EngineView` only attaches `ContextMenuFeature` (missing `PromptsFeature` and `setActivityContext`, so JS `alert`/`confirm`, `<input type="file">`, and permission prompts hang).
   - Android Back always navigates history on `selectedTab` (or closes the primary tab and kills Split View) even if the user was interacting with the secondary split pane.
   - Primary tab fullscreen causes `targetOf()` to return `null`, tearing down the secondary split `EngineView`.
   - In `dejavu-next` (`BrowserFragment.kt:240-248`), changing `ToolbarHorizontalGesturesHandler`'s `contentLayout` from `binding.browserLayout` to `binding.swipeRefresh` leaves the split pane (`newPane` in `browserLayout`) stationary while `swipeRefresh` slides horizontally during tab swipes.
6. **TabSidebar RTL breakdown, missing Back handler, and missing Home button (`TabSidebar.kt:70-72, 116-166`):**
   - In `dejavu-next`, `pulled.snapTo((pulled.value + delta).coerceIn(0f, widthPx))` clamps negative leftward drag deltas to `0f` in RTL, and `.offset { IntOffset((pulled.value - widthPx).roundToInt(), 0) }` places the closed sidebar in the middle of the screen in RTL.
   - No `BackHandler` or swipe-to-close gesture exists when the auto-hide sidebar overlay is open.
   - `isHomeSidebarShown()` checks `settings.isVerticalTabsEnabled && AcornWindowSize.isLargeWindow()` without checking `!settings.isVerticalTabsAutoHide`, stripping the Home button from `DejavuCompactBar` and `DejavuActionsBar` even when the sidebar is auto-hidden.
   - `LaunchedEffect(selectedTabId)` (`TabSidebar.kt:97-100`) does not key on `restoreComplete`, staying stuck on a blank screen when restoring 0 tabs on cold start.
7. **Full Immersive mode & Toolbar Gesture gaps (`BaseBrowserFragment.kt:2185-2499`, `DejavuBrowser.kt:222-234`, `ScaledToolbar.kt:26-41`):**
   - Pressing Android Back does not exit full immersive mode (`isImmersive` is not checked in `onBackPressed()`).
   - `onStop()` unconditionally calls `exitFullImmersive()` and `isImmersive` is not saved in `onSaveInstanceState`, losing immersive mode on screen rotation.
   - `exitImmersiveButtonPlacer` (`OnLayoutChangeListener`) and `fadeExitImmersiveButton` runnable are never removed in `onDestroyView()`, leaking `BaseBrowserFragment`.
   - `showImmersiveMenu()` uses `menu.showAsDropDown(button)` with `isFocusable = false`, clipping off-screen when the floating button is near the right/bottom edge.
   - `"address_menu"` ("Copy or paste the address") is in `@array/dejavu_gesture_values` (`dejavu_strings.xml:46,55,65`) but missing from `runToolbarGesture` (`DejavuBrowser.kt:222-234`).
   - When toolbar is at the TOP, `ToolbarVerticalGesturesHandler.kt:142` requires `distanceY.isSwipeDown` despite the setting title saying "Swipe the toolbar up".
   - `ScaledToolbar.kt` wraps `DejavuTopBar`'s `LongPressMenu` inside the overridden `LocalDensity`, shrinking dropdown popups and distorting popup anchor offsets; `settings.toolbarScale` is also not observed as Compose state on return from Settings.
8. **Settings redesign visibility, hidden Private Browsing, & search indexing bugs (`SettingsFragment.kt:198-255`, `DefaultFenixSettingsIndexer.kt:82, 127-172`, `preferences.xml:147-151`):**
   - `preferences.xml:147-151` hardcodes `app:isPreferenceVisible="false"` on `pref_key_private_browsing` and `SettingsFragment` never reveals it, locking users out of `PrivateBrowsingFragment` (biometric/PIN lock for private tabs, screenshots in private mode) even though `docs/dejavu/settings-redesign.md:19` lists `private browsing` under Privacy and security.
   - `DefaultFenixSettingsIndexer.getSettingsWithQuery` (`line 82`) runs `.distinctBy { it.preferenceKey }`, keeping the first XML preference item (`R.id.settingsFragment`) and discarding `DejavuSettingsSearchProvider`'s direct graph destinations (`R.id.dejavu_shortcuts_graph`, `dejavu_tab_actions_graph`, etc.).
   - `DefaultFenixSettingsIndexer.parseXmlFile()` (`lines 127-172`) does not handle `<androidx.preference.ListPreference>` or `<org.mozilla.fenix.settings.ToolbarSizePreference>`, so `pref_key_dejavu_toolbar_size` and all 3 toolbar gesture preferences are completely unindexed in Settings Search.
   - `showGroup(group)` sets `pref.isVisible = inGroup && pref.key !in HIDDEN_KEYS` **after** `setupPreferences()`, re-showing preferences that `setupPreferences()` hid for unsupported hardware/flags.
   - `scrollToPreferenceWithHighlight` in `onResume()` runs before `showGroup()` makes the target preference visible, and `args.preferenceToScrollTo` is never cleared on Back press (re-opening the group on next `onResume()`).
9. **Search keywords & `NewTabPrompt` edge cases (`SearchKeywords.kt:19-28`, `NewTabPrompt.kt:163-165`, `RadioSearchEngineListPreference.kt:260-304`):**
   - Plain bookmark keywords without `%s` fail when typed alone (`if (rest.isEmpty()) return null`) and ignore `rest` when typed with arguments.
   - Clicking an AwesomeBar suggestion in `NewTabPrompt` calls `load(..., forceSearch = true)`, which bypasses `keywordResolver` in `FenixBrowserUseCases.kt:63`.
   - Deleting a bookmark or search engine leaves orphaned entries in `settings.bookmarkKeywords` / `settings.engineKeywords`; `RadioSearchEngineListPreference` does not check `settings.engineKeywords` for collisions across engines.

### Performance & memory issues

- **Full-page screenshot OOM, native bitmap leak, & `BadTokenException` (`FullPageScreenshot.kt:34-88`):**
  - `engineView.captureFullPage` allocates an uncapped single `ARGB_8888` Bitmap of the entire document height (hundreds of MBs on tall pages, causing `OutOfMemoryError` or exceeding `GL_MAX_TEXTURE_SIZE`).
  - `bitmap.recycle()` is never called after `save(activity, bitmap)` finishes compressing to PNG.
  - `showSaved(activity, uri)` only checks `!activity.isFinishing` (missing `!activity.isDestroyed`) after a multi-second IO compression on `applicationScope`, risking `WindowManager.BadTokenException` if rotated or backgrounded.
- **Window flag thrashing on scroll (`BaseBrowserFragment.kt:2197-2226`, `Activity.kt:111`):** `systemBarsUpdater` (`OnPreDrawListener`) calls `enterImmersiveMode`/`exitImmersiveMode` during scroll, toggling `FLAG_LAYOUT_NO_LIMITS` on the window (causing SurfaceFlinger re-layouts and disabling `adjustResize` for the soft keyboard).
- **Per-keystroke synchronous JSON + SharedPreferences writes (`BookmarksScreen.kt:1431-1439`):** `onValueChange` in `BookmarkEditor` parses `bookmarkKeywords` JSON, filters, serializes, and writes to `SharedPreferences` on every keystroke on the UI thread, and `UseCases.kt:179` uses `runBlocking { bookmarksStorage.value.getBookmark(guid) }` on the calling thread.
- **Recomposition & coroutine churn:**
  - `DejavuTopBar.kt:61-63` observes coarse `store.observeAsComposableState { it.displayState }`, recomposing the entire top bar on every `progressBarConfig` tick during page loads.
  - `DejavuCompactBar.kt:115` and `TabSidebar.kt:135` (`dejavu-next`) call `scope.launch { snapTo(...) }` on every 60–120Hz pointer move event.
  - `TabStrip.kt:138-163` uses an unkeyed `remember` that captures stale `isPossiblyPrivateMode = false` and recreates click handlers on every recomposition.
  - `FuzzyMatch.kt:22-24` re-allocates `query.lowercase().filterNot { it.isWhitespace() }` per candidate item instead of once per query, and `SettingsProvider.ranked` in `NewTabPrompt.kt:187` runs synchronously on the UI thread on Enter.

### Worktree hygiene notes

- `/data/data/com.termux/files/usr/tmp/dejavu-ci` has a missing `.git/worktrees/dejavu-ci/index` file (interrupted `git worktree add` before `sparse-checkout`), causing `git status` to report ~300k deleted files; remove with `git worktree remove --force $TMPDIR/dejavu-ci`.
- `~/projects/dejavu` is an empty non-git directory; `~/projects/dejavu-downloads` (`feature/downloads`) has 0 commits beyond `main`; `dejavu-search-ui`, `dejavu-settings-redesign`, and `dejavu-sidebar-home` still hold uncommitted working tree diffs from `004f6eb0` that were already committed into `c44f072e28`.
