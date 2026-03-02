package com.rst.wisdawn

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import java.util.Random
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // --- SESSION CHECK ---
        val auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("WisdawnPrefs", Context.MODE_PRIVATE)
        val isProfileSet = prefs.getBoolean("is_profile_set", false)

        // If user is already logged in AND finished onboarding, skip to Dashboard instantly
        if (auth.currentUser != null && isProfileSet) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish() // Destroy MainActivity so user can't press 'back' to see onboarding
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color.Black,
                    background = Color(0xFFF5F5F5),
                    surface = Color.White,
                    onPrimary = Color.White,
                    onBackground = Color.Black,
                    onSurface = Color.Black
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedLiquidBackground()
                    OnboardingNavGraph()
                    Box(modifier = Modifier.fillMaxSize().background(rememberGrainBrush()))
                }
            }
        }
    }
}

// --- 1. Data Structure & ViewModel ---

data class UserProfile(
    val email: String = "", val fullName: String = "", val phone: String = "",
    val institution: String = "", val department: String = "", val year: String = "",
    val semester: String = "", val interests: List<String> = emptyList(),
    val gender: String = "", val dob: String = "", val address: String = "",
    val latitude: Double = 0.0, val longitude: Double = 0.0, val avatar: String = "",
    val savedPosts: List<String> = emptyList(),
    val enrolledWorkshops: Map<String, Float> = emptyMap(), val xp: Int = 0,
    val focusedSeconds: Long = 0L,
    val helpedCount: Int = 0
)

class OnboardingViewModel : ViewModel() {
    private val _state = MutableStateFlow(UserProfile())
    val state: StateFlow<UserProfile> = _state.asStateFlow()

    fun updateField(update: (UserProfile) -> UserProfile) { _state.update(update) }

    fun toggleInterest(interest: String) {
        _state.update { current ->
            val updated = if (current.interests.contains(interest)) current.interests - interest else current.interests + interest
            current.copy(interests = updated)
        }
    }

    // Checks Firestore if the user document already exists (useful if they reinstall the app)
    fun checkUserExists(uid: String, onExists: () -> Unit, onNewUser: () -> Unit) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) onExists() else onNewUser()
            }
            .addOnFailureListener { onNewUser() }
    }

    fun saveToFirestore(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val firestore = FirebaseFirestore.getInstance()
                val uid = auth.currentUser?.uid ?: throw Exception("User not authenticated")
                firestore.collection("users").document(uid).set(_state.value).await()
                onSuccess()
            } catch (e: Exception) { onError(e) }
        }
    }
}

// --- 2. Custom UI Components, Liquid Glass, Noise & Animations ---

@Composable
fun rememberGrainBrush(): Brush {
    return remember {
        val width = 200
        val height = 200
        val pixels = IntArray(width * height)
        val random = Random()
        for (i in pixels.indices) {
            val noiseValue = random.nextInt(256)
            val alpha = random.nextInt(25)
            pixels[i] = android.graphics.Color.argb(alpha, noiseValue, noiseValue, noiseValue)
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        ShaderBrush(shader)
    }
}

@Composable
fun AnimatedLiquidBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 500f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "x"
    )
    val yOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEBEBEB))) {
        Box(modifier = Modifier.offset(x = xOffset.dp, y = yOffset.dp).size(300.dp).blur(80.dp).background(Color(0xFFD3D3D3), CircleShape))
        Box(modifier = Modifier.offset(x = (-xOffset).dp, y = (yOffset/2).dp).size(400.dp).blur(100.dp).background(Color(0xFFFFFFFF), CircleShape))
    }
}

fun Modifier.liquidGlassCard() = composed {
    this.clip(RoundedCornerShape(28.dp))
        .background(Color.White.copy(alpha = 0.6f))
        .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(28.dp))
        .padding(24.dp)
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 40.sp,
    fontWeight: FontWeight = FontWeight.Black,
    color: Color = Color.Black,
    letterSpacing: androidx.compose.ui.unit.TextUnit = (-1).sp
) {
    var displayedText by remember { mutableStateOf("") }
    var cursorVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }

    LaunchedEffect(text) {
        displayedText = ""
        for (char in text) {
            delay(60)
            displayedText += char
        }
    }

    Text(
        text = buildAnnotatedString {
            append(displayedText)
            withStyle(style = SpanStyle(color = if (cursorVisible) color else Color.Transparent)) { append("|") }
        },
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        letterSpacing = letterSpacing,
        lineHeight = fontSize * 1.1f
    )
}

