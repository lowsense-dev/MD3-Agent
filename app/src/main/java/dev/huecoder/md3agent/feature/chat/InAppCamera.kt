package dev.huecoder.md3agent.feature.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Cameraswitch
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Flash_auto
import com.composables.icons.materialsymbols.rounded.Flash_off
import com.composables.icons.materialsymbols.rounded.Flash_on
import com.composables.icons.materialsymbols.rounded.No_photography
import com.composables.icons.materialsymbols.rounded.Replay
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.settings.CameraFrame
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Full-screen camera for the chat composer; a confirmed photo is handed back as a content uri to attach. */
@Composable
fun InAppCamera(onDismiss: () -> Unit, onPhoto: (Uri) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraPermission(onDismiss) { CameraContent(onDismiss, onPhoto) }
        }
    }
}

@Composable
private fun BoxScope.CameraPermission(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        denied = !allowed
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    when {
        granted -> content()
        denied -> CameraMessage(
            icon = MaterialSymbols.Rounded.No_photography,
            text = stringResource(R.string.camera_permission_denied),
            action = stringResource(R.string.camera_open_settings),
            onAction = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.CameraContent(onDismiss: () -> Unit, onPhoto: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = (context.applicationContext as App).container
    val frame by remember(container) { container.settings.settings.map { it.chat.cameraFrame } }
        .collectAsStateWithLifecycle(initialValue = null)
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var captured by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val current = frame ?: return
    val imageCapture = remember(current) {
        ImageCapture.Builder().setResolutionSelector(current.resolution()).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Like a camera app: the viewfinder stays in portrait, only the controls turn, and photos keep the phone's angle.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { if (activity != null && previous != null) activity.requestedOrientation = previous }
    }
    var rotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation != ORIENTATION_UNKNOWN) rotation = surfaceRotation(orientation)
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    LaunchedEffect(rotation, imageCapture) { imageCapture.targetRotation = rotation }
    var turned by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rotation) { turned += shortestTurn(turned, rotation.uprightDegrees()) }
    val iconAngle by animateFloatAsState(turned, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "controls")

    LaunchedEffect(current, lensFacing, captured) {
        if (captured != null) return@LaunchedEffect
        try {
            val cameras = ProcessCameraProvider.awaitInstance(context).also { provider = it }
            val preview = Preview.Builder().setResolutionSelector(current.resolution()).build().also { it.surfaceProvider = previewView.surfaceProvider }
            val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture)
            // Full screen keeps exactly what the viewfinder shows: the photo is cropped to the screen's own shape.
            if (current == CameraFrame.FULL_SCREEN) previewView.awaitViewPort()?.let(group::setViewPort)
            cameras.unbindAll()
            cameras.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(lensFacing).build(), group.build())
            error = null
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    LaunchedEffect(flash, imageCapture) { imageCapture.flashMode = flash }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }
    BackHandler(enabled = captured != null) { captured?.let { File(it).delete() }; captured = null }

    val photo = captured
    if (photo != null) {
        Review(
            file = File(photo),
            onRetake = { File(photo).delete(); captured = null },
            onAttach = { onPhoto(CameraCapture.uriFor(context, File(photo))) },
        )
        return
    }

    val viewfinder = when (current) {
        CameraFrame.FULL_SCREEN -> Modifier.fillMaxSize()
        CameraFrame.RATIO_16_9 -> Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(PORTRAIT_16_9).clip(RoundedCornerShape(VIEWFINDER_CORNER))
        CameraFrame.RATIO_4_3 -> Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(PORTRAIT_4_3).clip(RoundedCornerShape(VIEWFINDER_CORNER))
    }
    AndroidView(factory = { previewView }, modifier = viewfinder)

    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIconButton(MaterialSymbols.Rounded.Close, stringResource(R.string.camera_close), iconAngle, onClick = onDismiss)
        Spacer(Modifier.weight(1f))
        OverlayIconButton(flash.icon(), stringResource(flash.label()), iconAngle) { flash = flash.next() }
    }

    error?.let { message ->
        Text(
            stringResource(R.string.camera_error, message),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
        )
    }

    Row(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(shape = CircleShape, color = OverlayColor, contentColor = Color.White, modifier = Modifier.size(SIDE_CONTROL)) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(current.label()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.graphicsLayer { rotationZ = iconAngle })
            }
        }
        ShutterButton(busy = busy) {
            busy = true
            val file = CameraCapture.newPhotoFile(context)
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        busy = false
                        captured = file.path
                    }

                    override fun onError(exception: ImageCaptureException) {
                        busy = false
                        error = exception.message ?: exception.javaClass.simpleName
                    }
                },
            )
        }
        OverlayIconButton(MaterialSymbols.Rounded.Cameraswitch, stringResource(R.string.camera_switch), iconAngle, Modifier.size(SIDE_CONTROL)) {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShutterButton(busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "shutter")
    val description = stringResource(R.string.camera_shutter)
    Box(
        Modifier.size(SHUTTER).border(SHUTTER_RING, Color.White, CircleShape).padding(SHUTTER_GAP),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            enabled = !busy,
            // While the photo is saved the button turns into an expressive cookie with a loading indicator.
            shape = if (busy) MaterialShapes.Cookie9Sided.toShape() else CircleShape,
            color = Color.White,
            interactionSource = interaction,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.semantics { contentDescription = description },
        ) {
            if (busy) Box(contentAlignment = Alignment.Center) { LoadingIndicator(color = Color.Black) }
        }
    }
}

