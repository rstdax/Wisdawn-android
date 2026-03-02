package com.rst.wisdawn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Random
import java.util.UUID
import com.google.ai.client.generativeai.GenerativeModel
import org.json.JSONObject

class DashboardActivity : ComponentActivity() {
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
                    DashboardAnimatedBackground()
                    Box(modifier = Modifier.fillMaxSize().background(rememberDashboardGrain()))
                    DashboardNavGraph()
                }
            }
        }
    }
}

// --- 1. Data Models ---




data class McqQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class DoubtPost(
    val id: String = "",
    val authorId: String = "",
    val author: String = "",
    val authorAvatar: String = "",
    val question: String = "",
    val tags: List<String> = emptyList(),
    val upvotes: Int = 0,
    val likedBy: List<String> = emptyList(),
    val replies: Int = 0,
    val imageUrl: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class WorkshopChapter(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val imageUrl: String? = null,
    val pdfUrl: String? = null,
    val videoUrl: String? = null
)

data class Workshop(
    val id: String = "",
    val title: String = "",
    val tag: String = "",
    val authorId: String = "", // Added for My Uploads tracking
    val author: String = "",
    val authorAvatar: String = "",
    val chapters: List<WorkshopChapter> = emptyList(),
    val attendees: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class FocusGoal(val id: String = UUID.randomUUID().toString(), val text: String, var isDone: Boolean = false)

data class ChapterDraftState(
    val title: String = "",
    val imageUri: Uri? = null,
    val pdfUri: Uri? = null,
    val videoUri: Uri? = null
)

// --- 2. ViewModel ---

class DashboardViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile = _currentUserProfile.asStateFlow()

    private val _globalDoubts = MutableStateFlow<List<DoubtPost>>(emptyList())
    val globalDoubts = _globalDoubts.asStateFlow()

    private val _localDoubts = MutableStateFlow<List<DoubtPost>>(emptyList())
    val localDoubts = _localDoubts.asStateFlow()

    private val _globalWorkshops = MutableStateFlow<List<Workshop>>(emptyList())
    val globalWorkshops = _globalWorkshops.asStateFlow()

    private val _localWorkshops = MutableStateFlow<List<Workshop>>(emptyList())
    val localWorkshops = _localWorkshops.asStateFlow()

    // --- Library States ---
    private val _uniqueTags = MutableStateFlow<List<String>>(emptyList())
    val uniqueTags = _uniqueTags.asStateFlow()

    private val _savedPosts = MutableStateFlow<List<DoubtPost>>(emptyList())
    val savedPosts = _savedPosts.asStateFlow()

    private val _enrolledWorkshops = MutableStateFlow<List<Pair<Workshop, Float>>>(emptyList())
    val enrolledWorkshops = _enrolledWorkshops.asStateFlow()

    private val _recommendedWorkshops = MutableStateFlow<List<Workshop>>(emptyList())
    val recommendedWorkshops = _recommendedWorkshops.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    var currentMediaUrl by mutableStateOf<String>("")
    private var hasAddedAppOpenXp = false

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        fetchCurrentUserAndData()
        fetchLibraryData()
    }

    private fun fetchCurrentUserAndData() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                _currentUserProfile.value = profile

                // Track 1 Point for App Open (once per session)
                if (!hasAddedAppOpenXp) {
                    hasAddedAppOpenXp = true
                    firestore.collection("users").document(uid).update("xp", FieldValue.increment(1))
                }

                if (_globalDoubts.value.isEmpty()) {
                    fetchPosts(profile?.latitude ?: 0.0, profile?.longitude ?: 0.0)
                }
            }
        }
    }

    private fun fetchPosts(userLat: Double, userLon: Double) {
        firestore.collection("posts").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val allDoubts = snapshot.documents.mapNotNull { it.toObject(DoubtPost::class.java) }
                    _globalDoubts.value = allDoubts
                    _localDoubts.value = allDoubts.filter {
                        isWithin10Km(userLat, userLon, it.latitude, it.longitude)
                    }
                }
            }

        firestore.collection("workshops").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val allWorkshops = snapshot.documents.mapNotNull { it.toObject(Workshop::class.java) }
                    _globalWorkshops.value = allWorkshops
                    _localWorkshops.value = allWorkshops.filter {
                        isWithin10Km(userLat, userLon, it.latitude, it.longitude)
                    }
                }
                _isLoading.value = false
            }
    }

    private fun fetchLibraryData() {
        viewModelScope.launch {
            combine(_currentUserProfile.filterNotNull(), _globalDoubts) { profile, posts ->
                val allTags = posts.flatMap { it.tags }.map { it.trim().uppercase() }
                _uniqueTags.value = allTags.distinct().sorted()
                _savedPosts.value = posts.filter { profile.savedPosts.contains(it.id) }
            }.collect {}
        }

        viewModelScope.launch {
            combine(_currentUserProfile.filterNotNull(), _globalWorkshops) { profile, workshops ->
                _enrolledWorkshops.value = workshops.filter { profile.enrolledWorkshops.containsKey(it.id) }
                    .map { it to (profile.enrolledWorkshops[it.id] ?: 0f) }

                _recommendedWorkshops.value = workshops.sortedByDescending { it.attendees }
            }.collect {}
        }
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

    fun toggleSavePost(postId: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        val profile = _currentUserProfile.value ?: return
        val userRef = firestore.collection("users").document(uid)

        if (profile.savedPosts.contains(postId)) {
            userRef.update("savedPosts", FieldValue.arrayRemove(postId))
        } else {
            userRef.update("savedPosts", FieldValue.arrayUnion(postId))
        }
    }

    fun enrollWorkshop(workshopId: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        val userRef = firestore.collection("users").document(uid)
        val workshopRef = firestore.collection("workshops").document(workshopId)

        firestore.runBatch { batch ->
            batch.update(userRef, "enrolledWorkshops.$workshopId", 0.0f)
            batch.update(userRef, "xp", FieldValue.increment(5)) // Track +5 XP for Joining Workshop
            batch.update(workshopRef, "attendees", FieldValue.increment(1))
        }.addOnFailureListener { it.printStackTrace() }
    }

    fun addFocusTime(seconds: Long) {
        val uid = currentUserId
        if (uid.isEmpty() || seconds <= 0) return
        firestore.collection("users").document(uid).update("focusedSeconds", FieldValue.increment(seconds))
    }

    // Call this from DiscussionActivity when the user adds a reply/comment
    fun incrementHelpedCount() {
        val uid = currentUserId
        if (uid.isNotEmpty()) {
            firestore.collection("users").document(uid).update("helpedCount", FieldValue.increment(1))
            firestore.collection("users").document(uid).update("xp", FieldValue.increment(1))
        }
    }

    fun uploadDoubt(question: String, tags: List<String>, imageUri: Uri?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = _currentUserProfile.value ?: return onError("User profile not loaded")
        val postId = UUID.randomUUID().toString()
        val uid = currentUserId

        viewModelScope.launch {
            try {
                var imageUrl: String? = null
                if (imageUri != null) {
                    val ref = storage.reference.child("post_images/$postId.jpg")
                    ref.putFile(imageUri).await()
                    imageUrl = ref.downloadUrl.await().toString()
                }

                val doubt = DoubtPost(
                    id = postId, authorId = uid, author = user.fullName, authorAvatar = user.avatar, question = question, tags = tags,
                    imageUrl = imageUrl, latitude = user.latitude, longitude = user.longitude
                )

                firestore.collection("posts").document(postId).set(doubt).await()
                onSuccess()
            } catch (e: Exception) { onError(e.message ?: "Upload failed") }
        }
    }

    fun uploadWorkshop(title: String, tag: String, chaptersRaw: List<ChapterDraftState>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = _currentUserProfile.value ?: return onError("User profile not loaded")
        val workshopId = UUID.randomUUID().toString()
        val uid = currentUserId

        viewModelScope.launch {
            try {
                val processedChapters = kotlinx.coroutines.coroutineScope {
                    chaptersRaw.map { draft ->
                        async {
                            var imgUrl: String? = null
                            var pdfUrl: String? = null
                            var vidUrl: String? = null

                            if (draft.imageUri != null) {
                                val ref = storage.reference.child("workshop_assets/$workshopId/${UUID.randomUUID()}.jpg")
                                ref.putFile(draft.imageUri!!).await()
                                imgUrl = ref.downloadUrl.await().toString()
                            }
                            if (draft.pdfUri != null) {
                                val ref = storage.reference.child("workshop_assets/$workshopId/${UUID.randomUUID()}.pdf")
                                ref.putFile(draft.pdfUri!!).await()
                                pdfUrl = ref.downloadUrl.await().toString()
                            }
                            if (draft.videoUri != null) {
                                val ref = storage.reference.child("workshop_assets/$workshopId/${UUID.randomUUID()}.mp4")
                                ref.putFile(draft.videoUri!!).await()
                                vidUrl = ref.downloadUrl.await().toString()
                            }

                            WorkshopChapter(
                                id = UUID.randomUUID().toString(),
                                title = draft.title,
                                imageUrl = imgUrl,
                                pdfUrl = pdfUrl,
                                videoUrl = vidUrl
                            )
                        }
                    }.awaitAll()
                }

                val workshop = Workshop(
                    id = workshopId, title = title, tag = tag, authorId = uid, author = user.fullName, authorAvatar = user.avatar,
                    chapters = processedChapters, attendees = 0,
                    latitude = user.latitude, longitude = user.longitude
                )

                firestore.collection("workshops").document(workshopId).set(workshop).await()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                if (e is kotlinx.coroutines.CancellationException) throw e
                onError(e.message ?: "Failed to publish workshop")
            }
        }
    }

    fun updateProfile(newName: String, newAvatar: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("User not logged in")
        viewModelScope.launch {
            try {
                val updates = mapOf("fullName" to newName, "avatar" to newAvatar)
                firestore.collection("users").document(uid).update(updates).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update profile")
            }
        }
    }
}

