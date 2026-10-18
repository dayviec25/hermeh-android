package com.hermeh.android.feature.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hermeh.android.AppGraph
import com.hermeh.android.R
import com.hermeh.android.core.designsystem.Onboarding
import com.hermeh.android.core.model.ServerConfig
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 5
private const val CONNECT_PAGE = 4
private const val PROMPT_PAGE = 2
private val White = Color.White

@Composable
fun OnboardingRoute(
    graph: AppGraph,
    startAtConnect: Boolean,
    initialMessage: String?,
    server: ServerConfig? = null,
) {
    val viewModel: ConnectViewModel = viewModel(
        key = "connect-${server?.id}",
        factory = viewModelFactory { initializer { ConnectViewModel(graph.auth, server, initialMessage) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        startAtConnect = startAtConnect,
        onUrl = viewModel::onUrl,
        onUsername = viewModel::onUsername,
        onPassword = viewModel::onPassword,
        onAddHeader = viewModel::addHeader,
        onUpdateHeader = viewModel::updateHeader,
        onRemoveHeader = viewModel::removeHeader,
        onTest = viewModel::testConnection,
        onConnect = viewModel::connect,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(
    state: ConnectUiState,
    startAtConnect: Boolean,
    onUrl: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onAddHeader: () -> Unit,
    onUpdateHeader: (Int, HeaderRow) -> Unit,
    onRemoveHeader: (Int) -> Unit,
    onTest: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(initialPage = if (startAtConnect) CONNECT_PAGE else 0) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    var copied by rememberSaveable { mutableStateOf(false) }
    var bypassed by rememberSaveable { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }

    fun advance() {
        if (pager.currentPage == PROMPT_PAGE && !copied && !bypassed) {
            showReminder = true
            return
        }
        scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(CONNECT_PAGE)) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Onboarding.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            userScrollEnabled = !(pager.currentPage == PROMPT_PAGE && !copied && !bypassed),
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> FeaturesPage()
                2 -> AgentPromptPage(copied = copied, onCopied = { copied = true })
                3 -> ReachabilityPage()
                else -> ConnectPage(
                    state = state,
                    onUrl = onUrl,
                    onUsername = onUsername,
                    onPassword = onPassword,
                    onAddHeader = onAddHeader,
                    onUpdateHeader = onUpdateHeader,
                    onRemoveHeader = onRemoveHeader,
                    onConnect = onConnect,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageIndicator(current = pager.currentPage)
            if (pager.currentPage == CONNECT_PAGE) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Test Connection",
                        icon = Icons.Filled.Hub,
                        enabled = !state.isWorking && state.url.isNotBlank(),
                        onClick = onTest,
                        modifier = Modifier.weight(1f).testTag("connect.test"),
                    )
                    PrimaryButton(
                        text = "Connect",
                        icon = Icons.Filled.CheckCircle,
                        enabled = !state.isWorking,
                        onClick = onConnect,
                        modifier = Modifier.weight(1f).testTag("connect.submit"),
                    )
                }
            } else {
                val title = when (pager.currentPage) {
                    0 -> "Get Started"
                    1 -> "Set Up"
                    else -> "Continue"
                }
                PrimaryButton(
                    text = title,
                    onClick = ::advance,
                    modifier = Modifier.fillMaxWidth().testTag(if (pager.currentPage == 0) "onboarding.getStarted" else "onboarding.next"),
                )
                Text(
                    text = "Already have a server?",
                    color = White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { scope.launch { pager.animateScrollToPage(CONNECT_PAGE) } }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("onboarding.haveServer"),
                )
            }
        }
    }

    if (showReminder) {
        AlertDialog(
            onDismissRequest = { showReminder = false },
            title = { Text("Copy the setup prompt first") },
            text = { Text("Copy the agent setup prompt on your desktop before continuing so Hermes sign-in and HTTPS are configured correctly.") },
            confirmButton = {
                TextButton(modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("onboarding.continueAnyway"), onClick = {
                    showReminder = false
                    bypassed = true
                    scope.launch { pager.animateScrollToPage(PROMPT_PAGE + 1) }
                }) { Text("Continue Anyway") }
            },
            dismissButton = { TextButton(onClick = { showReminder = false }) { Text("Stay Here") } },
        )
    }
}

