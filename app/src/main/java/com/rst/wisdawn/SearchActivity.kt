package com.rst.wisdawn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.*

// --- ViewModel ---

class SearchViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _allPosts = MutableStateFlow<List<DoubtPost>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // Smart Search Logic using Keyword Scoring
    val smartResults = combine(_searchQuery, _allPosts) { query, posts ->
        if (query.isBlank()) return@combine emptyList<Pair<DoubtPost, Int>>()

        // Tokenize query and remove tiny "stop words"
        val tokens = query.lowercase().trim().split("\\s+".toRegex())
            .filter { it.length > 2 }

        posts.map { post ->
            var score = 0
            val questionLower = post.question.lowercase()
            val authorLower = post.author.lowercase()
            val tagsLower = post.tags.map { it.lowercase() }

            // 1. Exact Phrase Match (Highest Priority)
            if (questionLower.contains(query.lowercase())) score += 100

            // 2. Keyword matching in Question
            tokens.forEach { token ->
                if (questionLower.contains(token)) score += 20
            }

            // 3. Tag matching (High Priority)
            post.tags.forEach { tag ->
                if (tag.lowercase() == query.lowercase()) score += 80
                tokens.forEach { token ->
                    if (tag.lowercase().contains(token)) score += 15
                }
            }

            // 4. Author matching
            if (authorLower.contains(query.lowercase())) score += 50

            // Return a Pair mapping the post to its relevance score
            post to score
        }
            .filter { it.second > 0 } // Only keep posts with at least one match
            .sortedByDescending { it.second } // Sort by highest relevance score
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        fetchAllPosts()
    }

    private fun fetchAllPosts() {
        firestore.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    _allPosts.value = snapshot.documents.mapNotNull { it.toObject(DoubtPost::class.java) }
                }
                _isLoading.value = false
            }
    }

    fun updateQuery(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun toggleLike(postId: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return

        val postRef = firestore.collection("posts").document(postId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)
            val currentLikedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()
            val isLiked = currentLikedBy.contains(uid)

            if (isLiked) {
                transaction.update(postRef, "likedBy", FieldValue.arrayRemove(uid))
                transaction.update(postRef, "upvotes", FieldValue.increment(-1))
            } else {
                transaction.update(postRef, "likedBy", FieldValue.arrayUnion(uid))
                transaction.update(postRef, "upvotes", FieldValue.increment(1))
            }
        }.addOnFailureListener { e -> e.printStackTrace() }
    }
}

// --- Activity ---

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color.Black, background = Color(0xFFF5F5F5),
                    surface = Color.White, onPrimary = Color.White,
                    onBackground = Color.Black, onSurface = Color.Black
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Reusing the same animated background and grain from DashboardActivity
                    DashboardAnimatedBackground()
                    Box(modifier = Modifier.fillMaxSize().background(rememberDashboardGrain()))

                    SearchScreen(onBack = { finish() })
                }
            }
        }
    }
}

// --- UI Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, viewModel: SearchViewModel = viewModel()) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val rankedResults by viewModel.smartResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Search Bar Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashboardGlassCard()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black,
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)
                )

                BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateQuery(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Color.Black),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Search questions, subjects, or users...", color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.Black,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.updateQuery("") }
                            .padding(4.dp)
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = Color.Gray)
                }
            }
        }

        // --- Results Area ---
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Black)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (rankedResults.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            Text("No matching doubts found.", color = Color.Gray)
                        }
                    }
                } else if (searchQuery.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.TopCenter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Type to start searching", color = Color.Gray, fontSize = 16.sp)
                            }
                        }
                    }
                } else {
                    items(rankedResults) { (post, score) ->

                        // "BEST MATCH" badge for highly relevant posts
                        if (score >= 80 && rankedResults.first().first.id == post.id) {
                            Text(
                                text = "BEST MATCH",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }

                        DoubtCard(
                            doubt = post,
                            currentUserId = viewModel.currentUserId,
                            onLikeClick = { viewModel.toggleLike(post.id) },
                            onDiscussClick = {
                                val intent = Intent(context, DiscussionActivity::class.java).apply {
                                    putExtra("POST_ID", post.id)
                                }
                                context.startActivity(intent)
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}