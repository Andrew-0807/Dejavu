/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mozilla.components.feature.summarize.settings.SummarizationSettings
import org.mozilla.fenix.FeatureFlags
import org.mozilla.fenix.GleanMetrics.AppTheme
import org.mozilla.fenix.GleanMetrics.CustomizationSettings
import org.mozilla.fenix.GleanMetrics.PullToRefreshInBrowser
import org.mozilla.fenix.GleanMetrics.ToolbarSettings
import org.mozilla.fenix.R
import org.mozilla.fenix.components.toolbar.ToolbarPosition
import org.mozilla.fenix.dejavu.browser.DejavuToolbar
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.isTallWindow
import org.mozilla.fenix.ext.isWideWindow
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.settings.CustomizationFragment.Companion.Position
import org.mozilla.fenix.settings.settingssearch.PreferenceFileInformation.CustomizationPreferences
import org.mozilla.fenix.settings.settingssearch.SettingsSearchItem
import org.mozilla.fenix.settings.settingssearch.SettingsSearchProvider
import org.mozilla.fenix.translations.TranslationsEnabledSettings
import org.mozilla.fenix.utils.Settings
import org.mozilla.fenix.utils.view.addToRadioGroup

/** Lets the user customize the UI. */
class CustomizationFragment : PreferenceFragmentCompat(), SystemInsetsPaddedFragment {
    private val section = CustomizationSection(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.customization_preferences, rootKey)
        section.onCreate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        section.onViewCreated(view)
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_customize))
        // Dejavu: scroll to a searched preference once, not again on return from a sub screen.
        arguments?.getString(PREFERENCE_TO_SCROLL_TO)?.let {
            arguments?.remove(PREFERENCE_TO_SCROLL_TO)
            scrollToPreferenceWithHighlight(it)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        section.onPreferenceTreeClick(preference)
        return super.onPreferenceTreeClick(preference)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        section.onConfigurationChanged()
    }

    companion object {
        private const val PREFERENCE_TO_SCROLL_TO = "preference_to_scroll_to"

        // Used to send telemetry data about toolbar position changes
        enum class Position {
            TOP,
            BOTTOM,
        }
    }
}

/**
 * The preferences of [CustomizationFragment]: theme, toolbar, tab bar and gestures. The settings page shows them
 * inline, see docs/dejavu/settings-redesign.md.
 */
@Suppress("TooManyFunctions")
class CustomizationSection(private val fragment: PreferenceFragmentCompat) : SettingsSection {
    private lateinit var radioLightTheme: RadioButtonPreference
    private lateinit var radioDarkTheme: RadioButtonPreference
    private lateinit var radioDarkestTheme: RadioButtonPreference
    private lateinit var radioAutoBatteryTheme: RadioButtonPreference
    private lateinit var radioFollowDeviceTheme: RadioButtonPreference

    // Cached reactive feature state used when (re)building preferences.
    // Defaults match the DataStore defaults (summarization off, translations on)
    private var dynamicFeaturesStatus =
        DynamicFeaturesStatus(
            isSummarizationFeatureEnabled = false,
            isSummarizationGestureEnabled = false,
            isTranslationsFeatureEnabled = true,
            isTranslationsFeatureSupported = false,
        )

    private val requireComponents get() = fragment.requireComponents
    private val activity get() = fragment.activity
    private val viewLifecycleOwner get() = fragment.viewLifecycleOwner

    private fun requireContext() = fragment.requireContext()

    private fun getString(@StringRes id: Int) = fragment.getString(id)

    private fun <T : Preference> requirePreference(@StringRes id: Int): T = fragment.requirePreference(id)

    private fun isTallWindow() = fragment.isTallWindow()

    private fun isWideWindow() = fragment.isWideWindow()

    override fun onCreate() {
        setupPreferences(dynamicFeaturesStatus)
    }

