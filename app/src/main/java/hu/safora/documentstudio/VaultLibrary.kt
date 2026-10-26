package hu.safora.documentstudio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun vaultLabel(name: String, english: Boolean) = if (name == "Unsorted") if (english) "Default vault" else "Alapértelmezett tárhely" else name

@Composable internal fun VaultLibrary(store: Store, current: String, onSelect: (String) -> Unit, onImport: (String) -> Unit,
    onScan: (String) -> Unit, onCreate: () -> Unit, favoritesOnly: Boolean = false, initialQuery:String="") {
    val tr: (String,String)->String={hu,en->if(store.english)en else hu}
    var query by remember(current) { mutableStateOf(if(current.isEmpty())initialQuery else "") }
    var unlock by remember { mutableStateOf<String?>(null) }
    val inside = current.isNotEmpty() && current in store.vaultNames
    val allowed = inside && store.canEnterVault(current)
    SecureVaultWindow(allowed && store.hasVaultPassword(current))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (inside) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = { onSelect("") }) { Icon(Icons.Outlined.ArrowBack, tr("Tárhelyek", "All vaults")) }
                Spacer(Modifier.width(10.dp))
                Text(vaultLabel(current,store.english), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (store.hasVaultPassword(current) && allowed) OutlinedIconButton(onClick = { VaultSessions.lock(current) }) { Icon(Icons.Outlined.Lock, tr("Lezárás", "Lock vault")) }
            }
            if (!allowed) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp))
                        Text(tr("A tárhely zárolva van", "This vault is locked"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("A fájlok megtekintéséhez add meg a jelszót.", "Enter the password to view its files."))
                        Button(onClick = { unlock = current }) { Text(tr("Feloldás", "Unlock")) }
                    }
                }
            } else {
                Text(if(favoritesOnly)tr("Kedvenc dokumentumok", "Favorite documents") else tr("Dokumentumok ezen a tárhelyen", "Documents in this vault"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search,null) }, placeholder = { Text(tr("Keresés névben és tartalomban", "Search name and recognized text"), maxLines = 1) })
                val found = store.docs.filter { it.folder == current && (!favoritesOnly || it.favorite) && it.matches(query) }.sortedByDescending { it.created }
                if(found.isEmpty()) Surface(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("Nincsenek megjeleníthető fájlok", "No files to show"), fontWeight=FontWeight.SemiBold)
                        Text(if(favoritesOnly)tr("Importálás vagy szkennelés után a tárhely összes fájlja megnyílik.", "Importing or scanning opens all files in this vault.") else tr("Importálj egy dokumentumot, vagy módosítsd a keresést.", "Import a document or adjust your search."), style=MaterialTheme.typography.bodySmall)
                    }
                }
                found.forEach { DocumentRow(it,store,MaterialTheme.colorScheme.primary) }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={query="";onImport(current)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.UploadFile,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(tr("Importálás","Import")) }
                    OutlinedButton(onClick={query="";onScan(current)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.DocumentScanner,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(tr("Szkennelés","Scan")) }
                }
            }
        } else {
            Text(if(favoritesOnly)tr("Kedvencek tárhelyenként", "Favorites by vault") else tr("Dokumentumtárhelyek", "Document vaults"),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Text(tr("Válassz tárhelyet a fájlok megtekintéséhez.","Choose a vault to see its files."),color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp),leadingIcon={Icon(Icons.Outlined.Search,null)},placeholder={Text(tr("Tárhely keresése","Find a vault"))})
            store.vaultNames.filter{vaultLabel(it,store.english).contains(query,true)}.forEach { name ->
                VaultCard(name,store,favoritesOnly,onOpen={if(store.canEnterVault(name))onSelect(name) else unlock=name})
            }
            OutlinedButton(onClick=onCreate,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Icon(Icons.Outlined.CreateNewFolder,null);Spacer(Modifier.width(8.dp));Text(tr("Új tárhely","New vault"))}
        }
    }
    unlock?.let{name->VaultPasswordDialog(store,name,onDismiss={unlock=null},onUnlocked={unlock=null;onSelect(name)})}
}

