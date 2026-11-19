package app.kinotrend.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kinotrend.desktop.data.Movie
import kotlinx.coroutines.launch

val Bg = Color(0xFF14181C)
val Surface = Color(0xFF222B31)
val Accent = Color(0xFFFF9900)
val TextMain = Color(0xFFEEEEEE)
val TextMuted = Color(0xFF9AA4AE)

@Composable
fun HomeScreen(state: AppState) {
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state)
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }

                state.error != null -> ErrorPanel(state.error!!) {
                    scope.launch { state.load(force = true) }
                }

                else -> CatalogGrid(
                    state = state,
                    movies = state.filteredMovies,
                    searchActive = state.query.isNotBlank() || state.hasActiveFilters,
                ) { state.openMovie(it) }
            }
        }

        if (state.showFilters) {
            FiltersOverlay(state)
        }

        SettingsDrawer(
            state = state,
            visible = state.showSettings,
            onMirror = { enabled -> scope.launch { state.setMirror(enabled) } },
            onClearCache = { scope.launch { state.clearCache() } },
            onClose = { state.showSettings = false },
        )
    }
}

@Composable
private fun TopBar(state: AppState) {
    Row(
        modifier = Modifier.fillMaxWidth().height(69.dp).background(Surface)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("KinoTrend", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(24.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            singleLine = true,
            placeholder = { Text("Поиск фильмов", color = TextMuted, fontSize = 13.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 13.sp),
            modifier = Modifier.width(340.dp),
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    Text(
                        "✕", color = TextMuted, fontSize = 14.sp,
                        modifier = Modifier.clickable { state.query = "" }.padding(8.dp),
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color(0xFF3A454D),
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
                cursorColor = Accent,
                focusedContainerColor = Color(0xFF1A2126),
                unfocusedContainerColor = Color(0xFF1A2126),
            ),
        )
        Spacer(Modifier.weight(1f))
        if (state.dubbingOnly) {
            val total = state.catalog?.movies?.size ?: 0
            Text(
                "Дубляж ${state.dubbingMovieCount} из $total",
                color = Accent,
                fontSize = 14.sp,
                modifier = Modifier.clickable { state.showSettings = true }.padding(vertical = 8.dp),
            )
            Spacer(Modifier.width(18.dp))
        }
        Text(
            if (state.hasActiveFilters) "Фильтры •" else "Фильтры",
            color = if (state.hasActiveFilters) Accent else TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.clickable { state.showFilters = true }.padding(vertical = 8.dp),
        )
        Spacer(Modifier.width(20.dp))
        Text(
            "Настройки",
            color = TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.clickable { state.showSettings = true }.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = TextMain, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
        ) {
            Text("Обновить")
        }
    }
}

@Composable
private fun CatalogGrid(
    state: AppState,
    movies: List<Movie>,
    searchActive: Boolean,
    onOpen: (Movie) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.sortBy, state.query, state.selectedGenres, state.selectedCountries, state.minRating) {
        gridState.scrollToItem(0)
    }

    val cardWidth = when (state.density) {
        "compact" -> 150.dp
        "large" -> 230.dp
        else -> 180.dp
    }
    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (searchActive) "Ничего не найдено" else "Каталог пуст",
                color = TextMuted,
                fontSize = 15.sp,
            )
        }
        return
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(cardWidth),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(movies, key = { it.filmID }) { movie ->
            MovieCard(state, movie, onOpen)
        }
    }
}

@Composable
private fun MovieCard(state: AppState, movie: Movie, onOpen: (Movie) -> Unit) {
    val cover = if (state.widePoster) movie.bigPosterURL.ifBlank { movie.posterURL } else movie.posterURL
    val poster by produceState<ImageBitmap?>(initialValue = null, cover) {
        value = PosterLoader.load(cover)
    }

    Column(Modifier.fillMaxWidth().clickable { onOpen(movie) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (state.widePoster) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(8.dp)).background(Surface),
        ) {
            poster?.let { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = movie.nameRU,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (movie.rating.isNotBlank()) {
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("★", color = Accent, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(movie.rating, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            movie.nameRU,
            color = TextMain,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(movie.year.takeIf { it.isNotBlank() }, movie.genre.takeIf { it.isNotBlank() })
                .joinToString(" · "),
            color = TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
