package com.dmwnezes.palanque.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palanque.data.SaveStore
import com.dmwnezes.palanque.update.UpdateDialog
import com.dmwnezes.palanque.update.UpdatePill
import com.dmwnezes.palanque.world.Brasil

@Composable
fun HomeScreen(br: Brasil, store: SaveStore, onNew: () -> Unit, onLoad: (String) -> Unit) {
    var showLoad by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    val latest = remember { store.latest() }
    Box(Modifier.fillMaxSize().background(Ink.ocean)) {
        RegionPicker(br.states, Modifier.fillMaxSize().padding(top = 110.dp, bottom = 200.dp, start = 8.dp, end = 8.dp).alpha(0.9f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 0.92f to Ink.oceanDeep)))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            UpdatePill(Modifier.align(Alignment.End)) { showUpdate = true }
            Spacer(Modifier.height(18.dp))
            Text("PALANQUE", style = Type.big.copy(fontSize = 40.sp, letterSpacing = 4.sp), color = Ink.gold)
            Text("Da Câmara Municipal ao Planalto", style = Type.heading, color = Ink.muted)
            Spacer(Modifier.weight(1f))
            if (latest != null) {
                Row(
                    Modifier.fillMaxWidth().clip(Shapes.card).background(Ink.gold).clickable { onLoad(latest.slot) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Ink.onGold, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Continuar", style = Type.heading, color = Ink.onGold)
                        Text("${latest.role} de ${latest.city} · ${latest.dateLabel()}", style = Type.label, color = Ink.onGold.copy(alpha = 0.75f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Novo jogo", Modifier.weight(1f), if (latest == null) BtnKind.PRIMARY else BtnKind.SECONDARY, Icons.Rounded.Add) { onNew() }
                Btn("Carregar", Modifier.weight(1f), BtnKind.SECONDARY, Icons.Rounded.FolderOpen, enabled = latest != null) { showLoad = true }
            }
            Spacer(Modifier.height(14.dp))
            Text("criado por: @dmwnezes", style = Type.small, color = Ink.faint, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (showLoad) Overlay({ showLoad = false }) {
            Text("Carregar", style = Type.title, color = Ink.text)
            Spacer(Modifier.height(10.dp))
            for (slot in listOf("auto") + SaveStore.SLOTS) {
                val m = store.meta(slot) ?: continue
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).clickable { onLoad(slot) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (slot == "auto") "Automático" else "Espaço $slot", style = Type.label, color = Ink.muted)
                        Text("${m.role} de ${m.city}", style = Type.body, color = Ink.text)
                    }
                    Text(m.dateLabel(), style = Type.label, color = Ink.gold)
                }
            }
        }
        if (showUpdate) UpdateDialog { showUpdate = false }
    }
}
