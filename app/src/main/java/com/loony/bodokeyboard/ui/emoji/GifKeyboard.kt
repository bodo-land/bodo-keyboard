package com.loony.bodokeyboard.ui.emoji

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.loony.bodokeyboard.data.KeyboardMode
import com.loony.bodokeyboard.ui.keyboard.KeyButton
import com.loony.bodokeyboard.ui.theme.KbBg
import com.loony.bodokeyboard.ui.theme.KeyNorm
import com.loony.bodokeyboard.ui.theme.KeySpec
import com.loony.bodokeyboard.ui.theme.ToolTxt
import com.loony.bodokeyboard.viewmodel.KeyboardViewModel

@Composable
fun GifKeyboard(viewModel: KeyboardViewModel, onKeyClick: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    
    val gifs by viewModel.gifs
    val isLoading by viewModel.isGifLoading
    val gifError by viewModel.gifError
    val context = LocalContext.current

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    DisposableEffect(imageLoader) {
        onDispose { imageLoader.shutdown() }
    }

    // Downloads run in the ViewModel's scope, which outlives this composable —
    // this flag stops a late completion from committing a GIF after the user
    // has already navigated away from the GIF panel.
    val isActive = remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { isActive.value = false }
    }

    LaunchedEffect(searchQuery) {
        viewModel.searchGifs(searchQuery)
    }

    Column(
        modifier = Modifier
            .background(KbBg)
            .height(if (isSearching) 360.dp else 300.dp)
    ) {
        // Custom Search Bar UI
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(KeySpec)
                .clickable { isSearching = true }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearching) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ToolTxt,
                        modifier = Modifier.clickable { isSearching = false }
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, tint = ToolTxt)
                    Spacer(Modifier.width(12.dp))
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("Search GIFs", color = ToolTxt, fontSize = 16.sp)
                    } else {
                        Text(searchQuery, color = Color.White, fontSize = 16.sp)
                    }
                }
                
                if (searchQuery.isNotEmpty()) {
                    Text(
                        "✕",
                        color = ToolTxt,
                        modifier = Modifier.clickable { searchQuery = "" }.padding(8.dp)
                    )
                }
            }
        }

        // GIF Grid
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && gifs.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = ToolTxt
                )
            } else if (gifs.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            gifError -> "Couldn't load GIFs"
                            searchQuery.isEmpty() -> "Loading trending..."
                            else -> "No GIFs found"
                        },
                        color = ToolTxt
                    )
                    if (gifError) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap to retry",
                            color = ToolTxt,
                            modifier = Modifier.clickable { viewModel.retryGifs() }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(gifs) { gif ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(gif.previewUrl)
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(KeyNorm)
                                .clickable {
                                    isSending = true
                                    viewModel.downloadGif(context, gif.url) { uri ->
                                        if (isActive.value) {
                                            isSending = false
                                            if (uri != null) {
                                                onKeyClick(uri.toString())
                                            }
                                        }
                                    }
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            if (isSending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // Mini Keyboard for Search
        if (isSearching) {
            val searchRows = listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m", "BACKSPACE")
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                searchRows.forEachIndexed { rowIdx, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (rowIdx == 1) Spacer(Modifier.weight(0.5f))
                        row.forEach { key ->
                            KeyButton(
                                key = key,
                                modifier = Modifier.weight(if (key == "BACKSPACE") 1.8f else 1f),
                                mode = KeyboardMode.ENGLISH,
                                isCapsLock = false,
                                viewModel = viewModel,
                                onClick = {
                                    if (key == "BACKSPACE") {
                                        if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                                    } else {
                                        searchQuery += key
                                    }
                                }
                            )
                        }
                        if (rowIdx == 1) Spacer(Modifier.weight(0.5f))
                    }
                }
            }
        } else {
            // Panel Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyButton(
                    key = "ABC",
                    modifier = Modifier.width(80.dp),
                    mode = KeyboardMode.GIF,
                    isCapsLock = false,
                    viewModel = viewModel,
                    onClick = { onKeyClick("ABC") }
                )
                
                KeyButton(
                    key = "BACKSPACE",
                    modifier = Modifier.width(80.dp),
                    mode = KeyboardMode.GIF,
                    isCapsLock = false,
                    viewModel = viewModel,
                    onClick = { onKeyClick("BACKSPACE") }
                )
            }
        }
    }
}
