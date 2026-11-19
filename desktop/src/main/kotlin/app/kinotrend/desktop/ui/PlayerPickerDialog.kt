package app.kinotrend.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Окно выбора плеера перед запуском раздачи — как в LostFilm.
 * Клик по строке сразу запускает выбранный плеер; чекбокс отключает вопрос навсегда.
 */
@Composable
fun PlayerPickerDialog(
    title: String,
    players: List<Pair<String, String>>,
    defaultPath: String,
    onPick: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var dontAsk by remember { mutableStateOf(false) }
    val available = players.filter { File(it.second).isFile }

    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000)).clickable { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(480.dp).background(Surface, RoundedCornerShape(16.dp))
                .clickable { }.padding(22.dp),
        ) {
            Text("Выберите плеер", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (title.isNotBlank()) {
                Text(
                    title,
                    color = TextMuted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))

            available.forEach { (name, path) ->
                val isDefault = path.equals(defaultPath, true)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDefault) Color(0xFF334047) else Color.Transparent)
                        .clickable { onPick(path, dontAsk) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            color = TextMain, fontSize = 14.sp,
                            fontWeight = if (isDefault) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            path,
                            color = TextMuted, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isDefault) {
                        Text("по умолчанию", color = Accent, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { dontAsk = !dontAsk },
            ) {
                Checkbox(
                    checked = dontAsk,
                    onCheckedChange = { dontAsk = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Accent,
                        checkmarkColor = Color.Black,
                    ),
                )
                Text("Больше не спрашивать", color = TextMain, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Отмена", color = TextMuted) }
            }
        }
    }
}