@Composable
private fun OverlayIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, angle: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = OverlayColor, contentColor = Color.White),
        modifier = modifier.size(OVERLAY_BUTTON),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.graphicsLayer { rotationZ = angle })
    }
}

@Composable
private fun BoxScope.Review(file: File, onRetake: () -> Unit, onAttach: () -> Unit) {
    AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    Row(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onRetake,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = OverlayColor, contentColor = Color.White),
            modifier = Modifier.weight(1f).height(REVIEW_BUTTON),
        ) {
            Icon(MaterialSymbols.Rounded.Replay, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.camera_retake), style = MaterialTheme.typography.titleMedium)
        }
        Button(onClick = onAttach, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(REVIEW_BUTTON)) {
            Icon(MaterialSymbols.Rounded.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.camera_attach), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BoxScope.CameraMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.align(Alignment.Center).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(action) }
        OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Text(stringResource(R.string.camera_close))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** OrientationEventListener degrees (how far the phone is turned clockwise) to the rotation a photo is saved with. */
internal fun surfaceRotation(orientation: Int): Int = when (orientation) {
    in 45 until 135 -> Surface.ROTATION_270
    in 135 until 225 -> Surface.ROTATION_180
    in 225 until 315 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}

/** How far the controls turn (clockwise) so they stay upright for that rotation. */
internal fun Int.uprightDegrees(): Float = when (this) {
    Surface.ROTATION_90 -> 90f
    Surface.ROTATION_180 -> 180f
    Surface.ROTATION_270 -> -90f
    else -> 0f
}

/** The turn from [from] to [to] the short way round, so going from -90 to 180 does not spin through 270 degrees. */
internal fun shortestTurn(from: Float, to: Float): Float = ((to - from) % 360f + 540f) % 360f - 180f

private suspend fun PreviewView.awaitViewPort() = run {
    if (width == 0 || height == 0) suspendCancellableCoroutine { done -> doOnLayout { done.resume(Unit) } }
    viewPort
}

private fun CameraFrame.resolution(): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(if (this == CameraFrame.RATIO_4_3) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .build()

private fun CameraFrame.label(): Int = when (this) {
    CameraFrame.FULL_SCREEN -> R.string.camera_frame_full_short
    CameraFrame.RATIO_16_9 -> R.string.camera_frame_16_9
    CameraFrame.RATIO_4_3 -> R.string.camera_frame_4_3
}

private fun Int.next(): Int = when (this) {
    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
    else -> ImageCapture.FLASH_MODE_OFF
}

private fun Int.icon() = when (this) {
    ImageCapture.FLASH_MODE_AUTO -> MaterialSymbols.Rounded.Flash_auto
    ImageCapture.FLASH_MODE_ON -> MaterialSymbols.Rounded.Flash_on
    else -> MaterialSymbols.Rounded.Flash_off
}

private fun Int.label(): Int = when (this) {
    ImageCapture.FLASH_MODE_AUTO -> R.string.camera_flash_auto
    ImageCapture.FLASH_MODE_ON -> R.string.camera_flash_on
    else -> R.string.camera_flash_off
}

private val OverlayColor = Color.Black.copy(alpha = 0.45f)
private val VIEWFINDER_CORNER = 28.dp
private val SHUTTER = 84.dp
private val SHUTTER_RING = 4.dp
private val SHUTTER_GAP = 7.dp
private val SIDE_CONTROL = 56.dp
private val OVERLAY_BUTTON = 48.dp
private val REVIEW_BUTTON = 56.dp
private const val PRESSED_SCALE = 0.86f
private const val PORTRAIT_16_9 = 9f / 16f
private const val PORTRAIT_4_3 = 3f / 4f
