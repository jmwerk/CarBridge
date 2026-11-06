package org.carbridge.ui

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.carbridge.CarCopy
import org.carbridge.HomeModel
import org.carbridge.Job
import org.carbridge.JobStep
import org.carbridge.Setup
import org.carbridge.SourceApp
import org.carbridge.UserMessage

class HomeActions(
    val allowInstalls: () -> Unit,
    val openAndroidAuto: () -> Unit,
    val markDeveloperMode: () -> Unit,
    val markUnknownSources: () -> Unit,
    val add: (SourceApp) -> Unit,
    val update: (CarCopy) -> Unit,
    val remove: (CarCopy) -> Unit,
)

private val SignShape = RoundedCornerShape(20.dp)
private val SignBorderShape = RoundedCornerShape(15.dp)
private val Gutter = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: HomeModel, actions: HomeActions) {
    val snackbar = remember { SnackbarHostState() }
    var details by remember { mutableStateOf<UserMessage?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(model) {
        model.messages.collect { message ->
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = if (message.details != null) "Details" else null,
                withDismissAction = message.details == null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) details = message
        }
    }

    val catalog = model.catalog
    val job = model.job
    val setup = model.setup
    val busy = job != null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(job != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                job?.let { JobBar(it) }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = inner.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                ),
            ) {
                item("header") { SignHeader(onAbout = { showAbout = true }) }

                item("setup") {
                    if (!setup.complete || showSetup) {
                        SetupCard(setup, actions, onHide = if (setup.complete) ({ showSetup = false }) else null)
                    } else {
                        SetupDone(onShow = { showSetup = true })
                    }
                }

                item("car-title") { SectionTitle("On your car") }
                when {
                    catalog == null -> item("loading") { Loading() }
                    catalog.copies.isEmpty() -> item("car-empty") {
                        Hint("Nothing yet. Add an app below, then reconnect your phone to Android Auto.")
                    }
                    else -> items(catalog.copies, key = { "car:" + it.packageName }) { copy ->
                        CarSign(copy, busy, onUpdate = { actions.update(copy) }, onRemove = { actions.remove(copy) })
                    }
                }

                item("add-title") { SectionTitle("Add an app") }
                if (catalog != null) {
                    if (catalog.available.size > 6) {
                        item("search") { SearchField(query) { query = it } }
                    }
                    val shown = catalog.available.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                    if (shown.isEmpty()) {
                        item("add-empty") {
                            Hint(if (query.isBlank()) "Every app you can add is already on your car." else "No apps match “${query.trim()}”.")
                        }
                    }
                    items(shown, key = { "src:" + it.info.packageName }) { app ->
                        SourceRow(app, enabled = !busy, onAdd = { actions.add(app) })
                    }
                }

                item("footer") { Footer(onAbout = { showAbout = true }) }
            }

            // Keeps light status bar icons readable once the header scrolls away
            Box(
                Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(LocalSigns.current.guide),
            )
        }
    }

    if (showAbout) {
        ModalBottomSheet(onDismissRequest = { showAbout = false }) { About() }
    }
    details?.let { message ->
        AlertDialog(
            onDismissRequest = { details = null },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } },
            title = { Text(message.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "This app may not support being copied. These details help when reporting a problem:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(message.details.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}

/** A guide-sign blank: green field, inset white border. */
@Composable
private fun GuidePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val signs = LocalSigns.current
    Surface(modifier, shape = SignShape, color = signs.guide, contentColor = signs.onGuide, shadowElevation = 2.dp) {
        Column(
            Modifier.padding(5.dp).border(2.dp, signs.onGuide, SignBorderShape).padding(horizontal = 18.dp, vertical = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun SignHeader(onAbout: () -> Unit) {
    val signs = LocalSigns.current
    Column(
        Modifier.fillMaxWidth().background(signs.guide)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(start = Gutter, end = 4.dp, top = 12.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ExitArrow, contentDescription = null, tint = signs.onGuide, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "CarBridge",
                style = MaterialTheme.typography.displaySmall,
                color = signs.onGuide,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onAbout) {
                Icon(Icons.Rounded.Info, contentDescription = "About CarBridge", tint = signs.onGuide)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Put the apps on your phone on your car's screen.",
            style = MaterialTheme.typography.titleMedium,
            color = signs.onGuideMuted,
            modifier = Modifier.padding(end = Gutter),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = Gutter, end = Gutter, top = 32.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SetupCard(setup: Setup, actions: HomeActions, onHide: (() -> Unit)?) {
    val signs = LocalSigns.current
    val steps = listOf(
        SetupStep(
            "Let CarBridge install apps",
            "Android asks once. CarBridge only installs the car copies you choose.",
            setup.canInstall,
        ) { Button(onClick = actions.allowInstalls, colors = cautionButton()) { Text("Allow") } },
        SetupStep(
            "Turn on developer mode in Android Auto",
            "Open Android Auto settings and tap Version 10 times until it says developer mode is on.",
            setup.developerMode,
        ) {
            Button(onClick = actions.openAndroidAuto, colors = cautionButton()) { Text("Open Android Auto") }
            TextButton(onClick = actions.markDeveloperMode, colors = ButtonDefaults.textButtonColors(contentColor = signs.onCaution)) {
                Text("It's on")
            }
        },
        SetupStep(
            "Allow unknown sources",
            "In Android Auto settings, open the menu at the top right, choose Developer settings and turn on Unknown sources.",
            setup.unknownSources,
        ) {
            Button(onClick = actions.openAndroidAuto, colors = cautionButton()) { Text("Open Android Auto") }
            TextButton(onClick = actions.markUnknownSources, colors = ButtonDefaults.textButtonColors(contentColor = signs.onCaution)) {
                Text("It's on")
            }
        },
    )
    val current = steps.indexOfFirst { !it.done }

    Surface(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 20.dp),
        shape = SignShape,
        color = signs.caution,
        contentColor = signs.onCaution,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (current == -1) "You're set up" else "Set up once",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onHide != null) {
                    IconButton(onClick = onHide) { Icon(Icons.Rounded.Close, contentDescription = "Hide setup") }
                }
            }
            Text(
                "Android Auto only lists apps from outside the Play Store after these three steps.",
                style = MaterialTheme.typography.bodyMedium,
                color = signs.onCautionMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            steps.forEachIndexed { i, step ->
                StepRow(number = i + 1, step = step, active = i == current)
            }
        }
    }
}

private class SetupStep(
    val title: String,
    val body: String,
    val done: Boolean,
    val actions: @Composable () -> Unit,
)

@Composable
private fun cautionButton() = LocalSigns.current.let {
    ButtonDefaults.buttonColors(containerColor = it.onCaution, contentColor = it.caution)
}

@Composable
private fun StepRow(number: Int, step: SetupStep, active: Boolean) {
    val signs = LocalSigns.current
    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .then(if (step.done) Modifier.background(signs.onCaution) else Modifier.border(2.dp, signs.onCaution, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (step.done) {
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = signs.caution, modifier = Modifier.size(20.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (step.done || active) 1f else 0.72f)) {
            Text(step.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            if (active) {
                Text(step.body, style = MaterialTheme.typography.bodyMedium, color = signs.onCautionMuted, modifier = Modifier.padding(top = 2.dp))
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { step.actions() }
            }
        }
    }
}

@Composable
private fun SetupDone(onShow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Android Auto is set up", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onShow) { Text("Setup steps") }
    }
}

@Composable
private fun CarSign(copy: CarCopy, busy: Boolean, onUpdate: () -> Unit, onRemove: () -> Unit) {
    val signs = LocalSigns.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
        GuidePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(copy.icon, 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(copy.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            copy.original == null -> "The original app is gone, so this copy can't be updated."
                            copy.outdated -> "${copy.original.label} was updated. Update the car copy to match."
                            else -> "In Android Auto"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = signs.onGuideMuted,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onRemove,
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = signs.onGuide),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
                if (copy.original != null) {
                    Spacer(Modifier.width(8.dp))
                    val colors = if (copy.outdated) {
                        ButtonDefaults.buttonColors(containerColor = signs.caution, contentColor = signs.onCaution)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = signs.onGuide, contentColor = signs.guide)
                    }
                    Button(onClick = onUpdate, enabled = !busy, colors = colors) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Update")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap, sizeDp: Int) {
    Image(icon, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search your apps") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } }
        } else null,
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 8.dp),
    )
}

@Composable
private fun SourceRow(app: SourceApp, enabled: Boolean, onAdd: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, 44)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Formatter.formatShortFileSize(context, app.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onAdd, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add")
        }
    }
}

@Composable
private fun Footer(onAbout: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 32.dp)) {
        Text(
            "Car copies are for passengers, or for when you're parked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAbout, modifier = Modifier.padding(top = 12.dp)) { Text("How CarBridge works") }
    }
}

@Composable
private fun JobBar(job: Job) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(job.icon, 40)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (job.update) "Updating ${job.label}" else "Adding ${job.label} to your car",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        JobStep.entries[job.step].text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LaneProgress(job.step, JobStep.entries.size, Modifier.padding(top = 14.dp))
        }
    }
}

