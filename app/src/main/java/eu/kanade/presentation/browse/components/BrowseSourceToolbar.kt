package eu.kanade.presentation.browse.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.presentation.browse.BrowseSourceState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioButton
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.LocalSource
import exh.source.anyIs

@Composable
fun BrowseSourceToolbar(
    state: BrowseSourceState,
    source: CatalogueSource,
    displayMode: LibraryDisplayMode?,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSearch: (String) -> Unit,
    // SY -->
    onSettingsClick: () -> Unit,
    // SY <--
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    if (state.searchQuery == null) {
        BrowseSourceRegularToolbar(
            title = if (state.isUserQuery) state.currentFilter.query else source.name,
            isLocalSource = source is LocalSource,
            // SY -->
            isConfigurableSource = source.anyIs<ConfigurableSource>(),
            // SY <--
            displayMode = displayMode,
            onDisplayModeChange = onDisplayModeChange,
            navigateUp = navigateUp,
            onSearchClick = { state.searchQuery = if (state.isUserQuery) state.currentFilter.query else "" },
            onWebViewClick = onWebViewClick,
            onHelpClick = onHelpClick,
            // SY -->
            onSettingsClick = onSettingsClick,
            // SY <--
            scrollBehavior = scrollBehavior,
        )
    } else {
        val cancelSearch = { state.searchQuery = null }
        BrowseSourceSearchToolbar(
            searchQuery = state.searchQuery!!,
            onSearchQueryChanged = { state.searchQuery = it },
            placeholderText = stringResource(R.string.action_search_hint),
            navigateUp = cancelSearch,
            onResetClick = { state.searchQuery = "" },
            onSearchClick = {
                onSearch(it)
                cancelSearch()
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
fun BrowseSourceRegularToolbar(
    title: String,
    isLocalSource: Boolean,
    isConfigurableSource: Boolean,
    displayMode: LibraryDisplayMode?,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
    navigateUp: () -> Unit,
    onSearchClick: () -> Unit,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    // SY -->
    onSettingsClick: () -> Unit,
    // SY <--
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    AppBar(
        navigateUp = navigateUp,
        title = title,
        actions = {
            var selectingDisplayMode by remember { mutableStateOf(false) }
            AppBarActions(
                actions = listOfNotNull(
                    AppBar.Action(
                        title = stringResource(R.string.action_search),
                        icon = Icons.Outlined.Search,
                        onClick = onSearchClick,
                    ),
                    // SY -->
                    AppBar.Action(
                        title = stringResource(R.string.action_display_mode),
                        icon = if (displayMode == LibraryDisplayMode.List) Icons.Filled.ViewList else Icons.Filled.ViewModule,
                        onClick = { selectingDisplayMode = true },
                    ).takeIf { displayMode != null },
                    // SY <--
                    if (isLocalSource) {
                        AppBar.Action(
                            title = stringResource(R.string.label_help),
                            icon = Icons.Outlined.Help,
                            onClick = onHelpClick,
                        )
                    } else {
                        AppBar.Action(
                            title = stringResource(R.string.action_web_view),
                            icon = Icons.Outlined.Public,
                            onClick = onWebViewClick,
                        )
                    },
                    // SY -->
                    if (isConfigurableSource) {
                        AppBar.Action(
                            title = stringResource(R.string.action_settings),
                            icon = Icons.Outlined.Settings,
                            onClick = onSettingsClick,
                        )
                    } else {
                        null
                    },
                    // SY <--
                ),
            )
            DropdownMenu(
                expanded = selectingDisplayMode,
                onDismissRequest = { selectingDisplayMode = false },
            ) {
                RadioButton(
                    text = { Text(text = stringResource(R.string.action_display_comfortable_grid)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.ComfortableGrid) },
                    isChecked = displayMode == LibraryDisplayMode.ComfortableGrid,
                )
                RadioButton(
                    text = { Text(text = stringResource(R.string.action_display_grid)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.CompactGrid) },
                    isChecked = displayMode == LibraryDisplayMode.CompactGrid,
                )
                RadioButton(
                    text = { Text(text = stringResource(R.string.action_display_list)) },
                    onClick = { onDisplayModeChange(LibraryDisplayMode.List) },
                    isChecked = displayMode == LibraryDisplayMode.List,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun BrowseSourceSearchToolbar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    placeholderText: String?,
    navigateUp: () -> Unit,
    onResetClick: () -> Unit,
    onSearchClick: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    SearchToolbar(
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChanged,
        placeholderText = placeholderText,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearchClick(searchQuery)
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        ),
        onClickCloseSearch = navigateUp,
        onClickResetSearch = onResetClick,
        scrollBehavior = scrollBehavior,
    )
}
