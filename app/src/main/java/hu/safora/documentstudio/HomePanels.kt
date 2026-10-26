package hu.safora.documentstudio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable internal fun HomeShortcut(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable internal fun ToolsAndSettings(store: Store, onScan: () -> Unit, onNavigate: (String) -> Unit, onGuide:()->Unit) {
    val tr: (String, String) -> String = { hu, en -> if (store.english) en else hu }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MenuCard(tr("Dokumentumszkenner", "Document scanner"), Icons.Outlined.DocumentScanner, onScan)
        MenuCard(tr("Alkalmazásbemutató", "App guide"), Icons.Outlined.AutoStories, onGuide)
        MenuCard(tr("Sablonstúdió", "Template studio"), Icons.Outlined.Article) { onNavigate("templates") }
        MenuCard(tr("Kedvencek", "Favorites"), Icons.Outlined.StarOutline) { onNavigate("favorites") }
        MenuCard(tr("A Saforáról", "About Safora"), Icons.Outlined.Business) { onNavigate("company") }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(tr("Megjelenés", "Appearance"), fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(tr("Sötét mód", "Dark mode"), Modifier.weight(1f))
            Switch(store.dark, store::theme)
        }
        Text(tr("Nyelv", "Language"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = !store.english, onClick = { store.language(false) }, label = { Text("Magyar") })
            FilterChip(selected = store.english, onClick = { store.language(true) }, label = { Text("English") })
        }
        Text(tr("A fájlok csak ezen az eszközön tárolódnak.", "Files are stored on this device only."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun SettingsPopup(store: Store, onDismiss: () -> Unit, onScan: () -> Unit, onNavigate: (String) -> Unit, onGuide:()->Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.86f), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(if(windowWidthDp()<360)16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SaforaLogo(Modifier.size(38.dp));Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (store.english) "Tools & settings" else "Eszközök és beállítások", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Safora Document Studio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedIconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, if (store.english) "Close settings" else "Beállítások bezárása") }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { ToolsAndSettings(store, onScan, onNavigate, onGuide) }
            }
        }
    }
}
