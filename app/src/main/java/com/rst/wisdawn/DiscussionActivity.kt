package com.rst.wisdawn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

// --- Data Models ---
data class DiscussionComment(
    val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val text: String = "",
    val imageUrl: String? = null, // Added to support image attachments
    val timestamp: Long = System.currentTimeMillis()
)

// --- ViewModel ---
class DiscussionViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _post = MutableStateFlow<DoubtPost?>(null)
    val post = _post.asStateFlow()

    private val _comments = MutableStateFlow<List<DiscussionComment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private var currentPostId: String = ""
    private var currentUserProfile: UserProfile? = null

    init {
        fetchCurrentUser()
    }

    private fun fetchCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                currentUserProfile = doc.toObject(UserProfile::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadPostData(postId: String) {
        currentPostId = postId
        _isLoading.value = true

        firestore.collection("posts").document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    _post.value = snapshot.toObject(DoubtPost::class.java)
                }
            }

        firestore.collection("posts").document(postId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    _comments.value = snapshot.documents.mapNotNull { it.toObject(DiscussionComment::class.java) }
                }
                _isLoading.value = false
            }
    }

    fun postComment(text: String, imageUri: Uri?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("User not logged in")
        val user = currentUserProfile ?: return onError("Profile not loaded")
        val postId = currentPostId
        if (postId.isEmpty() || (text.isBlank() && imageUri == null)) return

        val commentId = UUID.randomUUID().toString()

        viewModelScope.launch {
            try {
                var downloadUrl: String? = null

                // Upload image if one is attached
                if (imageUri != null) {
                    val ref = storage.reference.child("comment_images/$commentId.jpg")
                    ref.putFile(imageUri).await()
                    downloadUrl = ref.downloadUrl.await().toString()
                }

                val newComment = DiscussionComment(
                    id = commentId,
                    postId = postId,
                    authorId = uid,
                    authorName = user.fullName,
                    authorAvatar = user.avatar,
                    text = text.trim(),
                    imageUrl = downloadUrl
                )

                // Batch write
                firestore.runBatch { batch ->
                    val commentRef = firestore.collection("posts").document(postId).collection("comments").document(commentId)
                    val postRef = firestore.collection("posts").document(postId)

                    batch.set(commentRef, newComment)
                    batch.update(postRef, "replies", FieldValue.increment(1))
                }.await()

                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to post reply")
            }
        }
    }
}