class QuestViewModel : ViewModel() {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "AIzaSyDDhhaNUrkASV0LxVbUcGjOpqePriF_skA"
    )

    var currentQuestion by mutableStateOf<McqQuestion?>(null)
    var isGenerating by mutableStateOf(false)
    var selectedOptionIndex by mutableStateOf<Int?>(null)
    var isEvaluated by mutableStateOf(false)
    var userXp by mutableStateOf(8450)
    var feedbackMessage by mutableStateOf("")

    fun generateQuestion(topic: String, difficulty: String = "Intermediate") {
        viewModelScope.launch {
            isGenerating = true
            selectedOptionIndex = null
            isEvaluated = false
            currentQuestion = null
            feedbackMessage = ""

            val prompt = """
                Generate ONE multiple-choice question about $topic at a $difficulty level.
                Return ONLY a raw JSON object. Do not include markdown formatting like ```json.
                Structure:
                {
                  "question": "text",
                  "options": ["A", "B", "C", "D"],
                  "correctIndex": 0,
                  "explanation": "text"
                }
            """.trimIndent()

            try {
                val response = generativeModel.generateContent(prompt)
                val rawText = response.text ?: ""
                val cleanJson = rawText.removePrefix("```json").removeSuffix("```").trim()

                if (cleanJson.isNotEmpty()) {
                    val jsonObject = JSONObject(cleanJson)
                    val optionsArray = jsonObject.getJSONArray("options")
                    val optionsList = List(optionsArray.length()) { i -> optionsArray.getString(i) }

                    currentQuestion = McqQuestion(
                        question = jsonObject.getString("question"),
                        options = optionsList,
                        correctIndex = jsonObject.getInt("correctIndex"),
                        explanation = jsonObject.getString("explanation")
                    )
                }
            } catch (e: Exception) {
                feedbackMessage = "Error: ${e.localizedMessage}"
                Log.e("QuestVM", "Gemini Error", e)
            } finally {
                isGenerating = false
            }
        }
    }

    fun evaluateAnswer() {
        if (selectedOptionIndex == null || currentQuestion == null) return
        isEvaluated = true
        if (selectedOptionIndex == currentQuestion?.correctIndex) {
            userXp += 50
            feedbackMessage = "Correct! +50 XP"
        } else {
            feedbackMessage = "Incorrect. Better luck next time!"
        }
    }
}

fun isWithin10Km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
    if (lat1 == 0.0 && lon1 == 0.0) return true
    val earthRadius = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return (earthRadius * c) <= 10.0
}

fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

// --- 3. Navigation & Scaffold ---

