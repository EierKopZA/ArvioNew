package com.arflix.tv.ui.screens.collections

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CollectionGroupKind
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.CatalogRepository
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.ui.components.AppTopBar
import com.arflix.tv.ui.components.CardLayoutMode
import com.arflix.tv.ui.components.MediaCard
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.rememberCardLayoutMode
import com.arflix.tv.ui.components.topBarFocusedItem
import com.arflix.tv.ui.components.topBarMaxIndex
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.BackgroundDark
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import com.arflix.tv.util.LocalDeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CollectionTab { MOVIES, SERIES }

data class CollectionDetailsUiState(
    val catalog: CatalogConfig? = null,
    val movieItems: List<MediaItem> = emptyList(),
    val seriesItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val hasMovies: Boolean get() = movieItems.isNotEmpty()
    val hasSeries: Boolean get() = seriesItems.isNotEmpty()
}

@HiltViewModel
class CollectionDetailsViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val mediaRepository: MediaRepository,
    private val streamRepository: StreamRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CollectionDetailsUiState())
    val uiState: StateFlow<CollectionDetailsUiState> = _uiState.asStateFlow()

    fun load(catalogId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val catalog = catalogRepository.getCatalogs().firstOrNull { it.id == catalogId }
            if (catalog == null) {
                _uiState.value = CollectionDetailsUiState(isLoading = false, error = "Collection not found")
                return@launch
            }
            if (catalog.requiredAddonUrls.isNotEmpty()) {
                runCatching { streamRepository.ensureCustomAddons(catalog.requiredAddonUrls) }
            }
            val page = runCatching {
                mediaRepository.loadCollectionCatalogPage(catalog, offset = 0, limit = 120)
            }.getOrNull()
            val all = page?.items.orEmpty()
            _uiState.value = CollectionDetailsUiState(
                catalog = catalog,
                movieItems = all.filter { it.mediaType == MediaType.MOVIE },
                seriesItems = all.filter { it.mediaType == MediaType.TV },
                isLoading = false,
                error = if (page == null) "Failed to load collection" else null
            )
        }
    }
}

