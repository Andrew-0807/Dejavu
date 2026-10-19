/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.newtab

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import mozilla.components.browser.state.search.DefaultSearchEngineProvider
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.compose.browser.awesomebar.AwesomeBar
import mozilla.components.concept.awesomebar.AwesomeBar.Suggestion
import mozilla.components.concept.awesomebar.AwesomeBar.SuggestionItem
import mozilla.components.concept.awesomebar.AwesomeBar.SuggestionProvider
import mozilla.components.concept.engine.EngineSession.LoadUrlFlags
import mozilla.components.feature.search.SearchUseCases.SearchUseCase
import mozilla.components.feature.session.SessionUseCases.LoadUrlUseCase
import mozilla.components.feature.tabs.TabsUseCases.SelectTabUseCase
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.components.metrics.MetricsUtils
import org.mozilla.fenix.dejavu.FUZZY_GOOD_SCORE_PER_CHAR
import org.mozilla.fenix.dejavu.fuzzyScore
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.search.SearchEngineSource
import org.mozilla.fenix.search.awesomebar.DefaultSuggestionIconProvider
import org.mozilla.fenix.search.awesomebar.DefaultSuggestionsStringsProvider
import org.mozilla.fenix.search.awesomebar.SearchSuggestionsProvidersBuilder
import org.mozilla.fenix.search.awesomebar.toSearchProviderState
import org.mozilla.fenix.search.createInitialSearchFragmentState
import org.mozilla.fenix.settings.settingssearch.PreferenceFileInformation
import org.mozilla.fenix.settings.settingssearch.SettingsSearchItem
import org.mozilla.fenix.theme.FirefoxTheme

/**
 * Zen's new tab prompt: a floating address field in the middle of the screen over a dimmed page, with the usual
 * suggestions and matching settings under it. What is picked opens in a new tab. Typing the settings search keyword
 * and a space first shows only settings.
 */
object NewTabPrompt {
    private const val TAG = "dejavu_new_tab_prompt"

    fun show(activity: HomeActivity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        if (root.findViewWithTag<View>(TAG) != null) return
        val view = ComposeView(activity).apply { tag = TAG }
        view.setContent {
            FirefoxTheme {
                Prompt(activity) { root.post { root.removeView(view) } }
            }
        }
        root.addView(view, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
}

@Composable
private fun Prompt(activity: HomeActivity, onClose: () -> Unit) {
    val components = activity.components
    val isPrivate = activity.browsingModeManager.mode.isPrivate
    var text by remember { mutableStateOf("") }
    val keyword = remember { components.settings.settingsSearchKeyword }
    val settingsOnly = keyword.isNotEmpty() && text.startsWith("$keyword ", ignoreCase = true)
    val query = if (settingsOnly) text.substring(keyword.length + 1) else text
    val navController = remember { activity.findNavController(R.id.container) }
    val focus = remember { FocusRequester() }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        shown.animateTo(1f, tween(OPEN_MS, easing = FastOutSlowInEasing))
    }
    BackHandler(onBack = onClose)

    fun showBrowser() {
        if (navController.currentDestination?.id != R.id.browserFragment) navController.navigate(R.id.browserFragment)
    }

    fun load(url: String, forceSearch: Boolean = false, engine: SearchEngine? = null, flags: LoadUrlFlags? = null) {
        components.useCases.fenixBrowserUseCases.loadUrlOrSearch(
            searchTermOrURL = url,
            newTab = true,
            private = isPrivate,
            forceSearch = forceSearch,
            searchEngine = engine,
            flags = flags ?: LoadUrlFlags.none(),
        )
        showBrowser()
        onClose()
    }

    fun openSetting(item: SettingsSearchItem) {
        val args = Bundle().apply { putString("preference_to_scroll_to", item.preferenceKey) }
        navController.navigate(item.preferenceFileInformation.fragmentId, args)
        onClose()
    }

    val webProviders = remember {
        val defaultEngine = components.core.store.state.search.selectedOrDefaultSearchEngine(private = isPrivate)
        val state = createInitialSearchFragmentState(activity, components, null, null, MetricsUtils.Source.NONE)
            .copy(searchEngineSource = defaultEngine?.let { SearchEngineSource.Default(it) } ?: SearchEngineSource.None)
        val builder = SearchSuggestionsProvidersBuilder(
            components = components,
            scope = ProcessLifecycleOwner.get().lifecycleScope,
            browsingModeManager = activity.browsingModeManager,
            includeSelectedTab = true,
            loadUrlUseCase = object : LoadUrlUseCase {
                override fun invoke(
                    url: String,
                    flags: LoadUrlFlags,
                    additionalHeaders: Map<String, String>?,
                    originalInput: String?,
                ) = load(url, flags = flags)
            },
            searchUseCase = object : SearchUseCase {
                override fun invoke(searchTerms: String, searchEngine: SearchEngine?, parentSessionId: String?) =
                    load(searchTerms, forceSearch = true, engine = searchEngine)
            },
            selectTabUseCase = object : SelectTabUseCase {
                override fun invoke(tabId: String) {
                    components.useCases.tabsUseCases.selectTab(tabId)
                    showBrowser()
                    onClose()
                }
            },
            suggestionsStringsProvider = DefaultSuggestionsStringsProvider(
                activity,
                DefaultSearchEngineProvider(components.core.store),
            ),
            suggestionIconProvider = DefaultSuggestionIconProvider(activity),
            onSearchEngineSuggestionSelected = {},
        )
        val engineShortcuts = setOfNotNull(builder.searchEngineSuggestionProvider)
        builder.getProvidersToAdd(state.toSearchProviderState()).toList() - engineShortcuts
    }
    val providers = remember(settingsOnly) {
        val settings = SettingsProvider(activity, onlySettings = settingsOnly, onPick = ::openSetting)
        if (settingsOnly) listOf(settings) else webProviders + settings
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .background(Color.Black.copy(alpha = BACKDROP_ALPHA))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(top = PROMPT_TOP, start = SIDE_PADDING, end = SIDE_PADDING)
                .widthIn(max = PROMPT_MAX_WIDTH)
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = PROMPT_START_SCALE + (1f - PROMPT_START_SCALE) * shown.value
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(PROMPT_ELEVATION, PromptShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = PROMPT_ALPHA), PromptShape)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, PromptShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = FIELD_HEIGHT).padding(horizontal = 20.dp)) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        fontSize = FIELD_TEXT_SIZE,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = FIELD_TEXT_SIZE,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) load(query) }),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (it.key) {
                                Key.Escape -> onClose()
                                Key.Enter, Key.NumPadEnter -> when {
                                    settingsOnly -> SettingsProvider.ranked(activity, query).firstOrNull()
                                        ?.let { (item, _) -> openSetting(item) }
                                    query.isNotBlank() -> load(query)
                                    else -> {}
                                }
                                else -> return@onPreviewKeyEvent false
                            }
                            true
                        },
                )
            }
            if (query.isNotBlank()) {
                Box(modifier = Modifier.heightIn(max = RESULTS_MAX_HEIGHT).padding(bottom = 8.dp)) {
                    AwesomeBar(
                        text = query,
                        providers = providers,
                        onSuggestionClicked = { it.onSuggestionClicked?.invoke() },
                        onAutoComplete = { suggestion -> suggestion.editSuggestion?.let { text = it } },
                        onRemoveClicked = {},
                    )
                }
            }
        }
    }
}

