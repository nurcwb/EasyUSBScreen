package br.com.infonur.easyusbscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import br.com.infonur.easyusbscreen.resources.Res
import br.com.infonur.easyusbscreen.resources.app_icon
import org.jetbrains.compose.resources.painterResource

@Composable
fun appIconPainter(): Painter = painterResource(Res.drawable.app_icon)
