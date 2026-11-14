package at.libmatti.mtztool.activity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.github.ThemeUpload
import at.libmatti.mtztool.ui.LogConsole
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

class AppThemeStudioActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppThemeStudioScreen(onBack = { finish() })
                }
            }
        }
    }
}

private val colorRoles = linkedMapOf(
    "primary" to "#415F91",
    "onPrimary" to "#FFFFFF",
    "primaryContainer" to "#D6E3FF",
    "onPrimaryContainer" to "#001B3E",
    "secondary" to "#565F71",
    "onSecondary" to "#FFFFFF",
    "secondaryContainer" to "#DAE2F9",
    "onSecondaryContainer" to "#131C2B",
    "tertiary" to "#705575",
    "onTertiary" to "#FFFFFF",
    "tertiaryContainer" to "#FAD8FD",
    "onTertiaryContainer" to "#28132E",
    "background" to "#F9F9FF",
    "onBackground" to "#191C20",
    "surface" to "#F9F9FF",
    "onSurface" to "#191C20",
    "surfaceVariant" to "#E0E2EC",
    "onSurfaceVariant" to "#44474E",
    "surfaceTint" to "#415F91",
    "surfaceBright" to "#F9F9FF",
    "surfaceDim" to "#D9D9E0",
    "surfaceContainerLowest" to "#FFFFFF",
    "surfaceContainerLow" to "#F3F3FA",
    "surfaceContainer" to "#EEEDF4",
    "surfaceContainerHigh" to "#E8E7EE",
    "surfaceContainerHighest" to "#E2E2E9",
    "inverseSurface" to "#2E3036",
    "inverseOnSurface" to "#F0F0F7",
    "inversePrimary" to "#AAC7FF",
    "error" to "#BA1A1A",
    "onError" to "#FFFFFF",
    "errorContainer" to "#FFDAD6",
    "onErrorContainer" to "#410002",
    "outline" to "#74777F",
    "outlineVariant" to "#C4C6D0",
    "scrim" to "#000000",
    // Custom clock colors. They are written into the app-theme JSON and can
    // be consumed by clock widgets or a future in-app clock style.
    "clockBackground" to "#1B1B1F",
    "clockText" to "#E6E1E5",
    "clockAccent" to "#D0BCFF",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppThemeStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("Mein App-Theme") }
    var description by remember { mutableStateOf("") }
    val colors = remember { mutableStateMapOf<String, String>().apply { putAll(colorRoles) } }
    var pickerRole by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var prUrl by remember { mutableStateOf<String?>(null) }

    val previewBitmap = remember(colors.toMap(), name) { renderPreview(name, colors) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App-Theme Studio", fontWeight = FontWeight.Bold)
                        Text(
                            "Farben frei wählen · Preview wird automatisch erzeugt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(1000) },
                label = { Text("Beschreibung (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Farben", fontWeight = FontWeight.Bold)
                    colorRoles.keys.forEach { role ->
                        val value = colors[role] ?: colorRoles.getValue(role)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickerRole = role }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(parseColor(value), RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(role, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    value.uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Default.Brush, contentDescription = null)
                        }
                    }
                }
            }

            if (!GitHubApi.isSignedIn(context)) {
                Text("Bitte zuerst mit GitHub anmelden.", color = MaterialTheme.colorScheme.error)
            } else {
                Button(
                    onClick = {
                        if (uploading) return@Button
                        uploading = true
                        error = null
                        scope.launch {
                            val preview = withContext(Dispatchers.IO) {
                                ByteArrayOutputStream().use { output ->
                                    previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                                    output.toByteArray()
                                }
                            }
                            ThemeUpload.submitAppTheme(
                                context, name, description, colors.toMap(), preview,
                            ).fold(
                                onSuccess = { prUrl = it },
                                onFailure = { error = it.message },
                            )
                            uploading = false
                        }
                    },
                    enabled = !uploading && name.length >= 3,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uploading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Upload, contentDescription = null)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(if (uploading) "Erstelle PR …" else "App-Theme als PR einreichen")
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            prUrl?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("PR erstellt", fontWeight = FontWeight.Bold)
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            LogConsole(height = 160)
            Spacer(Modifier.height(16.dp))
        }
    }

    pickerRole?.let { role ->
        ColorPickerDialog(
            initial = parseColor(colors[role] ?: colorRoles.getValue(role)),
            onDismiss = { pickerRole = null },
            onPick = {
                colors[role] = toHex(it)
                pickerRole = null
            },
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onPick: (Color) -> Unit,
) {
    val hsv = remember(initial) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(
                android.graphics.Color.rgb(
                    (initial.red * 255).toInt(),
                    (initial.green * 255).toInt(),
                    (initial.blue * 255).toInt(),
                ),
                it,
            )
        }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Farbe wählen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(color, RoundedCornerShape(12.dp)),
                )
                Text("Farbton", style = MaterialTheme.typography.labelSmall)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Sättigung", style = MaterialTheme.typography.labelSmall)
                Slider(value = saturation, onValueChange = { saturation = it })
                Text("Helligkeit", style = MaterialTheme.typography.labelSmall)
                Slider(value = value, onValueChange = { value = it })
                Text(
                    toHex(color),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(color) }) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

private fun parseColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF415F91))

private fun toHex(color: Color): String = String.format(
    Locale.US,
    "#%02X%02X%02X",
    (color.red * 255).toInt(),
    (color.green * 255).toInt(),
    (color.blue * 255).toInt(),
)

private fun renderPreview(name: String, colors: Map<String, String>): Bitmap {
    val width = 720
    val height = 360
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val primary = android.graphics.Color.parseColor(colors["primary"] ?: "#415F91")
    val secondary = android.graphics.Color.parseColor(colors["secondary"] ?: "#565F71")
    val tertiary = android.graphics.Color.parseColor(colors["tertiary"] ?: "#705575")
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, width.toFloat(), height.toFloat(),
        intArrayOf(primary, secondary, tertiary),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
    paint.shader = null
    // Clock preview with its own custom colors.
    val clockBackground = android.graphics.Color.parseColor(
        colors["clockBackground"] ?: "#1B1B1F"
    )
    val clockText = android.graphics.Color.parseColor(colors["clockText"] ?: "#E6E1E5")
    val clockAccent = android.graphics.Color.parseColor(colors["clockAccent"] ?: "#D0BCFF")
    paint.color = clockBackground
    canvas.drawRoundRect(RectF(56f, 34f, 310f, 142f), 28f, 28f, paint)
    paint.color = clockText
    paint.textSize = 54f
    paint.isFakeBoldText = true
    canvas.drawText("12:34", 82f, 110f, paint)
    paint.color = clockAccent
    canvas.drawCircle(282f, 62f, 9f, paint)
    paint.color = android.graphics.Color.argb(210, 255, 255, 255)
    canvas.drawRoundRect(RectF(56f, 170f, 664f, 300f), 36f, 36f, paint)
    paint.color = primary
    paint.textSize = 38f
    paint.isFakeBoldText = true
    canvas.drawText(name.take(28), 88f, 236f, paint)
    return bitmap
}