@Composable
private fun WelcomePage() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(322.dp)) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xFFFFBD1A).copy(alpha = 0.55f),
                        0.5f to Onboarding.glow.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                )
                drawCircle(
                    Brush.radialGradient(
                        0f to Onboarding.glowInner.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                        radius = size.minDimension * 0.35f,
                    ),
                    radius = size.minDimension * 0.35f,
                )
            }
            val shape = RoundedCornerShape(27.dp)
            Image(
                painter = painterResource(R.drawable.hermes_app_icon),
                contentDescription = "Hermeh",
                modifier = Modifier
                    .size(124.dp)
                    .shadow(24.dp, shape, ambientColor = Onboarding.glow, spotColor = Onboarding.glow)
                    .clip(shape)
                    .border(
                        BorderStroke(1.dp, Brush.linearGradient(listOf(White.copy(alpha = 0.25f), White.copy(alpha = 0.04f)))),
                        shape,
                    ),
            )
        }
        Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
        Column(
            Modifier.padding(horizontal = 28.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Control your Hermes agent from your Android phone.",
                color = White,
                fontSize = 31.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Connect to your self-hosted server over a private network, such as Tailscale or NetBird.",
                color = White.copy(alpha = 0.58f),
                fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroBadge(Icons.Filled.Lock, "Password protected")
                HeroBadge(Icons.Filled.Hub, "Private network ready")
            }
        }
    }
}