@Composable private fun VaultCard(name:String,store:Store,favoritesOnly:Boolean,onOpen:()->Unit){
    val tr:(String,String)->String={hu,en->if(store.english)en else hu}
    var menu by remember{mutableStateOf(false)}
    var action by remember{mutableStateOf("")}
    var pending by remember{mutableStateOf("")}
    var unlock by remember{mutableStateOf(false)}
    fun request(value:String){menu=false;if(store.canEnterVault(name))action=value else {pending=value;unlock=true}}
    Surface(onClick=onOpen,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface){
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(if(store.hasVaultPassword(name))Icons.Outlined.Lock else Icons.Outlined.FolderOpen,null,Modifier.padding(12.dp).size(26.dp),tint=MaterialTheme.colorScheme.primary)}
            Column(Modifier.weight(1f)){
                Text(vaultLabel(name,store.english),fontWeight=FontWeight.SemiBold)
                Text("${store.docs.count{it.folder==name&&(!favoritesOnly||it.favorite)}} "+tr("fájl","files")+if(store.hasVaultPassword(name))tr(" · Jelszóval védett"," · Password protected") else "",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box{
                IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,tr("Tárhely kezelése: ","Manage vault: ")+vaultLabel(name,store.english))}
                DropdownMenu(menu,{menu=false}){
                    if(name!="Unsorted")DropdownMenuItem(text={Text(tr("Átnevezés","Rename vault"))},onClick={request("rename")})
                    DropdownMenuItem(text={Text(if(store.hasVaultPassword(name))tr("Jelszó módosítása","Change password") else tr("Jelszó beállítása","Set password"))},onClick={request("password")})
                    if(store.hasVaultPassword(name)){
                        DropdownMenuItem(text={Text(tr("Jelszó eltávolítása","Remove password"))},onClick={request("removePassword")})
                        if(store.canEnterVault(name))DropdownMenuItem(text={Text(tr("Lezárás","Lock vault"))},onClick={menu=false;VaultSessions.lock(name)})
                    }
                    if(name!="Unsorted")DropdownMenuItem(text={Text(tr("Tárhely törlése","Delete vault"))},onClick={request("delete")})
                }
            }
        }
    }
    if(unlock)VaultPasswordDialog(store,name,onDismiss={unlock=false;pending=""},onUnlocked={unlock=false;action=pending;pending=""})
    if(action=="password")VaultPasswordDialog(store,name,create=true,onDismiss={action=""},onUnlocked={action=""})
    if(action=="rename")VaultNameDialog(store,name,onDismiss={action=""})
    if(action=="delete"||action=="removePassword")AlertDialog(onDismissRequest={action=""},title={Text(if(action=="delete")tr("Tárhely törlése?","Delete vault?") else tr("Jelszó eltávolítása?","Remove password?"))},text={Text(if(action=="delete")tr("A fájlok az alapértelmezett tárhelyre kerülnek, nem törlődnek. Ha az zárolva van, előbb oldd fel.","Files will move to the default vault and will not be deleted. Unlock the default vault first if it is protected.") else tr("A tárhely jelszó nélkül lesz megnyitható.","This vault will be accessible without a password."))},confirmButton={TextButton(onClick={if(action=="delete"){store.deleteVault(name)}else store.setVaultPassword(name,null);action=""}){Text(tr("Megerősítés","Confirm"))}},dismissButton={TextButton(onClick={action=""}){Text(tr("Mégse","Cancel"))}})
}

@Composable internal fun VaultNameDialog(store:Store,old:String?=null,onDismiss:()->Unit){
    val tr:(String,String)->String={hu,en->if(store.english)en else hu}
    var name by remember{mutableStateOf(old?:"")};var error by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(old==null)tr("Új tárhely","New vault") else tr("Tárhely átnevezése","Rename vault"))},text={Column{OutlinedTextField(name,{name=it;error=false},singleLine=true,label={Text(tr("Tárhely neve","Vault name"))},isError=error);if(error)Text(tr("Ez a név már foglalt.","This vault name is already in use."),color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={val saved=if(old==null)store.addFolder(name) else store.renameVault(old,name);if(saved)onDismiss() else error=true},enabled=name.isNotBlank()&&name.trim()!=old){Text(tr("Mentés","Save"))}},dismissButton={TextButton(onClick=onDismiss){Text(tr("Mégse","Cancel"))}})
}

