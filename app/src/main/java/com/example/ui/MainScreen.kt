package com.example.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.DownloadedAudio
import com.example.network.VideoMetadata
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // UI States
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val downloadHistory by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val selectedBitrate by viewModel.selectedBitrate.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val activeDownloadsCount = remember(downloadHistory) {
        downloadHistory.count { it.status == "DOWNLOADING" || it.status == "QUEUED" }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Tune",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "AURA DOWNLOADER",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Persistent Mini Player on top of system bar if any audio is active
            if (playerState.audioId != null) {
                MiniAudioPlayerBar(
                    playerState = playerState,
                    onTogglePlay = { viewModel.audioPlayer.togglePlayPause() },
                    onExpand = { isPlayerExpanded = true }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Custom Tab Row styled beautifully in Gold Accent
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp,
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab])
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Link, contentDescription = "Link")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Extractor", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("extractor_tab")
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BadgedBox(
                                    badge = {
                                        if (activeDownloadsCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text(activeDownloadsCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "History")
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("My Library", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("library_tab")
                    )
                }

                // Divider separating TabBar and content
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                // Render matching contents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (activeTab == 0) {
                        ExtractorTabContent(
                            viewModel = viewModel,
                            searchState = searchState,
                            selectedFormat = selectedFormat,
                            selectedBitrate = selectedBitrate,
                            selectedServer = selectedServer,
                            clipboardManager = clipboardManager,
                            onGoToLibrary = { activeTab = 1 }
                        )
                    } else {
                        LibraryTabContent(
                            viewModel = viewModel,
                            downloadHistory = downloadHistory,
                            playingAudioId = playerState.audioId,
                            context = context
                        )
                    }
                }
            }

            // Beautiful Full-Screen player expansion
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) + fadeOut()
            ) {
                FullScreenAudioPlayer(
                    playerState = playerState,
                    viewModel = viewModel,
                    onCollapse = { isPlayerExpanded = false }
                )
            }
        }
    }
}

