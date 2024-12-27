package com.example.musicplayer

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var audioManager: AudioManager
    private var audioFocusGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager


        mediaPlayer = MediaPlayer()

        setContent {
            MusicApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { focusChange -> handleAudioFocusChange(focusChange) }
                .build()

            audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }


    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> mediaPlayer.pause()
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!mediaPlayer.isPlaying) mediaPlayer.start()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                mediaPlayer.pause()
                audioFocusGranted = false
            }
        }
    }

    @Composable
    fun MusicApp() {
        var musicData by remember { mutableStateOf<List<Data>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var searchQuery by remember { mutableStateOf("") }
        var currentIndex by remember { mutableIntStateOf(-1) }

        val retrofitBuilder = Retrofit.Builder()
            .baseUrl("https://deezerdevs-deezer.p.rapidapi.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiInterface::class.java)

        LaunchedEffect(Unit) {
            isLoading = true
            val defaultQuery = "top hits"
            retrofitBuilder.getData(defaultQuery).enqueue(object : Callback<MyData> {
                override fun onResponse(call: Call<MyData>, response: Response<MyData>) {
                    musicData = response.body()?.data ?: emptyList()
                    isLoading = false
                }

                override fun onFailure(call: Call<MyData>, t: Throwable) {
                    isLoading = false
                    Log.e("MusicApp", "Error: ${t.message}")
                }
            })
        }

        LaunchedEffect(searchQuery) {
            if (searchQuery.isNotBlank()) {
                isLoading = true
                retrofitBuilder.getData(searchQuery).enqueue(object : Callback<MyData> {
                    override fun onResponse(call: Call<MyData>, response: Response<MyData>) {
                        musicData = response.body()?.data ?: emptyList()
                        isLoading = false
                    }

                    override fun onFailure(call: Call<MyData>, t: Throwable) {
                        isLoading = false
                        Log.e("MusicApp", "Error: ${t.message}")
                    }
                })
            }
        }

        val currentSong = if (currentIndex >= 0 && currentIndex < musicData.size) {
            musicData[currentIndex]
        } else null

        if (currentSong != null) {
            MusicPlayerScreen(
                currentSong = currentSong,
                musicData = musicData,
                currentIndex = currentIndex,
                onSongChange = { newIndex ->
                    currentIndex = newIndex
                    playSong(musicData[newIndex].preview)
                },
                onClose = {
                    currentIndex = -1
                    stopPlayback()
                }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchBar(searchQuery, onQueryChange = { searchQuery = it })

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    MusicList(
                        dataList = musicData,
                        onItemClick = { index ->
                            currentIndex = index
                            playSong(musicData[index].preview)
                        }
                    )
                }
            }
        }
    }

    private fun playSong(previewUrl: String) {
        try {

            resetPlayer()


            if (requestAudioFocus()) {
                audioFocusGranted = true


                mediaPlayer.setDataSource(previewUrl)
                mediaPlayer.prepareAsync()

                mediaPlayer.setOnPreparedListener {
                    mediaPlayer.start()
                }
            } else {
                Log.e("MusicApp", "Failed to gain audio focus")
            }
        } catch (e: Exception) {
            Log.e("MusicApp", "Error in playSong: ${e.message}")
        }
    }


    private lateinit var audioFocusRequest: AudioFocusRequest

    private fun stopPlayback() {
        try {

            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.reset() // Reset the player state


            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && ::audioFocusRequest.isInitialized) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }

            audioFocusGranted = false
        } catch (e: Exception) {
            Log.e("MusicApp", "Error in stopPlayback: ${e.message}")
        }
    }


    private fun resetPlayer() {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.reset()
        }
    }


    @Composable
    fun SearchBar(searchQuery: String, onQueryChange: (String) -> Unit) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Search artist or song") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }

    @Composable
    fun MusicList(dataList: List<Data>, onItemClick: (Int) -> Unit) {
        if (dataList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No songs found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(dataList) { item ->
                    MusicCard(
                        data = item,
                        onClick = { onItemClick(dataList.indexOf(item)) }
                    )
                }
            }
        }
    }

    @Composable
    fun MusicCard(data: Data, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberAsyncImagePainter(data.album.cover),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                ) {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = data.artist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    fun MusicPlayerScreen(
        currentSong: Data,
        musicData: List<Data>,
        currentIndex: Int,
        onSongChange: (Int) -> Unit,
        onClose: () -> Unit
    ) {
        var isPlaying by remember { mutableStateOf(false) }
        var playbackPosition by remember { mutableFloatStateOf(0f) }
        var totalDuration by remember { mutableFloatStateOf(0f) }
        var formattedTime by remember { mutableStateOf("00:00") }

        LaunchedEffect(currentIndex) {
            playbackPosition = 0f
            totalDuration = currentSong.duration.toFloat()
            mediaPlayer.reset()
            mediaPlayer.setDataSource(currentSong.preview)
            mediaPlayer.prepare()
            mediaPlayer.start()
            isPlaying = true
        }

        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                while (playbackPosition < totalDuration && mediaPlayer.isPlaying) {
                    playbackPosition = mediaPlayer.currentPosition / 1000f
                    formattedTime = formatTime(playbackPosition.toInt())
                    kotlinx.coroutines.delay(100)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            IconButton(onClick = { onClose() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to List",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(currentSong.album.cover),
                    contentDescription = "Album Cover",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = currentSong.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong.artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            onSongChange(currentIndex - 1)
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(
                    onClick = {

                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(
                    onClick = {
                        if (currentIndex < musicData.size - 1) {
                            onSongChange(currentIndex + 1)
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PlaybackProgressBar(
                progress = playbackPosition,
                totalDuration = totalDuration,
                formattedTime = formattedTime,
                onSeek = { newPosition ->
                    playbackPosition = newPosition
                    mediaPlayer.seekTo((newPosition * 1000).toInt())
                }
            )
        }
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }



    @Composable
    fun PlaybackProgressBar(
        progress: Float,
        totalDuration: Float,
        formattedTime: String,
        onSeek: (Float) -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.weight(1f))


            Slider(
                value = progress,
                onValueChange = { onSeek(it) },
                valueRange = 0f..totalDuration,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "%.1f".format(totalDuration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}