/** Settings whose titles fuzzy match the text. Unless [onlySettings], only the good matches. */
private class SettingsProvider(
    private val context: Context,
    private val onlySettings: Boolean,
    private val onPick: (SettingsSearchItem) -> Unit,
) : SuggestionProvider {
    override val id = "dejavu_settings"

    override fun groupTitle() = context.getString(R.string.settings_title)

    override suspend fun onInputChanged(text: String): List<SuggestionItem> {
        val indexer = context.components.settingsIndexer
        if (indexer.indexedSettings().isEmpty()) indexer.indexAllSettings()
        val good = text.length * FUZZY_GOOD_SCORE_PER_CHAR
        return ranked(context, text)
            .filter { (_, score) -> onlySettings || (text.length >= MIN_AUTO_CHARS && score >= good) }
            .take(if (onlySettings) MAX_ONLY_SETTINGS else MAX_AUTO_SETTINGS)
            .map { (item, score) ->
                Suggestion(
                    provider = this,
                    id = item.preferenceKey,
                    title = item.title,
                    description = item.categoryHeader.ifEmpty { item.summary },
                    score = score,
                    onSuggestionClicked = { onPick(item) },
                )
            }
    }

    companion object {
        fun ranked(context: Context, text: String): List<Pair<SettingsSearchItem, Int>> =
            context.components.settingsIndexer.indexedSettings()
                .sortedBy { it.preferenceFileInformation !is PreferenceFileInformation.DejavuScreen }
                .distinctBy { it.preferenceKey }
                .mapNotNull { item -> fuzzyScore(text, item.title)?.let { item to it } }
                .sortedByDescending { it.second }
    }
}

private val PromptShape = RoundedCornerShape(15.dp)
private val PROMPT_TOP = 120.dp
private val PROMPT_MAX_WIDTH = 720.dp
private val SIDE_PADDING = 16.dp
private val PROMPT_ELEVATION = 24.dp
private val FIELD_HEIGHT = 62.dp
private val FIELD_TEXT_SIZE = 20.sp
private val RESULTS_MAX_HEIGHT = 420.dp
private const val PROMPT_ALPHA = 0.93f
private const val BACKDROP_ALPHA = 0.35f
private const val PROMPT_START_SCALE = 0.96f
private const val OPEN_MS = 150
private const val MIN_AUTO_CHARS = 3
private const val MAX_AUTO_SETTINGS = 3
private const val MAX_ONLY_SETTINGS = 20