// --- Activity ---
class DiscussionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val postId = intent.getStringExtra("POST_ID")

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color.Black, background = Color(0xFFF5F5F5),
                    surface = Color.White, onPrimary = Color.White,
                    onBackground = Color.Black, onSurface = Color.Black
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Unified Design Language Background
                    DashboardAnimatedBackground()
                    Box(modifier = Modifier.fillMaxSize().background(rememberDashboardGrain()))

                    if (postId != null) {
                        DiscussionScreen(postId = postId, onBack = { finish() })
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Post not found.", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

// --- UI Components ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionScreen(postId: String, onBack: () -> Unit, viewModel: DiscussionViewModel = viewModel()) {
    val context = LocalContext.current
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var replyText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var replyMode by remember { mutableStateOf("Public") } // "Public" or "Private"
    var isPosting by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
    }

    LaunchedEffect(postId) {
        viewModel.loadPostData(postId)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Doubt Thread", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .dashboardGlassCard()
                        .dashboardBouncyClick { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        },
        bottomBar = {
            BottomGlassInputArea(
                replyText = replyText,
                onReplyTextChanged = { replyText = it },
                selectedImageUri = selectedImageUri,
                onAttachClick = { imagePicker.launch("image/*") },
                onClearImage = { selectedImageUri = null },
                replyMode = replyMode,
                onModeSelected = { replyMode = it },
                isPosting = isPosting,
                onSend = {
                    if (replyText.isBlank() && selectedImageUri == null) return@BottomGlassInputArea

                    if (replyMode == "Public") {
                        isPosting = true
                        viewModel.postComment(
                            text = replyText,
                            imageUri = selectedImageUri,
                            onSuccess = {
                                replyText = ""
                                selectedImageUri = null
                                isPosting = false
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                isPosting = false
                            }
                        )
                    } else {
                        // Private mode intent
                        post?.let { currentPost ->
                            try {
                                val intent = Intent(context, Class.forName("com.rst.wisdawn.PrivateChatActivity")).apply {
                                    putExtra("TARGET_USER_ID", currentPost.authorId)
                                    putExtra("INITIAL_MESSAGE", replyText)
                                    // If your chat accepts images, pass the URI string
                                    selectedImageUri?.let { putExtra("INITIAL_IMAGE_URI", it.toString()) }
                                }
                                context.startActivity(intent)
                                replyText = ""
                                selectedImageUri = null
                            } catch (e: Exception) {
                                Toast.makeText(context, "Private Chat not fully implemented yet!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading && post == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Black)
            }
        } else if (post != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Original Post
                item {
                    OriginalPostHeaderGlass(post!!)
                }

                // Replies Header
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${comments.size} Replies",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                // Comments List
                if (comments.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No answers yet. Be the first to help!", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(comments) { comment ->
                        CommentItemGlass(comment)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OriginalPostHeaderGlass(post: DoubtPost) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .dashboardGlassCard()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (post.authorAvatar.isNotEmpty()) {
                AsyncImage(
                    model = post.authorAvatar,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(post.author, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                Text("Asked " + formatTimeAgo(post.timestamp), fontSize = 12.sp, color = Color.DarkGray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(post.question, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.Black, lineHeight = 26.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (post.imageUrl != null) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = "Attached Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.Black.copy(alpha=0.1f), RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            post.tags.forEach { tag ->
                Box(
                    modifier = Modifier
                        .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun CommentItemGlass(comment: DiscussionComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .animateContentSize()
    ) {
        if (comment.authorAvatar.isNotEmpty()) {
            AsyncImage(
                model = comment.authorAvatar,
                contentDescription = "Avatar",
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .dashboardGlassCard()
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(comment.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                Text(formatTimeAgo(comment.timestamp), fontSize = 10.sp, color = Color.DarkGray)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (comment.text.isNotBlank()) {
                Text(comment.text, fontSize = 14.sp, color = Color.Black, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (comment.imageUrl != null) {
                AsyncImage(
                    model = comment.imageUrl,
                    contentDescription = "Comment Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun BottomGlassInputArea(
    replyText: String,
    onReplyTextChanged: (String) -> Unit,
    selectedImageUri: Uri?,
    onAttachClick: () -> Unit,
    onClearImage: () -> Unit,
    replyMode: String,
    onModeSelected: (String) -> Unit,
    isPosting: Boolean,
    onSend: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dashboardGlassCard()
                .padding(12.dp)
        ) {
            // Top Row: Attachment Icon + Text Field
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.5f))
                        .clickable(enabled = !isPosting) { onAttachClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = "Attach", tint = Color.Black, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = replyText,
                    onValueChange = onReplyTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp)
                        .padding(vertical = 8.dp),
                    textStyle = TextStyle(fontSize = 15.sp, color = Color.Black),
                    cursorBrush = SolidColor(Color.Black),
                    decorationBox = { innerTextField ->
                        if (replyText.isEmpty()) Text("Write your answer...", color = Color.DarkGray, fontSize = 15.sp)
                        innerTextField()
                    }
                )
            }

            // Image Preview (Animated)
            if (selectedImageUri != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.height(80.dp).clip(RoundedCornerShape(8.dp))) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected",
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(0.6f))
                            .clickable { onClearImage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions: Mode Options + Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reply Modes (Radio Options)
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeOptionPill(
                        title = "Public",
                        icon = Icons.Outlined.Public,
                        isSelected = replyMode == "Public",
                        onClick = { onModeSelected("Public") }
                    )
                    ModeOptionPill(
                        title = "Private",
                        icon = Icons.Outlined.Lock,
                        isSelected = replyMode == "Private",
                        onClick = { onModeSelected("Private") }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Send Button
                val isSendEnabled = (replyText.isNotBlank() || selectedImageUri != null) && !isPosting
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isSendEnabled) Color.Black else Color.LightGray)
                        .clickable(enabled = isSendEnabled) { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp).padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModeOptionPill(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(if (isSelected) Color.Black else Color.Transparent, label = "bg")
    val contentColor by animateColorAsState(if (isSelected) Color.White else Color.DarkGray, label = "content")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, if (isSelected) Color.Black else Color.LightGray, RoundedCornerShape(20.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}