/** Progress drawn as lane markings: finished dashes solid, the current one pulsing. */
@Composable
private fun LaneProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "lane").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(6.dp).clip(CircleShape).background(
                    when {
                        i < current -> on
                        i == current -> on.copy(alpha = pulse)
                        else -> off
                    },
                ),
            )
        }
    }
}

@Composable
private fun About() {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("How CarBridge works", style = MaterialTheme.typography.headlineSmall)
        AboutItem(
            "A separate copy",
            "CarBridge makes a copy of an app that Android Auto will show on the car screen. " +
                "Your original app and its data stay as they are; the copy has its own data and sign-in.",
        )
        AboutItem(
            "Some apps won't open",
            "Apps that check their own signature, a license or Play Integrity may refuse to run as a copy.",
        )
        AboutItem(
            "Keep CarBridge installed",
            "Copies are signed with a key stored only on this phone. If you uninstall CarBridge or clear its data, " +
                "remove your copies and add them again.",
        )
        AboutItem(
            "Use it safely and fairly",
            "Use car copies only while parked or for passengers. Only copy apps whose license allows changes, " +
                "and don't share the copies.",
        )
        AboutItem(
            "Free software",
            "GPL-3.0. No accounts, ads or tracking. Everything happens on this phone.",
        )
    }
}

@Composable
private fun AboutItem(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