@Composable
fun BouncyButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    // FIX: Always remember the latest version of the onClick lambda
    val currentOnClick by rememberUpdatedState(onClick)

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        waitForUpOrCancellation()
                        isPressed = false
                        currentOnClick() // <-- Trigger the updated lambda here
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (icon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun BouncyOutlinedButton(text: String, icon: Painter? = null, onClick: () -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick) // <-- FIX
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .border(1.dp, Color.Black, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        waitForUpOrCancellation()
                        isPressed = false
                        currentOnClick() // <-- Trigger the updated lambda here
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painter = icon, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun BouncyOutlinedButtonTextOnly(text: String, onClick: () -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick) // <-- FIX
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .border(1.dp, Color.Black, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        waitForUpOrCancellation()
                        isPressed = false
                        currentOnClick() // <-- Trigger the updated lambda here
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}


// --- 3. Navigation & Scaffold ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingNavGraph(viewModel: OnboardingViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val stepMapping = mapOf("welcome" to 1, "basic_info" to 2, "academic" to 3, "interests" to 4, "personal" to 5, "location" to 6, "all_set" to 7)
    val currentStep = stepMapping[currentRoute] ?: 1
    val showTopBar = currentStep in 2..6

    val animatedProgress by animateFloatAsState(targetValue = currentStep / 7f, animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing), label = "progress")

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AnimatedVisibility(visible = showTopBar, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.8f)).padding(bottom = 8.dp)) {
                    TopAppBar(
                        title = { Text("Setup Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                        navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black) } },
                        actions = { Text(text = "$currentStep / 7", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black, modifier = Modifier.padding(end = 16.dp)) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 24.dp).clip(CircleShape), color = Color.Black, trackColor = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, startDestination = "welcome", modifier = Modifier.padding(innerPadding).fillMaxSize(),
            enterTransition = { fadeIn(tween(500)) + slideInHorizontally(tween(500)) { it / 4 } }, exitTransition = { fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { -it / 4 } },
            popEnterTransition = { fadeIn(tween(500)) + slideInHorizontally(tween(500)) { -it / 4 } }, popExitTransition = { fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { it / 4 } }
        ) {
            composable("welcome") {
                WelcomeScreen(onGoogleSignInSuccess = { email, name, uid ->
                    // 1. Put basic data into ViewModel
                    viewModel.updateField { it.copy(email = email, fullName = name) }

                    // 2. Check if user already exists (Reinstall scenario)
                    viewModel.checkUserExists(uid,
                        onExists = {
                            // Backend profile found! Skip onboarding, save local flag, go to Dashboard
                            context.getSharedPreferences("WisdawnPrefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("is_profile_set", true).apply()
                            context.startActivity(Intent(context, DashboardActivity::class.java))
                            (context as? Activity)?.finish()
                        },
                        onNewUser = {
                            // Brand new user -> Continue to Onboarding Step 2
                            navController.navigate("basic_info")
                        }
                    )
                })
            }
            composable("basic_info") { BasicInfoScreen(state, { viewModel.updateField(it) }, { navController.navigate("academic") }) }
            composable("academic") { AcademicScreen(state, { viewModel.updateField(it) }, { navController.navigate("interests") }) }
            composable("interests") { InterestsScreen(state, { viewModel.toggleInterest(it) }, { navController.navigate("personal") }) }
            composable("personal") { PersonalDetailsScreen(state, { viewModel.updateField(it) }, { navController.navigate("location") }) }
            composable("location") { LocationScreen(state, { viewModel.updateField(it) }, { navController.navigate("all_set") }) }
            composable("all_set") {
                AllSetScreen(onFinish = {
                    viewModel.saveToFirestore(
                        onSuccess = {
                            // Save success! Flag profile as complete and transition.
                            context.getSharedPreferences("WisdawnPrefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("is_profile_set", true).apply()

                            context.startActivity(Intent(context, DashboardActivity::class.java))
                            (context as? Activity)?.finish() // Destroy onboarding activity
                        },
                        onError = { e -> Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                    )
                })
            }
        }
    }
}

// --- 4. The Screens (Glass & Monochrome Styling) ---

@Composable
fun WelcomeScreen(onGoogleSignInSuccess: (String, String, String) -> Unit) { // Added UID to callback
    val context = LocalContext.current
    val webClientId = "1009283394364-o50ng42hrd8u1cffpj1ujvetm5e0eavk.apps.googleusercontent.com"

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                onGoogleSignInSuccess(account.email ?: "", account.displayName ?: "", uid)
                            } else {
                                Toast.makeText(context, "Auth Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            } catch (e: ApiException) { Toast.makeText(context, "Failed: ${e.statusCode}", Toast.LENGTH_SHORT).show() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TypewriterText("Wisdawn.", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Unlock your potential with daily brain exercises.", textAlign = TextAlign.Center, color = Color.DarkGray, fontSize = 16.sp)

        Spacer(modifier = Modifier.height(48.dp))

        AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(R.drawable.logo)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Animated Logo",
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White, CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.weight(1f))

        BouncyOutlinedButton(
            text = "Continue with Google",
            icon = painterResource(id = R.drawable.ic_google)
        ) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(webClientId).requestEmail().build()
            val client = GoogleSignIn.getClient(context, gso)
            client.signOut().addOnCompleteListener { googleSignInLauncher.launch(client.signInIntent) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        BouncyButton("Create an account") { }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun NotionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false, // <-- Added readOnly parameter
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier // <-- Added modifier parameter
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly, // <-- Pass it here
        label = { Text(label, color = if (isError) Color.Red else Color.DarkGray) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, tint = if (isError) Color.Red else Color.Black) } },
        modifier = modifier.fillMaxWidth(), // <-- Apply the modifier here
        enabled = enabled,
        isError = isError,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Black,
            unfocusedBorderColor = Color.Gray.copy(alpha=0.5f),
            errorBorderColor = Color.Red,
            disabledBorderColor = Color.LightGray,
            disabledTextColor = Color.Gray,
            focusedContainerColor = Color.White.copy(alpha=0.5f),
            unfocusedContainerColor = Color.White.copy(alpha=0.3f),
            errorContainerColor = Color.Red.copy(alpha=0.05f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotionDropdownField(
    selectedValue: String,
    options: List<String>,
    label: String,
    onValueChangedEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selectedValue,
            onValueChange = {},
            label = { Text(label, color = if (isError) Color.Red else Color.DarkGray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = isError,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Gray.copy(alpha=0.5f),
                errorBorderColor = Color.Red,
                focusedContainerColor = Color.White.copy(alpha=0.5f),
                unfocusedContainerColor = Color.White.copy(alpha=0.3f),
                errorContainerColor = Color.Red.copy(alpha=0.05f)
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption, color = Color.Black) },
                    onClick = {
                        onValueChangedEvent(selectionOption)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneInputField(
    phone: String,
    selectedCountryCode: String,
    onPhoneChange: (String) -> Unit,
    onCountryCodeChange: (String) -> Unit,
    isError: Boolean = false
) {
    val countryCodes = listOf("+91", "+1", "+44", "+61", "+81", "+971")
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Country Code Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(0.35f)
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedCountryCode,
                onValueChange = {},
                isError = isError,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                    errorBorderColor = Color.Red,
                    focusedContainerColor = Color.White.copy(alpha = 0.5f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                    errorContainerColor = Color.Red.copy(alpha=0.05f)
                ),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                countryCodes.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(code, color = Color.Black) },
                        onClick = {
                            onCountryCodeChange(code)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Phone Number Input
        Box(modifier = Modifier.weight(0.65f)) {
            NotionTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = "Phone Number",
                icon = Icons.Default.Phone,
                isError = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        }
    }
}

@Composable
fun BasicInfoScreen(state: UserProfile, onStateChange: ((UserProfile) -> UserProfile) -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    var showError by remember { mutableStateOf(false) }

    // Extract country code and actual number for the UI state
    var countryCode by remember { mutableStateOf("+91") }
    val phoneWithoutCode = state.phone.removePrefix(countryCode).trim()

    val availableAvatars = listOf(
        "https://api.dicebear.com/7.x/avataaars/png?seed=Felix&backgroundColor=b6e3f4",
        "https://api.dicebear.com/7.x/avataaars/png?seed=Aneka&backgroundColor=c0aede",
        "https://api.dicebear.com/7.x/avataaars/png?seed=Mimi&backgroundColor=ffdfbf",
        "https://api.dicebear.com/7.x/avataaars/png?seed=Jack&backgroundColor=d1d4f9",
        "https://api.dicebear.com/7.x/avataaars/png?seed=Nala&backgroundColor=c0aede",
        "https://api.dicebear.com/7.x/avataaars/png?seed=Leo&backgroundColor=b6e3f4"
    )

    GlassScreenContainer("Basic Info", "Let's personalize your experience.", onContinue = {
        // Error Handling validation check
        if (state.fullName.isBlank() || phoneWithoutCode.isBlank() || state.avatar.isBlank()) {
            showError = true
            Toast.makeText(context, "Please fill all fields and pick an avatar.", Toast.LENGTH_SHORT).show()
        } else {
            onContinue()
        }
    }) {
        // Avatar Picker Component
        Text("Profile Picture", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(availableAvatars) { avatarUrl ->
                val isSelected = state.avatar == avatarUrl
                val borderModifier = if (isSelected) Modifier.border(3.dp, Color.Black, CircleShape) else Modifier

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .then(borderModifier)
                        .clickable { onStateChange { it.copy(avatar = avatarUrl) } },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar Option",
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        if (showError && state.avatar.isBlank()) {
            Text("Please select an avatar", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        NotionTextField(state.email, {}, "Email Address", Icons.Default.Email, enabled = false)
        Spacer(modifier = Modifier.height(16.dp))

        NotionTextField(
            value = state.fullName,
            onValueChange = { text -> onStateChange { it.copy(fullName = text) } },
            label = "Full Name",
            icon = Icons.Default.Person,
            isError = showError && state.fullName.isBlank()
        )
        Spacer(modifier = Modifier.height(16.dp))

        PhoneInputField(
            phone = phoneWithoutCode,
            selectedCountryCode = countryCode,
            onPhoneChange = { text -> onStateChange { it.copy(phone = countryCode + text) } },
            onCountryCodeChange = { newCode ->
                countryCode = newCode
                onStateChange { it.copy(phone = newCode + phoneWithoutCode) }
            },
            isError = showError && phoneWithoutCode.isBlank()
        )
    }
}

@Composable
fun AcademicScreen(state: UserProfile, onStateChange: ((UserProfile) -> UserProfile) -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    var showError by remember { mutableStateOf(false) }

    val yearOptions = listOf("Year 1", "Year 2", "Year 3", "Year 4")
    val semesterOptions = (1..8).map { "Semester $it" }

    GlassScreenContainer("Academics", "Connect with relevant study groups.", onContinue = {
        // Error handling validation check
        if (state.institution.isBlank() || state.department.isBlank() || state.year.isBlank() || state.semester.isBlank()) {
            showError = true
            Toast.makeText(context, "Please fill in all academic details.", Toast.LENGTH_SHORT).show()
        } else {
            onContinue()
        }
    }) {
        NotionTextField(
            value = state.institution,
            onValueChange = { text -> onStateChange { it.copy(institution = text) } },
            label = "Institution Name",
            icon = Icons.Default.AccountBalance,
            isError = showError && state.institution.isBlank()
        )
        Spacer(modifier = Modifier.height(16.dp))

        NotionTextField(
            value = state.department,
            onValueChange = { text -> onStateChange { it.copy(department = text) } },
            label = "Department",
            icon = Icons.Default.Class,
            isError = showError && state.department.isBlank()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) {
                NotionDropdownField(
                    selectedValue = state.year,
                    options = yearOptions,
                    label = "Year",
                    onValueChangedEvent = { text -> onStateChange { it.copy(year = text) } },
                    isError = showError && state.year.isBlank()
                )
            }
            Box(Modifier.weight(1f)) {
                NotionDropdownField(
                    selectedValue = state.semester,
                    options = semesterOptions,
                    label = "Semester",
                    onValueChangedEvent = { text -> onStateChange { it.copy(semester = text) } },
                    isError = showError && state.semester.isBlank()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsScreen(state: UserProfile, onToggleInterest: (String) -> Unit, onContinue: () -> Unit) {
    val topics = listOf("Coding", "Physics", "Math", "Biology", "History", "Chemistry", "Literature", "Geography", "Psychology", "Art")
    GlassScreenContainer("Interests", "Select what fascinates you.", onContinue) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            topics.forEach { topic ->
                val selected = state.interests.contains(topic)
                val bgColor by animateColorAsState(if (selected) Color.Black else Color.White.copy(alpha = 0.5f), label = "bg")
                val textColor by animateColorAsState(if (selected) Color.White else Color.Black, label = "text")

                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor).border(1.dp, if(selected) Color.Black else Color.Gray, RoundedCornerShape(20.dp)).clickable { onToggleInterest(topic) }.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(topic, color = textColor, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalDetailsScreen(state: UserProfile, onStateChange: ((UserProfile) -> UserProfile) -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val genders = listOf("Male", "Female", "Non-binary", "Prefer not to say")

    // Set up DatePickerDialog
    val calendar = java.util.Calendar.getInstance()
    val year = calendar.get(java.util.Calendar.YEAR)
    val month = calendar.get(java.util.Calendar.MONTH)
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Format the output to ensure two digits for day and month
                val formattedDate = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
                onStateChange { it.copy(dob = formattedDate) }
            },
            year, month, day
        )
    }

    GlassScreenContainer("Personal Details", "Just a few more details.", onContinue) {
        Text("Gender", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            genders.forEach { gender ->
                val selected = state.gender == gender
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Color.Black else Color.White.copy(alpha = 0.5f))
                        .border(1.dp, if(selected) Color.Black else Color.Gray, RoundedCornerShape(20.dp))
                        .clickable { onStateChange { it.copy(gender = gender) } }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(gender, color = if (selected) Color.White else Color.Black, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        // FIX: Wrap in a Box and use a transparent overlay to catch the click
        Box(modifier = Modifier.fillMaxWidth()) {
            NotionTextField(
                value = state.dob,
                onValueChange = {}, // State update is handled by the DatePicker instead
                label = "Date of Birth (DD/MM/YYYY)",
                icon = Icons.Default.DateRange,
                readOnly = true, // Keeps keyboard hidden
            )

            // Invisible overlay that perfectly matches the text field's size
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Transparent)
                    .clickable { datePickerDialog.show() }
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun LocationScreen(state: UserProfile, onStateChange: ((UserProfile) -> UserProfile) -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    onStateChange { it.copy(latitude = loc.latitude, longitude = loc.longitude) }
                }
            }
        }
    }

    GlassScreenContainer("Location Setup", "Connect with nearby peers.", onContinue) {
        val hasLoc = state.latitude != 0.0

        // Replaced icon-based outlined button with text-only version since no icon is provided
        if (hasLoc) {
            BouncyOutlinedButtonTextOnly("Location Captured ✓") {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        } else {
            // using generic icon here since the specific icon isn't standard in the painter update
            BouncyButton("Enable Precise Location") {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("OR ENTER MANUALLY", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        NotionTextField(state.address, { text -> onStateChange { it.copy(address = text) } }, "Full Address")
    }
}

@Composable
fun AllSetScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
        }
        Spacer(modifier = Modifier.height(40.dp))
        TypewriterText("You're all set.", fontSize = 36.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Your Wisdawn adventure begins now.", textAlign = TextAlign.Center, color = Color.DarkGray, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(48.dp))
        BouncyButton("Enter Dashboard", Icons.Default.ArrowForward, onFinish)
    }
}

@Composable
fun GlassScreenContainer(title: String, subtitle: String, onContinue: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        TypewriterText(title)
        Spacer(modifier = Modifier.height(8.dp))
        Text(subtitle, color = Color.DarkGray, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Column(modifier = Modifier.weight(1f).fillMaxWidth().liquidGlassCard()) { content() }
        Spacer(modifier = Modifier.height(24.dp))
        BouncyButton("Continue", Icons.Default.ArrowForward, onContinue)
        Spacer(modifier = Modifier.height(16.dp))
    }
}