package com.example.musicplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicApp()
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
                onSongChange = { newIndex -> currentIndex = newIndex },
                onClose = { currentIndex = -1 }
            )
        } else {

            Column {

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
                        onItemClick = { index -> currentIndex = index }
                    )
                }
            }
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
            },
//            colors = TextFieldDefaults.outlinedTextFieldColors(
//                focusedBorderColor = MaterialTheme.colorScheme.primary,
//                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
//                focusedLabelColor = MaterialTheme.colorScheme.primary,
//                cursorColor = MaterialTheme.colorScheme.primary // Add cursor color
//            )
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
                items(dataList.size) { index ->
                    MusicCard(
                        data = dataList[index],
                        onClick = { onItemClick(index) }
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

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            // Song Info
            Text(text = currentSong.title, style = MaterialTheme.typography.headlineMedium)
            Text(text = currentSong.artist.name, style = MaterialTheme.typography.bodyMedium)

            // Song Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (currentIndex > 0) onSongChange(currentIndex - 1) }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }

                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(onClick = { if (currentIndex < musicData.size - 1) onSongChange(currentIndex + 1) }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Close Button
            IconButton(onClick = { onClose() }) {
                Icon(Icons.Default.Close, contentDescription = "Close Player")
            }
        }
    }
}
