package hu.safora.documentstudio

import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Violet = Color(0xFF7258D9)
private val Lavender = Color(0xFFB4A2FF)
private val Ivory = Color(0xFFF8F7F3)
private val Graphite = Color(0xFF17171E)
private val DarkSurface = Color(0xFF24242E)
private val Ink = Color(0xFF242333)

class MainActivity:ComponentActivity(){
    private lateinit var store:Store
    override fun onCreate(savedInstanceState:Bundle?){
        val splash=installSplashScreen()
        super.onCreate(savedInstanceState);configureBrandSplash(splash);store=Store(this)
        androidx.work.WorkManager.getInstance(this).getWorkInfosByTagLiveData("ocr").observe(this){store.refreshText()}
        androidx.work.WorkManager.getInstance(this).getWorkInfosByTagLiveData("reminders").observe(this){store.refresh()}
        setContent{SaforaApp(store, intent.getBooleanExtra("showDeadlines",false))}
    }
    override fun onResume(){super.onResume();if(::store.isInitialized){store.refresh();store.resumeWork()}}
}

@Composable private fun SaforaApp(store:Store,showDeadlines:Boolean=false){
    val dark=store.dark;val accent=if(dark)Lavender else Violet
    val scheme=saforaColorScheme(dark)
    SaforaSystemBars(dark)
    MaterialTheme(colorScheme=scheme,typography=responsiveTypography()){
        val ctx=LocalContext.current
        var onboarding by rememberSaveable{mutableStateOf(!OnboardingPreferences.completed(ctx))}
        if(onboarding){OnboardingScreen(store.english,store::language,onFinish={OnboardingPreferences.complete(ctx);onboarding=false});return@MaterialTheme}
        var page by rememberSaveable { mutableStateOf(if(showDeadlines)"deadlines" else "home") }; var addMenu by remember { mutableStateOf(false) };var newFolder by remember{mutableStateOf(false)}
        var newDeadline by remember{mutableStateOf(false)};var newDraft by remember{mutableStateOf(false)}; var editingDraft by remember{mutableStateOf<Draft?>(null)}; var recentType by rememberSaveable{mutableStateOf("All")}
        var settingsPopup by rememberSaveable{mutableStateOf(false)}
        var detailOrigin by rememberSaveable{mutableStateOf("home")}
        val navigate:(String)->Unit={target->detailOrigin=page;page=target;settingsPopup=false}
        var selectedFolder by rememberSaveable{mutableStateOf("")};var query by rememberSaveable{mutableStateOf("")}
        BackHandler(enabled=page!="home"){if(page in listOf("docs","favorites")&&selectedFolder.isNotEmpty())selectedFolder="" else page=if(page in listOf("company","templates","favorites"))detailOrigin else "home"}
        var destinationAction by remember{mutableStateOf("")}
        var importTarget by rememberSaveable{mutableStateOf("Unsorted")}
        var pendingUris by rememberSaveable{mutableStateOf(arrayListOf<String>())}
        var pendingUnlock by rememberSaveable{mutableStateOf(false)}
        val scanLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){store.refresh()}
        val launchScanner:(String)->Unit={folder->scanLauncher.launch(Intent(ctx,ScannerActivity::class.java).putExtra("english",store.english).putExtra("dark",store.dark).putExtra("folder",folder))}
        val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
            if(store.canEnterVault(importTarget))uris.forEach{store.import(it,importTarget)}
            else if(uris.isNotEmpty()){pendingUris=ArrayList(uris.map{it.toString()});pendingUnlock=true}
        }
        val launchImport:(String)->Unit={folder->importTarget=folder;importer.launch(arrayOf("application/pdf","image/*"))}
        val startScanner:()->Unit={destinationAction="scan"}
        val startImport:()->Unit={destinationAction="import"}
        val t:(String,String)->String={hu,en->if(store.english)en else hu}
        Scaffold(containerColor=scheme.background,bottomBar={
            NavigationBar(containerColor=scheme.surface){
                listOf(Triple("home",t("Főoldal","Home"),Icons.Outlined.Home),Triple("docs",t("Iratok","Documents"),Icons.Outlined.FolderOpen),Triple("add",t("Új","Add"),Icons.Outlined.AddCircleOutline),Triple("deadlines",t("Határidők","Deadlines"),Icons.Outlined.Event),Triple("more",t("Továbbiak","More"),Icons.Outlined.Apps)).forEach{(key,label,icon)->
                    NavigationBarItem(selected=page==key,onClick={if(key=="add")addMenu=true else {page=key;if(key=="docs")selectedFolder=""}},icon={Icon(icon,null)},label={Text(label,maxLines=1)},colors=NavigationBarItemDefaults.colors(indicatorColor=scheme.surface,selectedIconColor=accent,selectedTextColor=accent),alwaysShowLabel=true)
                }
            }
        }){pad->Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal=screenGutter()),verticalArrangement=Arrangement.spacedBy(if(page=="home")10.dp else 16.dp)){
            Spacer(Modifier.height(8.dp))
            when(page){
                "home"->{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            SaforaLogo(Modifier.size(32.dp))
                            Text("SAFORA / DOCUMENT STUDIO",color=accent,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                        }
                        Text(t("Dokumentumaim","My documents"),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                        Text(t("Minden fontos irat egy helyen.","Every important file in one place."),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                    };OutlinedIconButton(onClick={settingsPopup=true}){Icon(Icons.Outlined.Tune,t("Beállítások","Settings"))}}
                    SearchField(query,{query=it;selectedFolder="";page="docs"},t("Tárhely keresése...","Find a vault..."))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        StatCard(Modifier.weight(1f),"${store.docs.size}",t("Dokumentumok","Documents"),Icons.Outlined.Description,highlight=true)
                        StatCard(Modifier.weight(1f),"${store.deadlines.count{!it.done}}",t("Közelgő határidők","Upcoming deadlines"),Icons.Outlined.Event)
                    }
                    HomeShortcut(t("Kedvencek","Favorites"),t("${store.docs.count{it.favorite}} kedvenc dokumentum","${store.docs.count{it.favorite}} favorite documents"),Icons.Outlined.StarOutline){selectedFolder="";navigate("favorites")}
                    SectionHeader(t("Tárhelyek","Vaults")){selectedFolder="";page="docs"}
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        store.folders.take(2).forEach{folder->Surface(onClick={selectedFolder=folder;page="docs"},modifier=Modifier.weight(1f),shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,scheme.outline)){Column(Modifier.heightIn(min=88.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Icon(if(store.hasVaultPassword(folder))Icons.Outlined.Lock else Icons.Outlined.Folder,tint=accent,contentDescription=null,modifier=Modifier.size(22.dp))
                            Column{Text(folder,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold);Text("${store.docs.count{it.folder==folder}} ${t("fájl","files")}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }}}
                    }
                    HomeShortcut(t("Dokumentumtárhelyek megnyitása","Open document vaults"),t("A fájlok a kiválasztott tárhelyen láthatók.","Files are visible inside their vault."),Icons.Outlined.FolderOpen){selectedFolder="";page="docs"}
                    CompanyHomeBanner(store.english){navigate("company")}
                    if(store.deadlines.any{!it.done}){SectionHeader(t("Közelgő határidők","Upcoming deadlines")){page="deadlines"};store.deadlines.filterNot{it.done}.take(2).forEach{ReminderCard(it,store)}}
                }
                "docs"->{VaultLibrary(store,selectedFolder,{selectedFolder=it},launchImport,launchScanner,{newFolder=true},initialQuery=query)}
                "deadlines"->{Title(t("Határidők","Deadlines"),t("Ne maradj le a fontos dátumokról.","Keep important dates in view."))
                    NotificationStatus(store.english)
                    Button(onClick={newDeadline=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text(t(" Határidő hozzáadása"," Add deadline"))}
                    if(store.deadlines.isEmpty())EmptyCard(t("Nincsenek határidők","No deadlines"),t("Adj hozzá egy fontos dátumot.","Add a date you want to track.")){newDeadline=true}
                    store.deadlines.sortedBy{it.date}.forEach{ReminderCard(it,store)}
                }
                "more"->{Title(t("Eszközök és beállítások","Tools & settings"),"Safora Document Studio")
                    ToolsAndSettings(store,startScanner,navigate,onGuide={onboarding=true})
                }
                "templates"->{Title(t("Sablonstúdió","Template studio"),t("Szerkeszthető üzleti szövegek","Editable business text"))
                    Button(onClick={newDraft=true}){Text(t("Új dokumentum","New draft"))}
                    val samples=listOf(t("Üzleti levél","Business letter"),t("Értekezleti jegyzőkönyv","Meeting minutes"),t("Fizetési emlékeztető","Payment reminder"),t("Dokumentumkérés","Document request"))
                    Text(t("A sablonok kiindulópontok; nem minősülnek jogi tanácsnak.","Templates are starting points, not legal advice."),style=MaterialTheme.typography.bodySmall)
                    samples.forEach{s->MenuCard(s,Icons.Outlined.Description){editingDraft=Draft("",s,if(store.english)"Date: [date]\nFrom: [name / company]\nTo: [recipient]\n\nSubject: $s\n\n[Enter details here]\n\nBest regards,\n[name]" else "Dátum: [dátum]\nFeladó: [név / cég]\nCímzett: [címzett]\n\nTárgy: $s\n\n[Részletek megadása]\n\nÜdvözlettel,\n[név]")}}
                    store.drafts.forEach{draft->Surface(shape=RoundedCornerShape(14.dp),color=scheme.surface){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(draft.title,modifier=Modifier.weight(1f).clickable{editingDraft=draft});IconButton(onClick={store.exportDraft(draft)}){Icon(Icons.Outlined.PictureAsPdf,if(store.english)"Export PDF" else "PDF exportálása")};IconButton(onClick={store.deleteDraft(draft.id)}){Icon(Icons.Outlined.Delete,null)}}}}
                }
                "favorites"->{VaultLibrary(store,selectedFolder,{selectedFolder=it},
                    {folder->page="docs";selectedFolder=folder;query="";launchImport(folder)},
                    {folder->page="docs";selectedFolder=folder;query="";launchScanner(folder)},
                    {newFolder=true},favoritesOnly=true)}
                "company"->{CompanyPage(store.english)}
            }
            Spacer(Modifier.height(12.dp))
        }}
        if(settingsPopup)SettingsPopup(store,onDismiss={settingsPopup=false},onScan={settingsPopup=false;startScanner()},onNavigate=navigate,onGuide={settingsPopup=false;onboarding=true})
        if(addMenu)AlertDialog(onDismissRequest={addMenu=false},title={Text(t("Hozzáadás","Add new"))},text={Column{
            TextButton(onClick={addMenu=false;startScanner()}){Text(t("Dokumentum szkennelése","Scan document"))}
            TextButton(onClick={addMenu=false;startImport()}){Text(t("Dokumentum importálása","Import document"))}
            TextButton(onClick={addMenu=false;newFolder=true}){Text(t("Új tárhely","New vault"))}
            TextButton(onClick={addMenu=false;newDeadline=true}){Text(t("Új határidő","New deadline"))}
            TextButton(onClick={addMenu=false;newDraft=true}){Text(t("Új sablon","New draft"))}
        }},confirmButton={TextButton(onClick={addMenu=false}){Text(t("Bezárás","Close"))}})
        if(newFolder)VaultNameDialog(store,onDismiss={newFolder=false})
        if(destinationAction.isNotEmpty())VaultDestinationDialog(store,onDismiss={destinationAction=""},onSelected={folder->val action=destinationAction;destinationAction="";if(action=="scan")launchScanner(folder)else launchImport(folder)})
        if(pendingUnlock)VaultPasswordDialog(store,importTarget,onDismiss={pendingUnlock=false;pendingUris=arrayListOf()},onUnlocked={pendingUnlock=false;pendingUris.forEach{store.import(Uri.parse(it),importTarget)};pendingUris=arrayListOf()})
        if(newDeadline)DeadlineEditor(store,onDismiss={newDeadline=false})
        editingDraft?.let { draft->DraftDialog(store.english,onDismiss={editingDraft=null},initial=draft,onSave={a,b->if(draft.id.isEmpty())store.addDraft(a,b) else store.updateDraft(draft.id,a,b);editingDraft=null}) }
        if(newDraft)DraftDialog(store.english,onDismiss={newDraft=false},onSave={a,b->store.addDraft(a,b);newDraft=false})
    }
}
@Composable private fun Title(a:String,b:String){Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text(a,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);if(b.isNotBlank())Text(b,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun SearchField(q:String,onChange:(String)->Unit,hint:String){OutlinedTextField(value=q,onValueChange=onChange,placeholder={Text(hint,maxLines=1)},leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp))}
@Composable private fun SectionHeader(s:String,onClick:()->Unit){Row(Modifier.fillMaxWidth().heightIn(min=36.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(s,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium);IconButton(onClick=onClick,modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.ArrowForward,s,modifier=Modifier.size(18.dp))}}}
@Composable private fun StatCard(modifier:Modifier,n:String,label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,highlight:Boolean=false){Surface(modifier=modifier,shape=RoundedCornerShape(16.dp),color=if(highlight)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface){Column(Modifier.heightIn(min=88.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp));Text(n,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
    Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}}}
@Composable internal fun DocumentRow(d:Doc,store:Store,accent:Color){
    var menu by remember{mutableStateOf(false)};var rename by remember{mutableStateOf(false)};var move by remember{mutableStateOf(false)};var remove by remember{mutableStateOf(false)};var ocr by remember{mutableStateOf(false)};var remind by remember{mutableStateOf(false)}
    val t:(String,String)->String={hu,en->if(store.english)en else hu}
    Surface(shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface){Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp).heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically){
        Surface(shape=RoundedCornerShape(9.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(if(d.type=="application/pdf")Icons.Outlined.PictureAsPdf else Icons.Outlined.Image,t("Megnyitás","Open"),tint=accent,modifier=Modifier.padding(8.dp).size(22.dp))}
        Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f).clickable{store.open(d)}){Text(d.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold);Text("${if(d.type=="application/pdf")"PDF" else t("Kép","Image")} · ${d.size/1024} KB · ${SimpleDateFormat("dd MMM",if(store.english)Locale.ENGLISH else Locale.forLanguageTag("hu")).format(Date(d.created))}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Box{IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreHoriz,t("Műveletek","Actions"))};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
            DropdownMenuItem(text={Text(t("Szövegfelismerés (OCR)","Recognize text (OCR)"))},onClick={menu=false;ocr=true})
            DropdownMenuItem(text={Text(t("Emlékeztető hozzáadása","Add reminder"))},onClick={menu=false;remind=true})
            DropdownMenuItem(text={Text(t("Megnyitás","Open"))},onClick={menu=false;store.open(d)})
            DropdownMenuItem(text={Text(if(d.favorite)t("Eltávolítás a kedvencekből","Remove favorite") else t("Kedvenc","Favorite"))},onClick={menu=false;store.toggleFavorite(d.id)})
            DropdownMenuItem(text={Text(t("Átnevezés","Rename"))},onClick={menu=false;rename=true})
            DropdownMenuItem(text={Text(t("Áthelyezés","Move"))},onClick={menu=false;move=true})
            DropdownMenuItem(text={Text(t("Megosztás","Share"))},onClick={menu=false;store.share(d)})
            DropdownMenuItem(text={Text(t("Törlés","Delete"))},onClick={menu=false;remove=true})
        }}
    }}
    if(ocr)OcrDialog(d,store.english,onDismiss={ocr=false},onTextChanged=store::refreshText)
    if(remind)DeadlineEditor(store,documentId=d.id,onDismiss={remind=false})
    if(rename)InputDialog(t("Átnevezés","Rename"),t("Fájlnév","File name"),onDismiss={rename=false},onSave={store.renameDoc(d.id,it);rename=false},initial=d.name)
    if(move)VaultDestinationDialog(store,onDismiss={move=false},onSelected={folder->store.moveDoc(d.id,folder);move=false})
    if(remove)AlertDialog(onDismissRequest={remove=false},title={Text(t("Dokumentum törlése?","Delete document?"))},text={Text(d.name)},confirmButton={TextButton(onClick={store.deleteDoc(d.id);remove=false}){Text(t("Törlés","Delete"))}},dismissButton={TextButton(onClick={remove=false}){Text(t("Mégse","Cancel"))}})
}
@Composable private fun EmptyCard(title:String,sub:String,action:()->Unit){Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(sub,style=MaterialTheme.typography.bodySmall);Button(onClick=action){Text("+")}}}}
@Composable internal fun MenuCard(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Surface(onClick=onClick,shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface){Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Text(label,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)}}}
@Composable private fun InputDialog(title:String,hint:String,onDismiss:()->Unit,onSave:(String)->Unit,initial:String=""){var v by rememberSaveable{mutableStateOf(initial)};AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(value=v,onValueChange={v=it},label={Text(hint)})},confirmButton={TextButton(onClick={onSave(v)},enabled=v.isNotBlank()){Text("OK")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}
@Composable private fun DraftDialog(en:Boolean,onDismiss:()->Unit,onSave:(String,String)->Unit,initial:Draft?=null){var a by rememberSaveable{mutableStateOf(initial?.title?:"")};var b by rememberSaveable{mutableStateOf(initial?.content?:"")};AlertDialog(onDismissRequest=onDismiss,title={Text(if(en)"New draft" else "Új vázlat")},text={Column{OutlinedTextField(a,{a=it},label={Text(if(en)"Title" else "Cím")});OutlinedTextField(b,{b=it},label={Text(if(en)"Text" else "Szöveg")},minLines=3)}},confirmButton={TextButton(onClick={onSave(a,b)},enabled=a.isNotBlank()){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}