@Composable
fun CollectionDetailsScreen(
    catalogId: String,
    currentProfile: com.arflix.tv.data.model.Profile? = null,
    viewModel: CollectionDetailsViewModel = hiltViewModel(),
    onNavigateToDetails: (MediaType, Int) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToTv: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSwitchProfile: () -> Unit = {},
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(catalogId) { viewModel.load(catalogId) }
    BackHandler(onBack = onBack)

    val usePosterCards = rememberCardLayoutMode() == CardLayoutMode.POSTER
    val configuration = LocalConfiguration.current
    val isMobile = LocalDeviceType.current.isTouchDevice()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Card sizing matches the home row feel: landscape tiles are the same
    // width as home-row cards, posters a touch wider so the grid doesn't feel
    // more cramped than the scrollable rows on home. Column counts scale to
    // fit. Previously cards were 210dp TV / 200dp mobile with 5–8 columns,
    // which looked dense next to home's 4–5 visible cards — user wanted them
    // closer in feel.
    val cardWidth = if (usePosterCards) {
        if (isMobile) 130.dp else 150.dp
    } else if (isMobile) 220.dp else 260.dp
    val gridColumns = if (isMobile) {
        if (isLandscape) { if (usePosterCards) 5 else 3 } else if (usePosterCards) 3 else 2
    } else if (usePosterCards) {
        when {
            configuration.screenWidthDp >= 2200 -> 10
            configuration.screenWidthDp >= 1600 -> 8
            else -> 6
        }
    } else when {
        configuration.screenWidthDp >= 2200 -> 6
        configuration.screenWidthDp >= 1600 -> 5
        else -> 4
    }

    val initialTab = when {
        uiState.hasMovies -> CollectionTab.MOVIES
        uiState.hasSeries -> CollectionTab.SERIES
        else -> CollectionTab.MOVIES
    }
    var selectedTab by remember(uiState.catalog?.id) { mutableStateOf(initialTab) }

    var isSidebarFocused by remember { mutableStateOf(false) }
    // Three-zone focus on TV: sidebar ↔ tab bar ↔ grid. Grid-up jumps to the
    // tab bar (previously vaulted straight to sidebar, which made Movies/Series
    // tabs unreachable with a remote); tab-up then continues to sidebar.
    var isTabBarFocused by remember { mutableStateOf(false) }
    val hasProfile = currentProfile != null
    val maxSidebarIndex = topBarMaxIndex(hasProfile)
    var sidebarFocusIndex by remember { mutableIntStateOf(if (hasProfile) 2 else 1) } // HOME
    val rootFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    val gridState = rememberTvLazyGridState()
    var focusedGridIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.catalog?.id) {
        if (uiState.catalog != null) {
            delay(80)
            runCatching { gridFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                fun moveToSidebar(): Boolean {
                    isTabBarFocused = false
                    isSidebarFocused = true
                    runCatching { rootFocusRequester.requestFocus() }
                    return true
                }
                fun moveToTabBar(): Boolean {
                    isSidebarFocused = false
                    isTabBarFocused = true
                    runCatching { rootFocusRequester.requestFocus() }
                    return true
                }
                fun moveToGrid(): Boolean {
                    isSidebarFocused = false
                    isTabBarFocused = false
                    scope.launch {
                        delay(40)
                        runCatching { gridFocusRequester.requestFocus() }
                    }
                    return true
                }
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        if (isSidebarFocused) { onBack(); true }
                        else if (isTabBarFocused) moveToSidebar()
                        else moveToSidebar()
                    }
                    Key.DirectionUp -> {
                        if (isSidebarFocused) return@onPreviewKeyEvent true
                        if (isTabBarFocused) { moveToSidebar(); true }
                        else {
                            val firstVisibleIndex = gridState.firstVisibleItemIndex
                            if (firstVisibleIndex == 0 && focusedGridIndex < gridColumns) {
                                // From the grid's top row, go to the tab bar
                                // so Movies/Series chips are reachable before
                                // the sidebar.
                                moveToTabBar()
                            } else false
                        }
                    }
                    Key.DirectionDown -> {
                        if (isSidebarFocused) moveToGrid()
                        else if (isTabBarFocused) moveToGrid()
                        else false
                    }
                    Key.DirectionLeft -> {
                        if (isSidebarFocused) {
                            if (sidebarFocusIndex > 0) {
                                sidebarFocusIndex = (sidebarFocusIndex - 1).coerceIn(0, maxSidebarIndex)
                            }
                            true
                        } else if (isTabBarFocused && uiState.hasMovies && uiState.hasSeries) {
                            // Movies chip sits left of Series — Left switches
                            // to Movies when both tabs are shown.
                            selectedTab = CollectionTab.MOVIES
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (isSidebarFocused) {
                            if (sidebarFocusIndex < maxSidebarIndex) {
                                sidebarFocusIndex = (sidebarFocusIndex + 1).coerceIn(0, maxSidebarIndex)
                            }
                            true
                        } else if (isTabBarFocused && uiState.hasMovies && uiState.hasSeries) {
                            selectedTab = CollectionTab.SERIES
                            true
                        } else false
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (isSidebarFocused) {
                            if (hasProfile && sidebarFocusIndex == 0) {
                                onSwitchProfile()
                            } else {
                                when (topBarFocusedItem(sidebarFocusIndex, hasProfile)) {
                                    SidebarItem.SEARCH -> onNavigateToSearch()
                                    SidebarItem.HOME -> onNavigateToHome()
                                    SidebarItem.WATCHLIST -> onNavigateToWatchlist()
                                    SidebarItem.TV -> onNavigateToTv()
                                    SidebarItem.SETTINGS -> onNavigateToSettings()
                                    null -> Unit
                                }
                            }
                            true
                        } else if (isTabBarFocused) {
                            // Already reflected by DirectionLeft/Right; Enter
                            // drops focus back into the grid so the user can
                            // scroll the newly-active tab.
                            moveToGrid()
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        if (!isMobile) {
            AppTopBar(
                selectedItem = SidebarItem.HOME,
                isFocused = isSidebarFocused,
                focusedIndex = sidebarFocusIndex,
                profile = currentProfile
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Hero renders behind everything as an ambient backdrop. The
            // Movies/Series tabs sit right below the topbar — the grid
            // scrolls over the fading hero so the page feels anchored to
            // the top instead of being pushed down by a tall hero block.
            val backdropHeight = (maxHeight * 0.42f).coerceIn(200.dp, 360.dp)
            CollectionHero(
                catalog = uiState.catalog,
                heroHeight = backdropHeight
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Sit the header close to the topbar so the hero backdrop
                    // reads as ambience, not a tall block. Leaves enough room
                    // for the TV sidebar chevron to render above it.
                    .padding(top = if (isMobile) 8.dp else 40.dp)
            ) {
                CollectionTitleHeader(catalog = uiState.catalog)

                CollectionTabBar(
                    hasMovies = uiState.hasMovies,
                    hasSeries = uiState.hasSeries,
                    selectedTab = selectedTab,
                    isFocusedFromRemote = isTabBarFocused,
                    onTabSelected = { selectedTab = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                val items = when (selectedTab) {
                    CollectionTab.MOVIES -> uiState.movieItems
                    CollectionTab.SERIES -> uiState.seriesItems
                }

                AnimatedContent(
                    targetState = Triple(selectedTab, items, uiState.isLoading),
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120)))
                    },
                    label = "collection_tab_content"
                ) { (_, currentItems, loading) ->
                    when {
                        loading -> CollectionSkeletonGrid(gridColumns = gridColumns, cardWidth = cardWidth, usePosterCards = usePosterCards)
                        currentItems.isEmpty() -> CollectionEmptyState(
                            message = uiState.error ?: "Nothing to show here yet."
                        )
                        else -> CollectionItemsGrid(
                            items = currentItems,
                            gridColumns = gridColumns,
                            cardWidth = cardWidth,
                            usePosterCards = usePosterCards,
                            gridState = gridState,
                            focusRequester = gridFocusRequester,
                            onFocusChanged = { hasFocus ->
                                if (hasFocus) isSidebarFocused = false
                            },
                            onItemFocused = { focusedGridIndex = it },
                            onItemClick = { onNavigateToDetails(it.mediaType, it.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionTitleHeader(catalog: CatalogConfig?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 10.dp)
    ) {
        CollectionGroupPill(group = catalog?.collectionGroup)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.tv.material3.Text(
            text = catalog?.title ?: "Collection",
            style = ArflixTypography.heroTitle.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun CollectionHero(
    catalog: CatalogConfig?,
    heroHeight: androidx.compose.ui.unit.Dp
) {
    val videoUrl = remember(catalog?.id) {
        catalog?.collectionHeroVideoUrl?.takeIf { it.isNotBlank() }
    }

    // `videoPlayed` resets when `catalog?.id` changes (new detail screen entry),
    // so re-entering a services collection replays the video. Within one entry,
    // once the video ends (or errors, or the app backgrounds) we flip to STATIC
    // and never re-spawn the player.
    var videoPlayed by remember(catalog?.id) { mutableStateOf(false) }

    // Static fallback candidates — used when there's no video, when the video
    // finishes, and when the video errors. Prefer high-res static hero art
    // over GIF loops so the backdrop looks crisp on large screens. Fall
    // through to the next URL when one fails to load (handles upstream URL
    // rot).
    val staticCandidates = remember(catalog?.id) {
        listOfNotNull(
            catalog?.collectionHeroImageUrl,
            catalog?.collectionCoverImageUrl,
            catalog?.collectionHeroGifUrl,
            catalog?.collectionFocusGifUrl
        ).filter { it.isNotBlank() }.distinct()
    }
    var staticIndex by remember(catalog?.id) { mutableIntStateOf(0) }
    val currentStatic = staticCandidates.getOrNull(staticIndex)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        if (videoUrl != null && !videoPlayed) {
            VideoHero(
                videoUrl = videoUrl,
                modifier = Modifier.fillMaxSize(),
                onEnded = { videoPlayed = true }
            )
        } else if (currentStatic != null) {
            val painter = rememberAsyncImagePainter(model = currentStatic)
            val painterState = painter.state
            LaunchedEffect(painterState, currentStatic) {
                if (painterState is AsyncImagePainter.State.Error &&
                    staticIndex < staticCandidates.lastIndex
                ) {
                    staticIndex += 1
                }
            }
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        // Stronger fade at the top (so the topbar stays readable) and at the
        // bottom (so grid cards aren't competing with backdrop imagery).
        // Stays on top of both video and static branches so topbar + grid
        // readability are preserved.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BackgroundDark.copy(alpha = 0.65f),
                            BackgroundDark.copy(alpha = 0.25f),
                            BackgroundDark.copy(alpha = 0.85f),
                            BackgroundDark
                        )
                    )
                )
        )
    }
}

@Composable
private fun CollectionGroupPill(group: CollectionGroupKind?) {
    if (group == null) return
    val label = when (group) {
        CollectionGroupKind.SERVICE -> "STREAMING SERVICE"
        CollectionGroupKind.GENRE -> "GENRE"
        CollectionGroupKind.DIRECTOR -> "DIRECTOR"
        CollectionGroupKind.FRANCHISE -> "FRANCHISE"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        androidx.tv.material3.Text(
            text = label,
            style = ArflixTypography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            ),
            color = TextPrimary.copy(alpha = 0.92f)
        )
    }
}

@Composable
private fun CollectionTabBar(
    hasMovies: Boolean,
    hasSeries: Boolean,
    selectedTab: CollectionTab,
    isFocusedFromRemote: Boolean,
    onTabSelected: (CollectionTab) -> Unit
) {
    val showMovies = hasMovies || !hasSeries
    val showSeries = hasSeries || !hasMovies
    val onlyOne = showMovies xor showSeries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 4.dp, end = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showMovies) {
            CollectionTabChip(
                label = "Movies",
                isSelected = selectedTab == CollectionTab.MOVIES || onlyOne,
                isFocusedFromRemote = isFocusedFromRemote && selectedTab == CollectionTab.MOVIES,
                onClick = { onTabSelected(CollectionTab.MOVIES) }
            )
        }
        if (showSeries) {
            CollectionTabChip(
                label = "Series",
                isSelected = selectedTab == CollectionTab.SERIES || onlyOne,
                isFocusedFromRemote = isFocusedFromRemote && selectedTab == CollectionTab.SERIES,
                onClick = { onTabSelected(CollectionTab.SERIES) }
            )
        }
    }
}

@Composable
private fun CollectionTabChip(
    label: String,
    isSelected: Boolean,
    isFocusedFromRemote: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val visuallyFocused = isFocused || isFocusedFromRemote
    val shape = RoundedCornerShape(50)
    val bg = when {
        visuallyFocused || isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.10f)
    }
    val fg = when {
        visuallyFocused || isSelected -> BackgroundDark
        else -> TextPrimary.copy(alpha = 0.9f)
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(
                if (visuallyFocused) Modifier.border(
                    width = 2.dp,
                    color = Color.White,
                    shape = shape
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        androidx.tv.material3.Text(
            text = label,
            style = ArflixTypography.sectionTitle.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            ),
            color = fg
        )
    }
}

@Composable
private fun CollectionItemsGrid(
    items: List<MediaItem>,
    gridColumns: Int,
    cardWidth: androidx.compose.ui.unit.Dp,
    usePosterCards: Boolean,
    gridState: androidx.tv.foundation.lazy.grid.TvLazyGridState,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onItemFocused: (Int) -> Unit,
    onItemClick: (MediaItem) -> Unit
) {
    val cardContentType = if (usePosterCards) "poster_card" else "landscape_card"
    TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(gridColumns),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 42.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.hasFocus) },
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(
            items,
            key = { _, it -> "${it.mediaType}-${it.id}" },
            contentType = { _, _ -> cardContentType }
        ) { index, item ->
            MediaCard(
                item = item,
                width = cardWidth,
                isLandscape = !usePosterCards,
                showTitle = true,
                onFocused = { onItemFocused(index) },
                onClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
private fun CollectionSkeletonGrid(
    gridColumns: Int,
    cardWidth: androidx.compose.ui.unit.Dp,
    usePosterCards: Boolean
) {
    val cardHeight = if (usePosterCards) cardWidth * 1.5f else cardWidth * 9f / 16f
    TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(gridColumns),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 42.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed((1..gridColumns * 3).toList()) { _, _ ->
            Box(
                modifier = Modifier
                    .height(cardHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
        }
    }
}

@Composable
private fun CollectionEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.tv.material3.Text(
            text = message,
            color = TextSecondary,
            style = ArflixTypography.body
        )
    }
}
