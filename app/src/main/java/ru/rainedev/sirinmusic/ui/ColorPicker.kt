package ru.rainedev.sirinmusic.ui

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ColorPicker(initial: Int, onApply: (Int) -> Unit) {
    val hsv = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by rememberSaveable(initial) { mutableFloatStateOf(hsv[0]) }
    var saturation by rememberSaveable(initial) { mutableFloatStateOf(hsv[1]) }
    var brightness by rememberSaveable(initial) { mutableFloatStateOf(hsv[2]) }
    val chosen = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
    var hex by rememberSaveable { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    LaunchedEffect(hue, saturation, brightness) { hex = "%06X".format(chosen and 0xFFFFFF) }
    val parsed = hex.removePrefix("#").takeIf { it.length == 6 }?.takeIf { it.all { ch -> ch in "0123456789abcdefABCDEF" } }?.toLongOrNull(16)
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(56.dp).background(Color(parsed?.let { (it or 0xFF000000L).toInt() } ?: chosen)))
        Text(strings(R.string.color_picker_1), Modifier.padding(top = 12.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Brush.horizontalGradient(
            (0..6).map { Color.hsv(it * 60f, 1f, 1f) })))
        Slider(hue, { hue = it }, valueRange = 0f..360f)
        Text(strings(R.string.color_picker_2))
        Slider(saturation, { saturation = it }, valueRange = 0f..1f)
        Text(strings(R.string.color_picker_3))
        Slider(brightness, { brightness = it }, valueRange = 0f..1f)
        OutlinedTextField(hex, { hex = it.take(7) }, label = { Text(strings(R.string.color_picker_4)) },
            prefix = { Text("#") }, singleLine = true, isError = parsed == null,
            supportingText = { Text(strings(R.string.color_picker_5)) }, modifier = Modifier.fillMaxWidth())
        Button(enabled = parsed != null, onClick = { parsed?.let { onApply((it or 0xFF000000L).toInt()) } }) {
            Text(strings(R.string.color_picker_6))
        }
    }
}