@Composable
fun DashboardNavGraph(viewModel: DashboardViewModel = viewModel(), questViewModel: QuestViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val hideBottomBarRoutes = listOf("add_content", "edit_profile", "workshop_detail/{workshopId}", "video_viewer", "image_viewer")

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { if (currentRoute !in hideBottomBarRoutes) GlassBottomNavBar(navController) },
        floatingActionButton = {
            if (currentRoute !in hideBottomBarRoutes && currentRoute != "profile") {
                FloatingActionButton(
                    onClick = { navController.navigate("add_content") },
                    containerColor = Color.Black, contentColor = Color.White,
                    shape = CircleShape, modifier = Modifier.padding(bottom = 16.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "Add Content") }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            composable("home") { HomeScreen(viewModel, navController) }
            composable("add_content") { AddContentScreen(viewModel, onBack = { navController.popBackStack() }) }
            composable("library") { LibraryScreen(viewModel, navController) }
            composable("focus") { FocusScreen(viewModel, navController) }
            composable("quest") { QuestScreen(questViewModel, navController) }
            composable("profile") { ProfileScreen(viewModel, navController) }
            composable("edit_profile") { EditProfileScreen(viewModel, onBack = { navController.popBackStack() }) }

            composable("workshop_detail/{workshopId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("workshopId")
                WorkshopDetailScreen(viewModel, id, navController)
            }
            composable("video_viewer") {
                VideoPlayerScreen(viewModel.currentMediaUrl) { navController.popBackStack() }
            }
            composable("image_viewer") {
                ImageViewerScreen(viewModel.currentMediaUrl) { navController.popBackStack() }
            }
        }
    }
}

// --- 4. Screens ---