    override fun onViewCreated(view: View) = with(fragment) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                val summarizationDataStore = SummarizationSettings.dataStore(requireContext())
                val translationsDataStore = TranslationsEnabledSettings.dataStore(requireContext())
                val browserStore = requireComponents.core.store
                combine(
                        summarizationDataStore.getFeatureEnabledUserStatus(),
                        summarizationDataStore.getGestureEnabledUserStatus(),
                        translationsDataStore.isEnabled,
                        browserStore.stateFlow,
                    ) { isSummarizationEnabled, isSummarizationGestureEnabled, areTranslationsEnabled, browserState ->
                        DynamicFeaturesStatus(
                            isSummarizationFeatureEnabled = isSummarizationEnabled == true,
                            isSummarizationGestureEnabled = isSummarizationGestureEnabled,
                            isTranslationsFeatureEnabled = areTranslationsEnabled,
                            isTranslationsFeatureSupported = browserState.translationEngine.isEngineSupported ?: false,
                        )
                    }
                    .distinctUntilChanged()
                    .collect { status ->
                        setupPreferences(status)
                    }
            }
        }
        Unit
    }

    private fun setupPreferences(status: DynamicFeaturesStatus) {
        dynamicFeaturesStatus = status

        bindFollowDeviceTheme()
        bindDarkTheme()
        bindDarkestTheme()
        bindLightTheme()
        bindAutoBatteryTheme()
        setupRadioGroups()
        setupToolbarCategory()
        setupTabStripDisplayCategory()
        setupTabStripLocationCategory()
        setupToolbarLayout()
        updateToolbarShortcut()

        // if tab strip is enabled, swipe toolbar to switch tabs should not be enabled so the
        // preference is not shown
        val tabletAndTabStripEnabled = Settings(fragment.requireContext()).isTabStripEnabled
        setupGesturesCategory(
            isSwipeToolbarToSwitchTabsVisible = !tabletAndTabStripEnabled,
            isSummarizationEnabled = status.isSummarizationFeatureEnabled,
            isSummarizationGestureEnabled = status.isSummarizationGestureEnabled,
        )
    }

    private fun updateToolbarShortcut() {
        val category = requirePreference<PreferenceCategory>(R.string.pref_key_customization_category_toolbar_shortcut)
        val settings = requireComponents.settings
        val isExpandedToolbarEnabled = settings.shouldUseExpandedToolbar && isTallWindow() && !isWideWindow()
        val isAnyShortcutSelectedForSimpleToolbar = settings.toolbarSimpleShortcutKey != ShortcutType.NONE.value

        val shortcutPreference =
            if (isExpandedToolbarEnabled) {
                buildExpandedToolbarCustomButtonSetting()
            } else if (settings.isTabStripEnabled) {
                if (settings.toolbarTabStripShortcutKey != ShortcutType.NONE.value) {
                    buildTabStripToolbarWithCustomButtonSelectedSetting()
                } else {
                    buildTabStripToolbarWithNoCustomButtonSelectedSetting()
                }
            } else if (isAnyShortcutSelectedForSimpleToolbar) {
                buildSimpleToolbarWithCustomButtonSelectedSetting()
            } else {
                buildSimpleToolbarWithNoCustomButtonSelectedSetting()
            }
        category.apply {
            removeAll()
            addPreference(shortcutPreference)
            val shortcutOptions = shortcutPreference.getShortcutOptions()
            shortcutOptions.forEach(::addPreference)
        }
    }

    private fun buildExpandedToolbarCustomButtonSetting() =
        ToolbarExpandedShortcutPreference(requireContext()).apply {
            key = getString(R.string.pref_key_toolbar_expanded_shortcut)
            layoutResource = R.layout.preference_toolbar_shortcut
            isTranslationsFeatureEnabled = dynamicFeaturesStatus.isTranslationsFeatureEnabled
        }

    private fun buildSimpleToolbarWithCustomButtonSelectedSetting() =
        ToolbarSimpleShortcutPreference(requireContext()).apply {
            key = getString(R.string.pref_key_toolbar_simple_shortcut)
            layoutResource = R.layout.preference_toolbar_shortcut
            isTranslationsFeatureEnabled = dynamicFeaturesStatus.isTranslationsFeatureEnabled
            optionChangedListener = { newOption ->
                if (newOption == null || newOption.key.value == ShortcutType.NONE.value) {
                    updateToolbarShortcut()
                }
            }
        }

    private fun buildSimpleToolbarWithNoCustomButtonSelectedSetting() =
        ToolbarSimpleNoShortcutPreference(requireContext()).apply {
            key = getString(R.string.pref_key_toolbar_simple_no_shortcut)
            layoutResource = R.layout.preference_toolbar_shortcut
            isTranslationsFeatureEnabled = dynamicFeaturesStatus.isTranslationsFeatureEnabled
            optionChangedListener = { newOption ->
                if (newOption == null || newOption.key.value != ShortcutType.NONE.value) {
                    updateToolbarShortcut()
                }
            }
        }

    private fun buildTabStripToolbarWithCustomButtonSelectedSetting() =
        ToolbarTabStripShortcutPreference(requireContext()).apply {
            key = getString(R.string.pref_key_toolbar_tab_strip_shortcut)
            layoutResource = R.layout.preference_toolbar_shortcut
            isTranslationsFeatureEnabled = dynamicFeaturesStatus.isTranslationsFeatureEnabled
            optionChangedListener = { newOption ->
                if (newOption == null || newOption.key.value == ShortcutType.NONE.value) {
                    updateToolbarShortcut()
                }
            }
        }

    private fun buildTabStripToolbarWithNoCustomButtonSelectedSetting() =
        ToolbarTabStripNoShortcutPreference(requireContext()).apply {
            key = getString(R.string.pref_key_toolbar_tab_strip_no_shortcut)
            layoutResource = R.layout.preference_toolbar_shortcut
            isTranslationsFeatureEnabled = dynamicFeaturesStatus.isTranslationsFeatureEnabled
            optionChangedListener = { newOption ->
                if (newOption == null || newOption.key.value != ShortcutType.NONE.value) {
                    updateToolbarShortcut()
                }
            }
        }

    private fun setupRadioGroups() {
        addToRadioGroup(
            radioLightTheme,
            radioDarkTheme,
            radioDarkestTheme,
            if (SDK_INT >= Build.VERSION_CODES.P) {
                radioFollowDeviceTheme
            } else {
                radioAutoBatteryTheme
            },
        )
    }

    private fun bindLightTheme() {
        radioLightTheme = requirePreference(R.string.pref_key_light_theme)
        radioLightTheme.onClickListener {
            setNewTheme(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun bindAutoBatteryTheme() {
        radioAutoBatteryTheme = requirePreference(R.string.pref_key_auto_battery_theme)
        radioAutoBatteryTheme.onClickListener {
            setNewTheme(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
        }
    }

    private fun bindDarkTheme() {
        radioDarkTheme = requirePreference(R.string.pref_key_dark_theme)
        radioDarkTheme.onClickListener {
            AppTheme.darkThemeSelected.record(AppTheme.DarkThemeSelectedExtra("SETTINGS"))
            setNewTheme(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun bindDarkestTheme() {
        val settings = requireComponents.settings
        radioDarkestTheme = requirePreference(R.string.pref_key_oled_theme)
        radioDarkestTheme.isVisible = settings.enableOledTheme
        radioDarkestTheme.onClickListener {
            AppTheme.oledThemeSelected.record(AppTheme.OledThemeSelectedExtra("SETTINGS"))
            setNewTheme(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun bindFollowDeviceTheme() {
        radioFollowDeviceTheme = requirePreference(R.string.pref_key_follow_device_theme)
        if (SDK_INT >= Build.VERSION_CODES.P) {
            radioFollowDeviceTheme.onClickListener {
                setNewTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun setNewTheme(mode: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == mode) return
        AppCompatDelegate.setDefaultNightMode(mode)
        activity?.recreate()
        with(requireComponents.core) {
            engine.settings.preferredColorScheme = getPreferredColorScheme()
        }
        requireComponents.useCases.sessionUseCases.reload.invoke()
    }

    private fun setupToolbarCategory() {
        val topPreference = requirePreference<RadioButtonPreference>(R.string.pref_key_toolbar_top)
        topPreference.onClickListener {
            ToolbarSettings.changedPosition.record(ToolbarSettings.ChangedPositionExtra(Position.TOP.name))

            updateToolbarLayoutIcons()
        }

        val bottomPreference = requirePreference<RadioButtonPreference>(R.string.pref_key_toolbar_bottom)
        bottomPreference.onClickListener {
            ToolbarSettings.changedPosition.record(ToolbarSettings.ChangedPositionExtra(Position.BOTTOM.name))

            updateToolbarLayoutIcons()
        }

        val compactPreference = requirePreference<RadioButtonPreference>(R.string.pref_key_toolbar_compact)
        compactPreference.onClickListener {
            ToolbarSettings.changedPosition.record(ToolbarSettings.ChangedPositionExtra(Position.BOTTOM.name))

            updateToolbarLayoutIcons()
        }

        val toolbarPosition = requireComponents.settings.toolbarPosition
        val isCompact = requireComponents.settings.shouldUseCompactToolbar
        topPreference.setCheckedWithoutClickListener(toolbarPosition == ToolbarPosition.TOP)
        bottomPreference.setCheckedWithoutClickListener(toolbarPosition == ToolbarPosition.BOTTOM && !isCompact)
        compactPreference.setCheckedWithoutClickListener(isCompact)

        addToRadioGroup(topPreference, bottomPreference, compactPreference)
    }

    private fun setupTabStripDisplayCategory() {
        val tabStripSwitch = requirePreference<SwitchPreferenceCompat>(R.string.pref_key_tab_strip_show)
        val context = requireContext()

        tabStripSwitch.isChecked = Settings(requireContext()).isTabStripEnabled

        tabStripSwitch.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            context.components.settings.isTabStripEnabled = enabled
            setupToolbarLayout()
            setupTabStripLocationCategory()
            updateToolbarShortcut()
            true
        }
    }

    private fun setupTabStripLocationCategory() {
        val settings = requireComponents.settings
        val isTabStripEnabled = settings.isTabStripEnabled

        requirePreference<PreferenceCategory>(R.string.pref_key_tab_strip_location).isVisible = isTabStripEnabled
        if (!isTabStripEnabled) return

        val topPreference = requirePreference<RadioButtonPreference>(R.string.pref_key_tab_bar_top)
        topPreference.onClickListener {
            ToolbarSettings.changedPosition.record(ToolbarSettings.ChangedPositionExtra(Position.TOP.name))
        }
        val bottomPreference = requirePreference<RadioButtonPreference>(R.string.pref_key_tab_bar_bottom)
        bottomPreference.onClickListener {
            ToolbarSettings.changedPosition.record(ToolbarSettings.ChangedPositionExtra(Position.BOTTOM.name))
        }

        val tabStripPosition = settings.tabStripPosition
        topPreference.setCheckedWithoutClickListener(tabStripPosition == ToolbarPosition.TOP)
        bottomPreference.setCheckedWithoutClickListener(tabStripPosition == ToolbarPosition.BOTTOM)

        addToRadioGroup(topPreference, bottomPreference)
    }

    private fun setupToolbarLayout() {
        (requirePreference(R.string.pref_key_customization_category_toolbar_layout) as PreferenceCategory).apply {
            // Dejavu's toolbar is always a single row.
            isVisible = isTallWindow() && !isWideWindow() && !DejavuToolbar.enabled
        }

        val layoutToggle = requirePreference<ToggleRadioButtonPreference>(R.string.pref_key_toolbar_expanded)
        layoutToggle.setOnToggleChanged {
            updateToolbarShortcut()
        }
        updateToolbarLayoutIcons()
    }

    private fun updateToolbarLayoutIcons() {
        (requirePreference(R.string.pref_key_toolbar_expanded) as ToggleRadioButtonPreference).apply {
            if (requireComponents.settings.shouldUseBottomToolbar) {
                updateIcon(R.drawable.ic_toolbar_bottom_expanded, R.drawable.ic_toolbar_bottom_simple)
            } else {
                updateIcon(R.drawable.ic_toolbar_top_expanded, R.drawable.ic_toolbar_top_simple)
            }
        }
    }

    private fun setupGesturesCategory(
        isSwipeToolbarToSwitchTabsVisible: Boolean,
        isSummarizationEnabled: Boolean,
        isSummarizationGestureEnabled: Boolean,
    ) {
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_website_pull_to_refresh).apply {
            isVisible = FeatureFlags.PULL_TO_REFRESH_ENABLED
            isChecked = context.components.settings.isPullToRefreshEnabledInBrowser
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_dejavu_hide_system_bars).apply {
            isChecked = context.components.settings.shouldHideSystemBars
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_dejavu_swipe_down_immersive).apply {
            isChecked = context.components.settings.isSwipeDownForImmersiveEnabled
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_vertical_tabs).apply {
            isChecked = context.components.settings.isVerticalTabsEnabled
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_vertical_tabs_auto_hide).apply {
            isChecked = context.components.settings.isVerticalTabsAutoHide
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_dejavu_sidebar_handle).apply {
            isChecked = context.components.settings.isSidebarHandleShown
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_dynamic_toolbar).apply {
            isChecked = context.components.settings.isDynamicToolbarEnabled
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_swipe_toolbar_switch_tabs).apply {
            isChecked = context.components.settings.isSwipeToolbarToSwitchTabsEnabled
            isVisible = isSwipeToolbarToSwitchTabsVisible
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_swipe_toolbar_show_tabs).apply {
            // In Dejavu, swiping the toolbar opens the menu; tabs are on the home screen.
            isVisible = !DejavuToolbar.enabled
            isChecked = context.components.settings.isSwipeToolbarToShowTabsEnabled
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_mouse_support).apply {
            isChecked = context.components.settings.isMouseSupportEnabled
            onPreferenceChangeListener = SharedPreferenceUpdater()
        }
        requirePreference<SwitchPreferenceCompat>(R.string.pref_key_shake_gesture_enabled).apply {
            isVisible = context.components.settings.shakeToSummarizeFeatureFlagEnabled && isSummarizationEnabled
            isChecked = isSummarizationGestureEnabled
            onPreferenceChangeListener = { _, newValue ->
                val updatedValue = (newValue as? Boolean) ?: false
                viewLifecycleOwner.lifecycleScope.launch {
                    SummarizationSettings.dataStore(requireContext()).setGestureEnabledUserStatus(updatedValue)
                }
                true
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            getString(R.string.pref_key_dejavu_more_menu) -> {
                fragment.findNavController().navigate(R.id.dejavu_more_menu_graph)
                return true
            }
            getString(R.string.pref_key_website_pull_to_refresh) -> {
                PullToRefreshInBrowser.enabled.set(requireComponents.settings.isPullToRefreshEnabledInBrowser)
            }
            getString(R.string.pref_key_dynamic_toolbar) -> {
                CustomizationSettings.dynamicToolbar.set(requireComponents.settings.isDynamicToolbarEnabled)
            }
        }
        return false
    }

    override fun onConfigurationChanged() {
        setupToolbarLayout()
        updateToolbarShortcut()
    }

    private data class DynamicFeaturesStatus(
        val isSummarizationFeatureEnabled: Boolean,
        val isSummarizationGestureEnabled: Boolean,
        val isTranslationsFeatureEnabled: Boolean,
        val isTranslationsFeatureSupported: Boolean,
    )
}

/** Provides the toolbar shortcuts options for use in settings search. */
object ToolbarShortcutSettingsSearchProvider : SettingsSearchProvider {
    private val preferenceFileInformation = CustomizationPreferences

    override fun getSearchItems(context: Context) =
        listOf(
            SettingsSearchItem(
                title = context.getString(R.string.preferences_toolbar_shortcut),
                summary = "",
                preferenceKey = context.getString(R.string.pref_key_customization_category_toolbar_shortcut),
                categoryHeader = context.getString(preferenceFileInformation.categoryHeaderResourceId),
                preferenceFileInformation = preferenceFileInformation,
            )
        )
}