@Composable internal fun VaultPasswordDialog(store:Store,name:String,create:Boolean=false,onDismiss:()->Unit,onUnlocked:()->Unit){
    val tr:(String,String)->String={hu,en->if(store.english)en else hu}
    var password by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var seconds by remember{mutableLongStateOf(VaultSessions.waitSeconds(name))};val scope=rememberCoroutineScope()
    LaunchedEffect(name){while(true){seconds=VaultSessions.waitSeconds(name);delay(1000)}}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),title={Text(if(create)tr("Jelszó beállítása","Set vault password") else tr("Tárhely feloldása","Unlock vault"))},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(vaultLabel(name,store.english),fontWeight=FontWeight.SemiBold)
        OutlinedTextField(password,{password=it;error=""},label={Text(tr("Jelszó","Password"))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!busy)
        if(create){OutlinedTextField(confirm,{confirm=it;error=""},label={Text(tr("Jelszó megerősítése","Confirm password"))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!busy)
            Text(tr("Legalább 6 karakter. Nincs jelszó-helyreállítás. Ez alkalmazászár, nem fájltitkosítás.","At least 6 characters. No password recovery. This is an app access lock, not file encryption."),style=MaterialTheme.typography.bodySmall)}
        if(seconds>0)Text(tr("Próbáld újra $seconds másodperc múlva.","Try again in $seconds seconds."))
        if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={TextButton(onClick={scope.launch{
        busy=true
        try{
            val chars=password.toCharArray()
            try{
                if(create){val record=withContext(Dispatchers.IO){VaultPasswords.create(chars)};store.setVaultPassword(name,record);onUnlocked()}
                else if(withContext(Dispatchers.IO){store.unlockVault(name,chars)})onUnlocked() else {password="";error=tr("Helytelen jelszó.","Incorrect password.");seconds=VaultSessions.waitSeconds(name)}
            }finally{chars.fill('\u0000')}
        }catch(e:Exception){error=tr("A művelet sikertelen. Próbáld újra.","Could not complete this action. Please retry.")}
        finally{busy=false}
    }},enabled=!busy&&seconds==0L&&password.isNotEmpty()&&(!create||(password.length>=6&&password==confirm))){Text(if(create)tr("Mentés","Save password") else tr("Feloldás","Unlock"))}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text(tr("Mégse","Cancel"))}})
}

@Composable internal fun VaultDestinationDialog(store:Store,onDismiss:()->Unit,onSelected:(String)->Unit){
    var unlock by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(store.english)"Choose a vault" else "Válassz tárhelyet")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){store.vaultNames.forEach{name->TextButton(onClick={if(store.canEnterVault(name))onSelected(name)else unlock=name}){Icon(if(store.hasVaultPassword(name))Icons.Outlined.Lock else Icons.Outlined.FolderOpen,null);Spacer(Modifier.width(8.dp));Text(vaultLabel(name,store.english))}}}},confirmButton={TextButton(onClick=onDismiss){Text(if(store.english)"Cancel" else "Mégse")}})
    unlock?.let{name->VaultPasswordDialog(store,name,onDismiss={unlock=null},onUnlocked={unlock=null;onSelected(name)})}
}

@Composable internal fun SecureVaultWindow(enabled:Boolean){
    val activity=androidx.activity.compose.LocalActivity.current ?: return
    DisposableEffect(enabled){if(enabled)activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose{if(enabled)activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}}
}

@Composable internal fun VaultAccessScreen(store:Store,name:String,onExit:()->Unit,content:@Composable ()->Unit){
    var unlock by remember{mutableStateOf(false)}
    if(store.canEnterVault(name))content() else Surface(Modifier.fillMaxSize()){
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
            Icon(Icons.Outlined.Lock,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp));Text(vaultLabel(name,store.english),style=MaterialTheme.typography.titleLarge)
            Text(if(store.english)"Unlock the vault to continue" else "A folytatáshoz oldd fel a tárhelyet",Modifier.padding(vertical=16.dp))
            Button(onClick={unlock=true}){Text(if(store.english)"Unlock" else "Feloldás")}
            TextButton(onClick=onExit){Text(if(store.english)"Back" else "Vissza")}
        }
    }
    if(unlock)VaultPasswordDialog(store,name,onDismiss={unlock=false},onUnlocked={unlock=false})
}
