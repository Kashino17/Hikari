package com.hikari.app.ui.tuning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariAmberSoft
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipMulti(
    options: List<String>,
    values: List<String>,
    onChange: (List<String>) -> Unit,
    renderLabel: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { opt ->
            val active = values.contains(opt)
            Box(
                modifier = Modifier
                    .clickable {
                        onChange(
                            if (active) values - opt else values + opt,
                        )
                    }
                    .background(
                        if (active) HikariAmber else HikariSurface,
                        RoundedCornerShape(20.dp),
                    )
                    .let {
                        if (active) it
                        else it.border(0.5.dp, HikariBorder, RoundedCornerShape(20.dp))
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    renderLabel(opt),
                    color = if (active) Color.Black else HikariTextMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFreeInput(
    values: List<String>,
    onChange: (List<String>) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun add() {
        val v = draft.trim()
        if (v.isNotEmpty() && !values.contains(v)) onChange(values + v)
        draft = ""
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(HikariSurface, RoundedCornerShape(8.dp))
            .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
            .clickable { focus.requestFocus() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .wrapContentHeight(Alignment.Top),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            values.forEach { v ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(HikariAmberSoft, RoundedCornerShape(20.dp))
                        .border(0.5.dp, HikariAmber.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(
                        v,
                        color = HikariAmber,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp)
                            .clickable { onChange(values - v) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "$v entfernen",
                            tint = HikariAmber,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            BasicTextField(
                value = draft,
                onValueChange = {
                    if (it.endsWith(",") || it.endsWith("\n")) {
                        draft = it.dropLast(1)
                        add()
                    } else {
                        draft = it
                    }
                },
                singleLine = true,
                textStyle = TextStyle(color = HikariText, fontSize = 13.sp),
                cursorBrush = SolidColor(HikariAmber),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier
                    .focusRequester(focus)
                    .heightIn(min = 28.dp)
                    .padding(horizontal = 4.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty() && values.isEmpty()) {
                        Text(
                            placeholder,
                            color = HikariTextFaint,
                            style = TextStyle(fontSize = 13.sp),
                        )
                    }
                    inner()
                },
            )
        }
    }
}