@Composable
private fun HeroBadge(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(White.copy(alpha = 0.06f))
            .border(1.dp, White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = White.copy(alpha = 0.68f), modifier = Modifier.size(13.dp))
        Text(title, color = White.copy(alpha = 0.68f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private data class Feature(val icon: ImageVector, val color: Color, val title: String, val subtitle: String)

private val features = listOf(
    Feature(Icons.Filled.Forum, Onboarding.amber, "Chat with your Hermes agent from your phone", "Drive conversations from anywhere on your tailnet."),
    Feature(Icons.AutoMirrored.Filled.ViewList, Color(0xFF34C759), "Manage sessions, tasks, and files remotely", "Browse workspaces and stay on top of agent work."),
    Feature(Icons.Filled.Mic, Color(0xFFAF52DE), "Voice input and mobile-friendly composer controls", "Compose naturally with touch-first controls."),
    Feature(Icons.Filled.VerifiedUser, Color(0xFF32ADE6), "Review approvals and clarifications inline", "Respond to agent prompts without switching apps."),
    Feature(Icons.Filled.Storage, Color(0xFFFF9500), "Self-hosted: your machine, your tailnet", "Your Hermes host stays on hardware you control."),
)

@Composable
private fun FeaturesPage() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What you get", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Your Hermes agent, reachable from your phone over Tailscale.",
                color = White.copy(alpha = 0.45f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            features.forEach { feature ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(feature.color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(feature.icon, contentDescription = null, tint = feature.color, modifier = Modifier.size(18.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(feature.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(feature.subtitle, color = White.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int, icon: ImageVector, title: String, description: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(White.copy(alpha = 0.06f))
                .border(1.dp, Onboarding.amber.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = White, modifier = Modifier.size(32.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("STEP $step", color = Onboarding.amber.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(title, color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 33.sp)
            Text(description, color = White.copy(alpha = 0.45f), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AgentPromptPage(copied: Boolean, onCopied: () -> Unit) {
    val context = LocalContext.current
    val prompt = remember { context.resources.openRawResource(R.raw.setup_prompt).bufferedReader().use { it.readText() }.trim() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        StepHeader(
            1,
            Icons.Filled.Terminal,
            "Set up Hermes",
            "Send this prompt to your Hermes Agent. It turns on password sign-in and gives you an HTTPS address.",
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(White.copy(alpha = 0.055f))
                .border(1.dp, White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                Text(prompt, color = White.copy(alpha = 0.82f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            PrimaryButton(
                text = if (copied) "Copied" else "Copy prompt",
                icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Hermes setup prompt", prompt))) }
                    onCopied()
                },
                modifier = Modifier.fillMaxWidth().testTag("onboarding.copyPrompt"),
            )
        }
    }
}

@Composable
private fun ReachabilityPage() {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        StepHeader(
            2,
            Icons.Filled.PhonelinkSetup,
            "Join your private network",
            "Put your phone on the same private network as your server, such as Tailscale or NetBird. Your agent will reply with the exact URL to use on the next screen.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(
                "Install your network’s app, such as Tailscale or NetBird, from Google Play.",
                "Sign in with the same account you used on your server.",
                "Keep it connected while using Hermeh.",
            ).forEachIndexed { index, text ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(23.dp).clip(CircleShape).background(Onboarding.amber), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text, color = White.copy(alpha = 0.72f), fontSize = 14.sp)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(White.copy(alpha = 0.06f))
                    .border(1.dp, White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .clickable { uri.openUri("https://play.google.com/store/apps/details?id=com.tailscale.ipn") }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Onboarding.amber, modifier = Modifier.size(18.dp))
                Text("Get Tailscale on Google Play", color = Onboarding.amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConnectPage(
    state: ConnectUiState,
    onUrl: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onAddHeader: () -> Unit,
    onUpdateHeader: (Int, HeaderRow) -> Unit,
    onRemoveHeader: (Int) -> Unit,
    onConnect: () -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(state.headers.isNotEmpty()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connect", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Enter your Hermes dashboard address, such as its Tailscale name or IP and port, then sign in with the dashboard username and password.",
                color = White.copy(alpha = 0.5f),
                fontSize = 13.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingField(
                icon = Icons.Filled.Link,
                title = "Server URL",
                value = state.url,
                onValueChange = onUrl,
                placeholder = "http://hermes.tailnet.ts.net:9119",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                tag = "connect.url",
            )
            state.addressPreview?.let {
                Text("Will connect to $it", color = White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            StatusBanner(state.status)
            OnboardingField(
                icon = Icons.Filled.Person,
                title = "Username",
                value = state.username,
                onValueChange = onUsername,
                placeholder = "Dashboard username",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                tag = "connect.username",
            )
            OnboardingField(
                icon = Icons.Filled.Key,
                title = "Password",
                value = state.password,
                onValueChange = onPassword,
                placeholder = "Dashboard password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
                secure = true,
                onGo = onConnect,
                tag = "connect.password",
            )
        }
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { advanced = !advanced }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                Text("Advanced", color = White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = White.copy(alpha = 0.6f))
            }
            if (advanced) {
                Text(
                    "Connection headers are sent with every request, for example Cloudflare Access service tokens (CF-Access-Client-Id, CF-Access-Client-Secret).",
                    color = White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
                state.headers.forEachIndexed { index, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlainField(row.name, { onUpdateHeader(index, row.copy(name = it)) }, "Header", Modifier.weight(1f))
                        PlainField(row.value, { onUpdateHeader(index, row.copy(value = it)) }, "Value", Modifier.weight(1f))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove header",
                            tint = White.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onRemoveHeader(index) }.padding(6.dp),
                        )
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAddHeader).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Onboarding.amber, modifier = Modifier.size(18.dp))
                    Text("Add header", color = Onboarding.amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(status: ConnectStatus) {
    val (text, tint, icon) = when (status) {
        ConnectStatus.Idle -> return
        ConnectStatus.Checking -> Triple("Checking server...", White.copy(alpha = 0.7f), null)
        is ConnectStatus.Found -> Triple(status.text, Onboarding.success, Icons.Filled.CheckCircle)
        is ConnectStatus.Error -> Triple(status.text, Onboarding.error, Icons.Filled.Warning)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag(if (status is ConnectStatus.Error) "connect.error" else "connect.status"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon == null) {
            CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(text, color = White.copy(alpha = 0.76f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OnboardingField(
    icon: ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    tag: String,
    secure: Boolean = false,
    onGo: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Onboarding.amber, modifier = Modifier.width(24.dp).size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Onboarding.amber),
                visualTransformation = if (secure) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth().testTag(tag),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, color = White.copy(alpha = 0.38f), fontSize = 16.sp, maxLines = 1)
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun PlainField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = White, fontSize = 14.sp),
        cursorBrush = SolidColor(Onboarding.amber),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = White.copy(alpha = 0.38f), fontSize = 14.sp)
                inner()
            }
        },
    )
}

@Composable
private fun PageIndicator(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PAGE_COUNT) { index ->
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (index == current) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (index == current) White else White.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Onboarding.amber.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(White.copy(alpha = 0.065f))
            .border(1.dp, White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = White.copy(alpha = if (enabled) 0.84f else 0.4f)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