@Composable
fun ExtractorTabContent(
    viewModel: MainViewModel,
    searchState: SearchState,
    selectedFormat: String,
    selectedBitrate: String,
    selectedServer: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onGoToLibrary: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Paste YouTube Video or Shorts link",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Custom High-End Outlined Paste Input
        OutlinedTextField(
            value = searchState.urlInput,
            onValueChange = { viewModel.onUrlChange(it) },
            placeholder = { Text("https://www.youtube.com/watch?v=...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchState.urlInput.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearSearch() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Input")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("url_input_field")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Direct Clipboard quickpaste and Parse buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val clipText = clipboardManager.getText()?.text
                    if (!clipText.isNullOrEmpty()) {
                        viewModel.onUrlChange(clipText)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("paste_clipboard_button")
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Clipboard")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Paste", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.parseUrl() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("parse_link_button")
            ) {
                if (searchState.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(Icons.Default.YoutubeSearchedFor, contentDescription = "Parse")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Parse Link", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Show parsing errors in premium crimson red accent
        searchState.error?.let { errorText ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // YouTube video result display drawer
        AnimatedContent(
            targetState = searchState.activeMetadata,
            transitionSpec = {
                slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
            },
            label = "MetadataDisplay"
        ) { meta ->
            if (meta != null) {
                ParsedVideoCard(
                    meta = meta,
                    selectedFormat = selectedFormat,
                    selectedBitrate = selectedBitrate,
                    selectedServer = selectedServer,
                    viewModel = viewModel,
                    onDownloadTriggered = onGoToLibrary
                )
            } else {
                // If idle, show instructions/intro card in premium slate
                InstructionAuraCard()
            }
        }
    }
}

@Composable
fun InstructionAuraCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Instruction Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Studio Audio Extractor",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Download premium 320kbps MP3 or M4A direct streams natively with zero sound compression and zero trackers.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CheckCircle, "check", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("320Kbps HD", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Speed, "speed", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Super Fast", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.NoEncryption, "adfree", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Zero Ads", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun ParsedVideoCard(
    meta: VideoMetadata,
    selectedFormat: String,
    selectedBitrate: String,
    selectedServer: String,
    viewModel: MainViewModel,
    onDownloadTriggered: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .testTag("parsed_result_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Video Preview section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = meta.thumbnailUrl,
                    contentDescription = meta.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(90.dp, 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meta.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = meta.author,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))

            // Config format selection
            Text("Audio Output Format", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.formatList.forEach { format ->
                    val isSelected = selectedFormat == format
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.selectedFormat.value = format }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = format.uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Config Bitrate selection
            Text("Audio Bitrate Density", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("320" to "320k", "256" to "256k", "128" to "128k", "64" to "64k").forEach { (valStr, dispStr) ->
                    val isSelected = selectedBitrate == valStr
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.selectedBitrate.value = valStr }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dispStr,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Server Selection row
            Text("Bypass Server Instance", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.serverList.forEach { (srvUrl, srvLabel) ->
                    val isSelected = selectedServer == srvUrl
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedServer.value = srvUrl },
                        label = { Text(srvLabel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Download Trigger button
            Button(
                onClick = {
                    viewModel.startAudioDownload(meta)
                    onDownloadTriggered()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_download_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EXTRACT & DOWNLOAD AUDIO",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun LibraryTabContent(
    viewModel: MainViewModel,
    downloadHistory: List<DownloadedAudio>,
    playingAudioId: String?,
    context: Context
) {
    if (downloadHistory.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Empty Library",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your Library is empty",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Extracted tracks will show up here as they load. You can play, edit, search, and export them directly.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = downloadHistory, key = { it.id }) { audio ->
                LibraryTrackCard(
                    audio = audio,
                    isPlaying = playingAudioId == audio.id,
                    onPlay = { viewModel.playAudio(audio) },
                    onDelete = { viewModel.deleteAudio(audio) },
                    onShare = { shareDownloadedFile(context, audio) }
                )
            }
        }
    }
}

@Composable
fun LibraryTrackCard(
    audio: DownloadedAudio,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .testTag("track_item_${audio.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Thumbnail loading
                AsyncImage(
                    model = audio.thumbnailUrl,
                    contentDescription = "Audio track thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(75.dp, 50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = audio.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = audio.author,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Track details badge capsules (Bitrate & Format)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${audio.format.uppercase()} · ${audio.bitrate}kbps",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp
                            )
                        }

                        if (audio.size > 0 && audio.status == "COMPLETED") {
                            Text(
                                text = formatFileSize(audio.size),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Controls inside state
                when (audio.status) {
                    "COMPLETED" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onShare) {
                                Icon(Icons.Default.Share, contentDescription = "Share File", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = onPlay) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Play Track",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                            }
                        }
                    }
                    "QUEUED", "DOWNLOADING" -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel Download", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        }
                    }
                    "FAILED" -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Record", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Real-time downloading progress bar
            if (audio.status == "DOWNLOADING" || audio.status == "QUEUED") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (audio.status == "QUEUED") "Connecting..." else "${audio.progress}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(54.dp)
                    )
                    
                    if (audio.status == "DOWNLOADING" && audio.progress >= 0) {
                        LinearProgressIndicator(
                            progress = { audio.progress / 100f },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = audio.speed,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.61f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(80.dp),
                        maxLines = 1
                    )
                }
            } else if (audio.status == "FAILED") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Error, "error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = audio.speed, // Stores exception message
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MiniAudioPlayerBar(
    playerState: com.example.player.PlayerState,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clickable(onClick = onExpand)
            .testTag("floating_mini_player")
    ) {
        Column {
            // Seek micro progress bar
            val progressFactor = if (playerState.duration > 0) playerState.currentPosition.toFloat() / playerState.duration else 0f
            LinearProgressIndicator(
                progress = { progressFactor },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = playerState.thumbnailUrl,
                    contentDescription = "Playing track image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(45.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playerState.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playerState.author,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/pause floating bar toggle",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Expand Player",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun FullScreenAudioPlayer(
    playerState: com.example.player.PlayerState,
    viewModel: MainViewModel,
    onCollapse: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .testTag("full_player_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "STUDIO PLAYER",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = {}, enabled = false) {
                    Spacer(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // Giant artwork
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .size(260.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = playerState.thumbnailUrl,
                        contentDescription = "Artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Golden overlay hue
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title and author Info
            Text(
                text = playerState.title,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("full_player_title")
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = playerState.author,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(0.5f))

            // Seeker Slider
            val seekerValue = if (playerState.duration > 0) playerState.currentPosition.toFloat() else 0f
            Slider(
                value = seekerValue,
                valueRange = 0f..(if (playerState.duration > 0) playerState.duration.toFloat() else 100f),
                onValueChange = { viewModel.audioPlayer.seekTo(it.toInt()) },
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thumbColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_seek_slider")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(playerState.currentPosition),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatTime(playerState.duration),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controller Playback Panel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                IconButton(onClick = { viewModel.audioPlayer.seekBackward() }) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Forward back 10 seconds",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Main Play/Pause container with glowing ripple background effect
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { viewModel.audioPlayer.togglePlayPause() }
                        .testTag("full_player_play_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play toggle complete card",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = { viewModel.audioPlayer.seekForward() }) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Skip ahead 10 seconds",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))
        }
    }
}

// Format logic helpers
private fun formatTime(ms: Int): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun shareDownloadedFile(context: Context, audio: DownloadedAudio) {
    val file = File(audio.localPath)
    if (!file.exists()) {
        Log.e("MainScreen", "Download file does not exist at path: ${audio.localPath}")
        return
    }

    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Extracted Audio"))
    } catch (e: Exception) {
        Log.e("MainScreen", "Share action failed", e)
    }
}