@Composable
fun HomeScreen(viewModel: DashboardViewModel, navController: NavController) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Locality") }
    val userProfile by viewModel.currentUserProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val doubts by if (selectedTab == "Locality") viewModel.localDoubts.collectAsState() else viewModel.globalDoubts.collectAsState()
    val workshops by if (selectedTab == "Locality") viewModel.localWorkshops.collectAsState() else viewModel.globalWorkshops.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Good Morning,", fontSize = 16.sp, color = Color.DarkGray)
                    Text("${userProfile?.fullName?.substringBefore(" ") ?: "User"} \uD83D\uDC4B", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black, letterSpacing = (-1).sp)
                }
                Box(modifier = Modifier.size(48.dp).dashboardGlassCard().dashboardBouncyClick {
                    context.startActivity(Intent(context, SearchActivity::class.java))
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Black)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).dashboardGlassCard().padding(4.dp)) {
                listOf("Locality", "Global").forEach { tab ->
                    val isSelected = selectedTab == tab
                    val bgColor by animateColorAsState(if (isSelected) Color.Black else Color.Transparent, label = "bg")
                    val textColor by animateColorAsState(if (isSelected) Color.White else Color.DarkGray, label = "text")

                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(bgColor).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedTab = tab }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(tab, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (isLoading) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Black) } }
        } else {
            if (workshops.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF6200EA)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Workshops", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(workshops) { workshop ->
                            val isEnrolled = userProfile?.enrolledWorkshops?.containsKey(workshop.id) == true
                            WorkshopCard(
                                workshop = workshop,
                                isEnrolled = isEnrolled,
                                onJoinClick = {
                                    viewModel.enrollWorkshop(workshop.id)
                                    Toast.makeText(context, "Joined ${workshop.title}", Toast.LENGTH_SHORT).show()
                                },
                                onClick = { navController.navigate("workshop_detail/${workshop.id}") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Trending Doubts", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.Gray)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (doubts.isEmpty()) {
                item { Text("No posts found here yet. Be the first!", modifier = Modifier.padding(24.dp), color = Color.Gray) }
            } else {
                items(doubts) { doubt ->
                    val isSaved = userProfile?.savedPosts?.contains(doubt.id) == true
                    DoubtCard(
                        doubt = doubt,
                        currentUserId = viewModel.currentUserId,
                        isSaved = isSaved,
                        onLikeClick = { viewModel.toggleLike(doubt.id) },
                        onDiscussClick = {
                            val intent = Intent(context, DiscussionActivity::class.java).apply {
                                putExtra("POST_ID", doubt.id)
                            }
                            context.startActivity(intent)
                        },
                        onSaveClick = { viewModel.toggleSavePost(doubt.id) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// ... LibraryScreen and its child components remain unchanged structurally ...
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(viewModel: DashboardViewModel, navController: NavController) {
    val context = LocalContext.current
    val enrolled by viewModel.enrolledWorkshops.collectAsState()
    val saved by viewModel.savedPosts.collectAsState()
    val tags by viewModel.uniqueTags.collectAsState()
    val recommended by viewModel.recommendedWorkshops.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp)) {

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Your Library", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black, letterSpacing = (-1).sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pick up where you left off or discover resources.", fontSize = 12.sp, color = Color.DarkGray)
                }
                Box(
                    modifier = Modifier.size(48.dp).dashboardGlassCard().dashboardBouncyClick {
                        context.startActivity(Intent(context, SearchActivity::class.java))
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Black)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (enrolled.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue Learning", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(enrolled) { (workshop, progress) ->
                        ContinueLearningCard(workshop, progress) {
                            navController.navigate("workshop_detail/${workshop.id}")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (saved.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saved Resources", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                val savedItems = saved.take(4).chunked(2)
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    savedItems.forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { post ->
                                SavedResourceCard(
                                    modifier = Modifier.weight(1f),
                                    title = post.question,
                                    type = if (post.imageUrl != null) "IMAGE" else "TEXT",
                                    date = formatTimeAgo(post.timestamp)
                                ) {
                                    val intent = Intent(context, DiscussionActivity::class.java).apply {
                                        putExtra("POST_ID", post.id)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                            if (rowItems.size == 1) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (tags.isNotEmpty()) {
            item {
                Text("Explore by Subject", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tags) { tag ->
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black).dashboardBouncyClick {
                                val intent = Intent(context, SearchActivity::class.java).apply { putExtra("INITIAL_QUERY", tag) }
                                context.startActivity(intent)
                            }.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(tag, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (recommended.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recommended", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(recommended) { workshop ->
                        RecommendedWorkshopCard(workshop) { navController.navigate("workshop_detail/${workshop.id}") }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueLearningCard(workshop: Workshop, progress: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(280.dp).dashboardGlassCard().dashboardBouncyClick { onClick() }.padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (workshop.authorAvatar.isNotEmpty()) {
                AsyncImage(model = workshop.authorAvatar, contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(workshop.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(workshop.author, fontSize = 12.sp, color = Color.DarkGray)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${(progress * 100).toInt()}% Complete", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Text(formatTimeAgo(workshop.timestamp), fontSize = 10.sp, color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.LightGray.copy(alpha=0.5f))) {
            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color(0xFF2563EB)))
        }
    }
}

@Composable
fun SavedResourceCard(modifier: Modifier = Modifier, title: String, type: String, date: String, onClick: () -> Unit) {
    Row(modifier = modifier.dashboardGlassCard().dashboardBouncyClick { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (type == "IMAGE") Color(0xFFFEE2E2) else Color(0xFFFEF3C7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (type == "IMAGE") Icons.Outlined.Image else Icons.Outlined.Description,
                contentDescription = null,
                tint = if (type == "IMAGE") Color(0xFFDC2626) else Color(0xFFD97706),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(type, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text(" • $date", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun RecommendedWorkshopCard(workshop: Workshop, onClick: () -> Unit) {
    Row(modifier = Modifier.width(300.dp).dashboardGlassCard().dashboardBouncyClick { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (workshop.chapters.firstOrNull()?.imageUrl != null) {
            AsyncImage(model = workshop.chapters.first().imageUrl, contentDescription = "Cover", modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(workshop.tag.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(workshop.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("4.9", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            }
        }
    }
}

// ... Additional viewers & AddContent components untouched ...

fun openPdfView(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF with"))
    } catch (e: Exception) {
        Toast.makeText(context, "No PDF viewer found. Please install one.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ImageViewerScreen(url: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(model = url, contentDescription = "Image Viewer", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        IconButton(onClick = onBack, modifier = Modifier.padding(24.dp).align(Alignment.TopStart).background(Color.Black.copy(0.5f), CircleShape)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerScreen(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    val exoPlayer = remember {
        ExoPlayer.Builder(context).setHandleAudioBecomingNoisy(true).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insetsController?.apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        onDispose {
            exoPlayer.release()
            activity?.requestedOrientation = originalOrientation
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); keepScreenOn = true; setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(onClick = onBack, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Close Video", tint = Color.White)
        }
    }
}

@Composable
fun WorkshopDetailScreen(viewModel: DashboardViewModel, workshopId: String?, navController: NavController) {
    val context = LocalContext.current
    val workshops by viewModel.globalWorkshops.collectAsState()
    val localWorkshops by viewModel.localWorkshops.collectAsState()
    val workshop = workshops.find { it.id == workshopId } ?: localWorkshops.find { it.id == workshopId }

    if (workshop == null) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Workshop not found", color = Color.Gray) }; return }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp, start = 24.dp, end = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black) }
                Text("Workshop Overview", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(workshop.title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black, lineHeight = 34.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().dashboardGlassCard().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = workshop.authorAvatar.takeIf { it.isNotBlank() } ?: "[https://api.dicebear.com/7.x/avataaars/png?seed=Mimi](https://api.dicebear.com/7.x/avataaars/png?seed=Mimi)", contentDescription = "Author Avatar", modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.LightGray), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(workshop.author, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        Text(workshop.tag, fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
                OutlinedButton(onClick = { Toast.makeText(context, "Followed ${workshop.author}!", Toast.LENGTH_SHORT).show() }, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)) { Text("Follow", fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("Chapters", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (workshop.chapters.isEmpty()) {
            item { Text("No chapters added yet.", color = Color.Gray) }
        } else {
            items(workshop.chapters.size) { index ->
                val chapter = workshop.chapters[index]
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).dashboardGlassCard().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) { Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(chapter.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    if (chapter.imageUrl != null || chapter.pdfUrl != null || chapter.videoUrl != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (chapter.videoUrl != null) { ChapterResourceButton("Video", Icons.Outlined.PlayCircle) { viewModel.currentMediaUrl = chapter.videoUrl; navController.navigate("video_viewer") } }
                            if (chapter.pdfUrl != null) { ChapterResourceButton("PDF", Icons.Outlined.PictureAsPdf) { openPdfView(context, chapter.pdfUrl) } }
                            if (chapter.imageUrl != null) { ChapterResourceButton("Image", Icons.Outlined.Image) { viewModel.currentMediaUrl = chapter.imageUrl; navController.navigate("image_viewer") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterResourceButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.8f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = Color.Black)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddContentScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    var selectedType by remember { mutableStateOf("Doubt") }
    val context = LocalContext.current
    var doubtText by remember { mutableStateOf("") }
    var tagText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var workshopTitle by remember { mutableStateOf("") }
    var workshopTag by remember { mutableStateOf("") }
    val chapters = remember { mutableStateListOf(ChapterDraftState()) }
    var isUploading by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val doubtImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> selectedImageUri = uri }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isUploading) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black) } }
            Text(if (isUploading) "Publishing..." else "Create Post", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (isUploading) {
            val infiniteTransition = rememberInfiniteTransition()
            val alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "uploadAlpha")
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.Black.copy(alpha = alpha)), contentAlignment = Alignment.Center) { Icon(Icons.Default.CloudUpload, contentDescription = "Uploading", tint = Color.White, modifier = Modifier.size(40.dp)) }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Uploading large files to the cloud...", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Please do not close the app.", color = Color.DarkGray, fontSize = 12.sp)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().dashboardGlassCard().padding(4.dp)) {
                listOf("Doubt", "Workshop").forEach { type ->
                    val isSelected = selectedType == type
                    val bgColor by animateColorAsState(if (isSelected) Color.Black else Color.Transparent, label = "bg")
                    val textColor by animateColorAsState(if (isSelected) Color.White else Color.DarkGray, label = "text")
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(bgColor).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedType = type }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(type, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.weight(1f).fillMaxWidth().dashboardGlassCard().verticalScroll(scrollState).padding(20.dp)) {
                if (selectedType == "Doubt") {
                    OutlinedTextField(value = doubtText, onValueChange = { doubtText = it }, label = { Text("What's your question?") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = tagText, onValueChange = { tagText = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(24.dp))
                    if (selectedImageUri != null) {
                        AsyncImage(model = selectedImageUri, contentDescription = "Selected", modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedButton(onClick = { doubtImagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedImageUri == null) "Attach Image" else "Change Image", color = Color.Black) }
                } else {
                    OutlinedTextField(value = workshopTitle, onValueChange = { workshopTitle = it }, label = { Text("Workshop Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = workshopTag, onValueChange = { workshopTag = it }, label = { Text("Topic / Domain") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Chapters", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    chapters.forEachIndexed { index, chapterDraft ->
                        ChapterDraftInput(index = index + 1, state = chapterDraft, onUpdate = { chapters[index] = it }, onRemove = { if (chapters.size > 1) chapters.removeAt(index) })
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    TextButton(onClick = { chapters.add(ChapterDraftState()) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Chapter", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (selectedType == "Doubt") {
                        if (doubtText.isBlank()) return@Button Toast.makeText(context, "Please enter content", Toast.LENGTH_SHORT).show()
                        isUploading = true
                        val tagsList = tagText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        viewModel.uploadDoubt(doubtText, tagsList, selectedImageUri, onSuccess = { Toast.makeText(context, "Doubt Posted!", Toast.LENGTH_SHORT).show(); onBack() }, onError = { e -> Toast.makeText(context, "Error: $e", Toast.LENGTH_LONG).show(); isUploading = false })
                    } else {
                        if (workshopTitle.isBlank() || chapters.any { it.title.isBlank() }) return@Button Toast.makeText(context, "Please fill all titles", Toast.LENGTH_SHORT).show()
                        isUploading = true
                        viewModel.uploadWorkshop(workshopTitle, workshopTag, chapters, onSuccess = { Toast.makeText(context, "Workshop Created!", Toast.LENGTH_SHORT).show(); onBack() }, onError = { e -> Toast.makeText(context, "Error: $e", Toast.LENGTH_LONG).show(); isUploading = false })
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) { Text("Publish", color = Color.White) }
        }
    }
}

@Composable
fun ChapterDraftInput(index: Int, state: ChapterDraftState, onUpdate: (ChapterDraftState) -> Unit, onRemove: () -> Unit) {
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onUpdate(state.copy(imageUri = uri)) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onUpdate(state.copy(pdfUri = uri)) }
    val vidPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onUpdate(state.copy(videoUri = uri)) }

    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Chapter $index", fontWeight = FontWeight.Bold)
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = state.title, onValueChange = { onUpdate(state.copy(title = it)) }, label = { Text("Chapter Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vidPicker.launch("video/*") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(if (state.videoUri == null) "Video" else "1 Video", fontSize = 11.sp, color = Color.Black) }
            OutlinedButton(onClick = { pdfPicker.launch("application/pdf") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(if (state.pdfUri == null) "PDF" else "1 PDF", fontSize = 11.sp, color = Color.Black) }
            OutlinedButton(onClick = { imgPicker.launch("image/*") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(if (state.imageUri == null) "Image" else "1 Image", fontSize = 11.sp, color = Color.Black) }
        }
    }
}

@Composable
fun WorkshopCard(workshop: Workshop, isEnrolled: Boolean = false, onJoinClick: () -> Unit = {}, onClick: () -> Unit = {}) {
    Column(modifier = Modifier.width(280.dp).dashboardGlassCard().dashboardBouncyClick { onClick() }.padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (workshop.authorAvatar.isNotEmpty()) {
                    AsyncImage(model = workshop.authorAvatar, contentDescription = "Avatar", modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(24.dp)) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(workshop.author, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Black)
                    Text(workshop.tag, fontSize = 12.sp, color = Color.DarkGray)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(workshop.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MenuBook, contentDescription = "Chapters", modifier = Modifier.size(14.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                Text("${workshop.chapters.size} Chapters", fontSize = 12.sp, color = Color.Gray)
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (isEnrolled) Color.Gray else Color.Black)
                    .clickable { if (!isEnrolled) onJoinClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(if (isEnrolled) "Joined" else "Join", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// --- Modified Focus Screen to track real time to Firebase ---
@Composable
fun FocusScreen(viewModel: DashboardViewModel, navController: NavController) {
    var selectedPreset by remember { mutableStateOf(25) }
    var timeLeft by remember { mutableStateOf(selectedPreset * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedAmbiance by remember { mutableStateOf<String?>(null) }
    var newGoalText by remember { mutableStateOf("") }
    var accumulatedSeconds by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

    LaunchedEffect(selectedAmbiance) {
        mediaPlayer.reset()
        if (selectedAmbiance != null) {
            val url = when(selectedAmbiance) {
                "Rain" -> "[https://wisdawn-songs.netlify.app/song1.mp3](https://wisdawn-songs.netlify.app/song1.mp3)"
                "Cafe" -> "[https://wisdawn-songs.netlify.app/cafe.mp3](https://wisdawn-songs.netlify.app/cafe.mp3)"
                "Wind" -> "[https://wisdawn-songs.netlify.app/wind.mp3](https://wisdawn-songs.netlify.app/wind.mp3)"
                "Music" -> "[https://wisdawn-songs.netlify.app/music.mp3](https://wisdawn-songs.netlify.app/music.mp3)"
                else -> null
            }
            try {
                if (url != null) {
                    mediaPlayer.setDataSource(url); mediaPlayer.isLooping = true; mediaPlayer.prepareAsync()
                    mediaPlayer.setOnPreparedListener { it.start() }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val goals = remember { mutableStateListOf(FocusGoal(text = "Define abstract concept", isDone = true), FocusGoal(text = "Read chapters 4 & 5", isDone = false)) }

    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            delay(1000)
            timeLeft--
            accumulatedSeconds++
            // Save to Firebase incrementally per minute
            if (accumulatedSeconds >= 60) {
                viewModel.addFocusTime(accumulatedSeconds)
                accumulatedSeconds = 0
            }
        } else if (timeLeft == 0 && isRunning) {
            isRunning = false
            // Save remaining when done
            if (accumulatedSeconds > 0) {
                viewModel.addFocusTime(accumulatedSeconds)
                accumulatedSeconds = 0
            }
        }
    }

    val progress = if (selectedPreset > 0) timeLeft.toFloat() / (selectedPreset * 60) else 0f
    val minutes = timeLeft / 60
    val seconds = timeLeft % 60
    val timeFormatted = "${String.format("%02d", minutes)}:${String.format("%02d", seconds)}"

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp, start = 24.dp, end = 24.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Focus Mode", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black)
                Box(modifier = Modifier.size(40.dp).dashboardGlassCard().dashboardBouncyClick { navController.navigate("home") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().dashboardGlassCard().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(color = Color.LightGray.copy(alpha = 0.3f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(color = Color.Black, startAngle = -90f, sweepAngle = progress * 360f, useCenter = false, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(timeFormatted, fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.Black, letterSpacing = 2.sp)
                        Text(if (isRunning) "FOCUSING" else if (timeLeft == 0) "DONE" else "PAUSED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 2.sp)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).border(1.dp, Color.LightGray, CircleShape).clip(CircleShape).dashboardBouncyClick {
                        isRunning = false; timeLeft = selectedPreset * 60
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Refresh, contentDescription = "Reset", tint = Color.DarkGray, modifier = Modifier.size(24.dp)) }
                    Box(modifier = Modifier.size(64.dp).background(Color.Black, CircleShape).clip(CircleShape).dashboardBouncyClick {
                        if (timeLeft > 0) {
                            isRunning = !isRunning
                            if (!isRunning && accumulatedSeconds > 0) {
                                viewModel.addFocusTime(accumulatedSeconds)
                                accumulatedSeconds = 0
                            }
                        }
                    }, contentAlignment = Alignment.Center) { Icon(if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(32.dp)) }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(15, 25, 50, 90).forEach { preset ->
                        val isSelected = selectedPreset == preset
                        val bgColor by animateColorAsState(if (isSelected) Color.Black else Color.Transparent, label = "preset_bg")
                        val textColor by animateColorAsState(if (isSelected) Color.White else Color.DarkGray, label = "preset_text")
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp).clip(RoundedCornerShape(12.dp)).background(bgColor).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            selectedPreset = preset; timeLeft = preset * 60; isRunning = false
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("${preset}m", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().dashboardGlassCard().padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ambiance", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    if (selectedAmbiance != null) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.GraphicEq, contentDescription = "Playing", tint = Color.Black, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Playing", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold) } } else { Icon(Icons.Outlined.VolumeOff, contentDescription = "Muted", tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Rain" to Icons.Outlined.Cloud, "Cafe" to Icons.Outlined.LocalCafe, "Wind" to Icons.Outlined.Air, "Music" to Icons.Outlined.MusicNote).forEach { (name, icon) ->
                        val isSelected = selectedAmbiance == name
                        val bgColor by animateColorAsState(if (isSelected) Color.Black else Color.White.copy(alpha=0.5f), label = "amb_bg")
                        val iconColor by animateColorAsState(if (isSelected) Color.White else Color.DarkGray, label = "amb_icon")
                        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(bgColor).dashboardBouncyClick { selectedAmbiance = if (selectedAmbiance == name) null else name }, contentAlignment = Alignment.Center) { Icon(icon, contentDescription = name, tint = iconColor, modifier = Modifier.size(28.dp)) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().dashboardGlassCard().padding(24.dp)) {
                Text("Goals", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))
                goals.toList().forEach { goal ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f).dashboardBouncyClick { val index = goals.indexOf(goal); if (index >= 0) goals[index] = goal.copy(isDone = !goal.isDone) }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (goal.isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = if (goal.isDone) Color.Black else Color.Gray, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = goal.text, fontSize = 15.sp, color = if (goal.isDone) Color.Gray else Color.Black, fontWeight = if (goal.isDone) FontWeight.Medium else FontWeight.SemiBold, textDecoration = if (goal.isDone) TextDecoration.LineThrough else TextDecoration.None)
                        }
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).clickable { goals.remove(goal) }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(value = newGoalText, onValueChange = { newGoalText = it }, modifier = Modifier.weight(1f), textStyle = TextStyle(fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Medium), decorationBox = { innerTextField -> if (newGoalText.isEmpty()) Text("Add goal...", color = Color.Gray, fontSize = 14.sp); innerTextField() })
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(if (newGoalText.isNotBlank()) Color.Black else Color.LightGray).clickable { if (newGoalText.isNotBlank()) { goals.add(FocusGoal(text = newGoalText)); newGoalText = "" } }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = "Add Goal", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

// --- Modified Profile Screen for Real Data ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(viewModel: DashboardViewModel, navController: NavController) {
    val userProfile by viewModel.currentUserProfile.collectAsState()
    val enrolledWorkshops by viewModel.enrolledWorkshops.collectAsState()

    // Derived collections for the "My Uploads" tab
    val currentUserId = viewModel.currentUserId
    val globalDoubts by viewModel.globalDoubts.collectAsState()
    val globalWorkshops by viewModel.globalWorkshops.collectAsState()
    val myDoubts = globalDoubts.filter { it.authorId == currentUserId }
    val myWorkshops = globalWorkshops.filter { it.authorId == currentUserId }

    var selectedTab by remember { mutableStateOf("Overview") }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp, start = 24.dp, end = 24.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Profile", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black)
                Box(modifier = Modifier.size(40.dp).dashboardGlassCard().dashboardBouncyClick { navController.navigate("home") }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(20.dp)) }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(90.dp).clip(CircleShape).background(Color.White.copy(alpha=0.6f)).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    val avatarUrl = try { userProfile?.javaClass?.getMethod("getAvatar")?.invoke(userProfile) as? String } catch(e: Exception) { userProfile?.avatar }

                    if (avatarUrl.isNullOrEmpty()) { Icon(Icons.Outlined.Person, contentDescription = "Avatar", tint = Color.DarkGray, modifier = Modifier.size(40.dp)) }
                    else { AsyncImage(model = avatarUrl, contentDescription = "Avatar", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.dashboardBouncyClick { navController.navigate("edit_profile") }) {
                    Text(userProfile?.fullName ?: "User", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile", tint = Color.DarkGray, modifier = Modifier.size(18.dp))
                }
                Text("Student / Developer", fontSize = 14.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Real Live Stats Implementation
                ProfileStatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.EmojiEvents, value = "${userProfile?.xp ?: 0}", label = "REP", iconColor = Color(0xFFF59E0B))
                ProfileStatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.LocalFireDepartment, value = "${(userProfile?.focusedSeconds ?: 0L) / 3600}h", label = "FOCUS", iconColor = Color(0xFFEF4444))
                ProfileStatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.CheckCircleOutline, value = "${userProfile?.helpedCount ?: 0}", label = "HELPED", iconColor = Color(0xFF10B981))
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Overview", "My Uploads").forEach { tab ->
                    val isSelected = selectedTab == tab
                    val textColor by animateColorAsState(if (isSelected) Color.Black else Color.Gray, label = "text")
                    Column(modifier = Modifier.padding(end = 24.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedTab = tab }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab, color = textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isSelected) { Box(modifier = Modifier.height(3.dp).width(40.dp).clip(RoundedCornerShape(50)).background(Color.Black)) } else { Spacer(modifier = Modifier.height(3.dp)) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (selectedTab == "Overview") {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enrolled Workshops", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (enrolledWorkshops.isEmpty()) {
                item { Text("You haven't enrolled in any workshops yet.", color = Color.Gray, fontSize = 14.sp) }
            } else {
                items(enrolledWorkshops.size) { index ->
                    val workshopPair = enrolledWorkshops[index]
                    ProfileWorkshopCard(workshop = workshopPair.first, isLive = false)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        } else {
            // My Uploads Tab
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("My Workshops", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (myWorkshops.isEmpty()) {
                item { Text("No workshops uploaded yet.", color = Color.Gray, fontSize = 14.sp) }
            } else {
                items(myWorkshops.size) { index ->
                    ProfileWorkshopCard(workshop = myWorkshops[index], isLive = false)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("My Doubts", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (myDoubts.isEmpty()) {
                item { Text("No doubts posted yet.", color = Color.Gray, fontSize = 14.sp) }
            } else {
                items(myDoubts.size) { index ->
                    ProfileDoubtItemCard(doubt = myDoubts[index])
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

// ... Edit Profile, Dashboard Component methods ...

@Composable
fun EditProfileScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    val userProfile by viewModel.currentUserProfile.collectAsState()
    val context = LocalContext.current
    val currentAvatar = try { userProfile?.javaClass?.getMethod("getAvatar")?.invoke(userProfile) as? String ?: "" } catch(e: Exception) { "" }

    var nameText by remember { mutableStateOf(userProfile?.fullName ?: "") }
    var selectedAvatar by remember { mutableStateOf(currentAvatar) }
    var isSaving by remember { mutableStateOf(false) }
    val availableAvatars = listOf(
        "[https://api.dicebear.com/7.x/avataaars/png?seed=Felix&backgroundColor=b6e3f4](https://api.dicebear.com/7.x/avataaars/png?seed=Felix&backgroundColor=b6e3f4)",
        "[https://api.dicebear.com/7.x/avataaars/png?seed=Aneka&backgroundColor=c0aede](https://api.dicebear.com/7.x/avataaars/png?seed=Aneka&backgroundColor=c0aede)",
        "[https://api.dicebear.com/7.x/avataaars/png?seed=Mimi&backgroundColor=ffdfbf](https://api.dicebear.com/7.x/avataaars/png?seed=Mimi&backgroundColor=ffdfbf)",
        "[https://api.dicebear.com/7.x/avataaars/png?seed=Jack&backgroundColor=d1d4f9](https://api.dicebear.com/7.x/avataaars/png?seed=Jack&backgroundColor=d1d4f9)",
        "[https://api.dicebear.com/7.x/avataaars/png?seed=Nala&backgroundColor=c0aede](https://api.dicebear.com/7.x/avataaars/png?seed=Nala&backgroundColor=c0aede)",
        "[https://api.dicebear.com/7.x/avataaars/png?seed=Leo&backgroundColor=b6e3f4](https://api.dicebear.com/7.x/avataaars/png?seed=Leo&backgroundColor=b6e3f4)"
    )

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black) }
            Text("Edit Profile", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
        }
        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.weight(1f).fillMaxWidth().dashboardGlassCard().padding(20.dp)) {
            Text("Profile Picture", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(availableAvatars) { avatarUrl ->
                    val isSelected = selectedAvatar == avatarUrl
                    val borderModifier = if (isSelected) Modifier.border(3.dp, Color.Black, CircleShape) else Modifier
                    Box(modifier = Modifier.size(70.dp).clip(CircleShape).then(borderModifier).clickable { selectedAvatar = avatarUrl }, contentAlignment = Alignment.Center) { AsyncImage(model = avatarUrl, contentDescription = "Avatar Option", modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White), contentScale = ContentScale.Crop) }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("Display Name", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = nameText, onValueChange = { nameText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (isSaving) { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Black) } } else {
            Button(
                onClick = {
                    if (nameText.isBlank()) return@Button Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    isSaving = true
                    viewModel.updateProfile(nameText, selectedAvatar, onSuccess = { Toast.makeText(context, "Profile Updated!", Toast.LENGTH_SHORT).show(); onBack() }, onError = { e -> Toast.makeText(context, "Error: $e", Toast.LENGTH_LONG).show(); isSaving = false })
                }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) { Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun ProfileStatCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, iconColor: Color) {
    Column(modifier = modifier.dashboardGlassCard().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.Black)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
    }
}

@Composable
fun ProfileWorkshopCard(workshop: Workshop, isLive: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().dashboardGlassCard().dashboardBouncyClick { }.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(workshop.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.weight(1f))
            if (isLive) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFEBEE)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Red)); Spacer(modifier = Modifier.width(4.dp))
                    Text("LIVE NOW", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, contentDescription = "Speaker", tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(workshop.author, fontSize = 12.sp, color = Color.DarkGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = "Time", tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(formatTimeAgo(workshop.timestamp), fontSize = 12.sp, color = Color.DarkGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PeopleOutline, contentDescription = "Attendees", tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${workshop.attendees}", fontSize = 12.sp, color = Color.DarkGray)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileDoubtItemCard(doubt: DoubtPost) {
    Column(modifier = Modifier.fillMaxWidth().dashboardGlassCard().dashboardBouncyClick { }.padding(16.dp)) {
        Text(doubt.question, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            doubt.tags.forEach { tag ->
                Box(modifier = Modifier.border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(tag, fontSize = 10.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SubdirectoryArrowRight, contentDescription = "Replies", tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${doubt.replies} Replies", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.width(12.dp))
                Text(formatTimeAgo(doubt.timestamp), fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

// ... Remaining unmodified base code components ...

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoubtCard(
    doubt: DoubtPost,
    currentUserId: String = "",
    isSaved: Boolean = false,
    onLikeClick: () -> Unit = {},
    onDiscussClick: () -> Unit = {},
    onSaveClick: () -> Unit = {}
) {
    val isLiked = doubt.likedBy.contains(currentUserId)
    val likeIconColor = if (isLiked) Color.Black else Color.DarkGray
    val likeIcon = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).dashboardGlassCard().dashboardBouncyClick { }.padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (doubt.authorAvatar.isNotEmpty()) {
                    AsyncImage(model = doubt.authorAvatar, contentDescription = "Avatar", modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.DarkGray), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(doubt.author, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Black)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTimeAgo(doubt.timestamp), fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = "Save", tint = if (isSaved) Color.Black else Color.Gray, modifier = Modifier.size(20.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSaveClick() })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(doubt.question, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black, lineHeight = 22.sp)
        Spacer(modifier = Modifier.height(12.dp))
        if (doubt.imageUrl != null) {
            AsyncImage(model = doubt.imageUrl, contentDescription = "Attached Image", modifier = Modifier.fillMaxWidth().height(180.dp).border(1.dp, Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.height(12.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            doubt.tags.forEach { tag ->
                Box(modifier = Modifier.border(1.dp, Color.Black, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) { Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLikeClick() }.padding(4.dp)) {
                    Icon(likeIcon, contentDescription = "Upvotes", modifier = Modifier.size(18.dp), tint = likeIconColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(doubt.upvotes.toString(), fontSize = 14.sp, color = likeIconColor, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDiscussClick() }.padding(4.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Replies", modifier = Modifier.size(18.dp), tint = Color.DarkGray)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(doubt.replies.toString(), fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
            Text("Answer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDiscussClick() }.padding(4.dp))
        }
    }
}

@Composable
fun rememberDashboardGrain(): Brush {
    return remember {
        val width = 200; val height = 200; val pixels = IntArray(width * height); val random = Random()
        for (i in pixels.indices) {
            val noiseValue = random.nextInt(256); val alpha = random.nextInt(25)
            pixels[i] = android.graphics.Color.argb(alpha, noiseValue, noiseValue, noiseValue)
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        ShaderBrush(shader)
    }
}

@Composable
fun DashboardAnimatedBackground() {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEBEBEB))) {
        Box(modifier = Modifier.offset(x = 100.dp, y = 150.dp).size(350.dp).blur(90.dp).background(Color(0xFFD3D3D3), CircleShape))
        Box(modifier = Modifier.offset(x = (-50).dp, y = 400.dp).size(400.dp).blur(100.dp).background(Color(0xFFFFFFFF), CircleShape))
    }
}

fun Modifier.dashboardGlassCard() = composed { this.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.6f)).border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp)) }
fun Modifier.dashboardBouncyClick(onClick: () -> Unit) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")
    this.scale(scale).clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
fun GlassBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val items = listOf(Triple("home", "Home", Icons.Filled.Home), Triple("library", "Library", Icons.Outlined.Book), Triple("focus", "Focus", Icons.Outlined.CenterFocusStrong), Triple("quest", "Quest", Icons.Outlined.Flag), Triple("profile", "Profile", Icons.Outlined.Person))
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).dashboardGlassCard().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (route, label, icon) ->
                val selected = currentRoute == route
                val color by animateColorAsState(if (selected) Color.Black else Color.Gray, label = "icon_color")
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.dashboardBouncyClick { if (!selected) navController.navigate(route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } }) {
                    Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.height(4.dp))
                    Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = color)
                }
            }
        }
    }
}

@Composable
fun DashboardPlaceholderScreen(title: String) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestScreen(viewModel: QuestViewModel, navController: NavController) {
    var topicInput by remember { mutableStateOf("") }
    val successColor = Color(0xFF10B981)
    val errorColor = Color(0xFFEF4444)

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp, start = 24.dp, end = 24.dp)) {
        item {
            Text("Your Quests", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Complete objectives to earn XP and unlock exclusive rewards.", fontSize = 14.sp, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().dashboardGlassCard().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(60.dp).border(2.dp, Color.Black, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("LVL", color = Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("12", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Knowledge Seeker", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black).padding(horizontal=6.dp, vertical=2.dp)) { Text("Current Tier", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${viewModel.userXp} XP", color = Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("10,000 XP", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(progress = viewModel.userXp / 10000f, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Color.Black, trackColor = Color.LightGray)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Color.White.copy(alpha=0.8f), RoundedCornerShape(12.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = "Streak", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    Text("14", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("DAY STREAK", color = Color.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Text("Challenge Arena", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black); Spacer(modifier = Modifier.height(4.dp))
            Text("Generate AI-powered questions to test your knowledge.", fontSize = 14.sp, color = Color.DarkGray); Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth().dashboardGlassCard().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = Color(0xFF6200EA)); Spacer(modifier = Modifier.width(8.dp)); Text("AI Topic Generator", color = Color.Black, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(value = topicInput, onValueChange = { topicInput = it }, label = { Text("Enter a topic (e.g., React Hooks, CAP Theorem)", color = Color.DarkGray) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = Color.Black), singleLine = true)
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { if (topicInput.isNotBlank()) viewModel.generateQuestion(topicInput) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black), enabled = !viewModel.isGenerating) {
                    if (viewModel.isGenerating) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White) } else { Text("Generate Question", color = Color.White, fontWeight = FontWeight.Bold) }
                }

                AnimatedVisibility(visible = viewModel.currentQuestion != null) {
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        HorizontalDivider(color = Color.LightGray); Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("AI GENERATED", color = Color(0xFF6200EA), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("+50 XP", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(viewModel.currentQuestion?.question ?: "", color = Color.Black, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(16.dp))

                        viewModel.currentQuestion?.options?.forEachIndexed { index, option ->
                            val isSelected = viewModel.selectedOptionIndex == index
                            val isCorrect = viewModel.isEvaluated && index == viewModel.currentQuestion?.correctIndex
                            val isWrongSelection = viewModel.isEvaluated && isSelected && !isCorrect
                            val bgColor = when { isCorrect -> successColor; isWrongSelection -> errorColor; isSelected -> Color.Black; else -> Color.Transparent }
                            val textColor = when { isCorrect || isWrongSelection || isSelected -> Color.White; else -> Color.Black }
                            val borderColor = when { isCorrect -> successColor; isWrongSelection -> errorColor; isSelected -> Color.Black; else -> Color.Gray }

                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(bgColor).border(1.dp, borderColor, RoundedCornerShape(12.dp)).clickable(enabled = !viewModel.isEvaluated) { viewModel.selectedOptionIndex = index }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${'A' + index}", color = if (textColor == Color.White) Color.White.copy(alpha=0.8f) else Color.DarkGray, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Text(option, color = textColor, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!viewModel.isEvaluated) {
                            Button(onClick = { viewModel.evaluateAnswer() }, enabled = viewModel.selectedOptionIndex != null, modifier = Modifier.align(Alignment.End), colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("Submit Answer", color = Color.White) }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha=0.5f)).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)).padding(16.dp)) {
                                Text(viewModel.feedbackMessage, color = if (viewModel.selectedOptionIndex == viewModel.currentQuestion?.correctIndex) successColor else errorColor, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Explanation: ${viewModel.currentQuestion?.explanation}", color = Color.DarkGray, fontSize = 14.sp)
                            }
                        }
                    }
                }
                if (viewModel.feedbackMessage.isNotEmpty() && viewModel.currentQuestion == null) { Text(viewModel.feedbackMessage, color = errorColor, modifier = Modifier.padding(top = 16.dp)) }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Text("Daily Objectives", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black); Spacer(modifier = Modifier.height(16.dp))
            ObjectiveItem("Complete a Focus Session", "1/2", "+150 XP", 0.5f, false)
            ObjectiveItem("Help a Peer", "1/1", "+300 XP", 1f, true, "Answer a doubt in your locality")
            ObjectiveItem("Daily Login", "1/1", "+50 XP", 1f, true)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ObjectiveItem(title: String, progressText: String, xp: String, progress: Float, isDone: Boolean, subtitle: String? = null) {
    val successColor = Color(0xFF10B981)
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).dashboardGlassCard().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isDone) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = if (isDone) successColor else Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column { Text(title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp); if (subtitle != null) { Text(subtitle, color = Color.DarkGray, fontSize = 12.sp) } }
            }
            Text(xp, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(progress = progress, modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape), color = if (isDone) successColor else Color.Black, trackColor = Color.LightGray)
            Spacer(modifier = Modifier.width(12.dp))
            Text(progressText, color = Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}