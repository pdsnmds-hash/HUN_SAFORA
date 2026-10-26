package hu.safora.documentstudio

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

object OnboardingPreferences {
    const val FILE="safora_intro"
    private const val VERSION=1
    fun completed(context:Context)=context.getSharedPreferences(FILE,Context.MODE_PRIVATE).getInt("completed_version",0)>=VERSION
    fun complete(context:Context){context.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().putInt("completed_version",VERSION).apply()}
}

private data class IntroPage(val label:String,val title:String,val body:String,val benefits:List<String>,val icon:ImageVector)
private fun introPages(english:Boolean)=if(english)listOf(
    IntroPage("WELCOME","Welcome to Safora","A calmer way to keep your important documents organized and close at hand.",listOf("Documents, scans and reminders in one app","No account required"),Icons.Outlined.Description),
    IntroPage("01 · ORGANIZE","A place for every document","Create vaults for your work, import PDFs and images, and open a vault to see its files.",listOf("Rename vaults and move your files","Add an app access password when you need it"),Icons.Outlined.FolderOpen),
    IntroPage("02 · CAPTURE & FIND","From paper to searchable PDF","Scan multiple pages, crop and rotate them, then save a PDF. Offline OCR helps you find text inside your documents.",listOf("Camera access is requested when you start scanning","Latin text recognition for HU/EN"),Icons.Outlined.DocumentScanner),
    IntroPage("03 · REMEMBER","Keep important dates in view","Link reminders to documents and choose advance notices or repeating dates.",listOf("Notification access is requested when you create a reminder","Battery saving may delay delivery"),Icons.Outlined.NotificationsActive)
)else listOf(
    IntroPage("ÜDVÖZLÜNK","Üdv a Saforában","Átláthatóbb mindennapok: fontos dokumentumaid rendezve, mindig kéznél.",listOf("Dokumentumok, szkennelés és emlékeztetők egy helyen","Regisztráció nélkül használható"),Icons.Outlined.Description),
    IntroPage("01 · RENDSZEREZÉS","Minden dokumentumnak saját hely","Hozz létre tárhelyeket, importálj PDF-eket és képeket. A fájlok a kiválasztott tárhely megnyitása után láthatók.",listOf("Tárhelyek átnevezése és fájlok áthelyezése","Igény szerint jelszavas hozzáférés az alkalmazásban"),Icons.Outlined.FolderOpen),
    IntroPage("02 · SZKENNELÉS ÉS KERESÉS","Papírból kereshető PDF","Szkennelj több oldalt, vágd körbe és forgasd el őket, majd mentsd PDF-ként. Az offline OCR segít a dokumentumok szövegében keresni.",listOf("Kameraengedélyt a szkennelés indításakor kérünk","Latin betűs szövegfelismerés HU/EN nyelven"),Icons.Outlined.DocumentScanner),
    IntroPage("03 · EMLÉKEZTETŐK","A fontos dátumok szem előtt","Kapcsolj emlékeztetőt a dokumentumaidhoz, és állíts be előzetes értesítést vagy ismétlődő időpontot.",listOf("Értesítési engedélyt emlékeztető létrehozásakor kérünk","Az energiatakarékosság késleltetheti az értesítést"),Icons.Outlined.NotificationsActive)
)

@Composable internal fun OnboardingScreen(english:Boolean,onLanguage:(Boolean)->Unit,onFinish:()->Unit){
    val pages=remember(english){introPages(english)}
    val pager=rememberPagerState(pageCount={pages.size})
    val scope=rememberCoroutineScope()
    BackHandler(enabled=pager.currentPage>0){scope.launch{pager.animateScrollToPage(pager.currentPage-1)}}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=screenGutter()+4.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                SaforaLogo(Modifier.size(42.dp))
                Text("SAFORA",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
                TextButton(onClick={onLanguage(!english)}){Text(if(english)"HU" else "EN")}
                TextButton(onClick=onFinish){Text(if(english)"Skip" else "Kihagyás")}
            }
            HorizontalPager(pager,modifier=Modifier.weight(1f),pageSpacing=24.dp){index->
                val page=pages[index]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                    IntroIllustration(index,page.icon)
                    Spacer(Modifier.height(20.dp))
                    Text(page.label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(page.title,style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Text(page.body,style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(22.dp))
                    page.benefits.forEach{benefit->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top){
                        Icon(Icons.Outlined.CheckCircle,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                        Text(benefit,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                repeat(pages.size){index->Box(Modifier.padding(4.dp).size(if(index==pager.currentPage)24.dp else 7.dp,7.dp).clip(CircleShape).background(if(index==pager.currentPage)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))}
                Spacer(Modifier.width(10.dp));Text("${pager.currentPage+1} / ${pages.size}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                if(pager.currentPage>0)OutlinedIconButton(onClick={scope.launch{pager.animateScrollToPage(pager.currentPage-1)}},modifier=Modifier.size(52.dp),shape=RoundedCornerShape(14.dp)){Icon(Icons.Outlined.ArrowBack,if(english)"Previous" else "Előző")}
                Button(onClick={if(pager.currentPage==pages.lastIndex)onFinish()else scope.launch{pager.animateScrollToPage(pager.currentPage+1)}},modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(14.dp)){
                    Text(if(pager.currentPage==pages.lastIndex)if(english)"Get started" else "Kezdjük!" else if(english)"Next" else "Következő")
                    Spacer(Modifier.width(8.dp));Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable private fun IntroIllustration(page:Int,icon:ImageVector){
    Box(Modifier.fillMaxWidth().height(if(windowWidthDp()<360)148.dp else 198.dp),contentAlignment=Alignment.Center){
        Box(Modifier.size(184.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=.06f)))
        Box(Modifier.size(142.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=.07f)))
        if(page==0)SaforaLogo(Modifier.size(258.dp))else{
            Surface(modifier=Modifier.size(136.dp,158.dp),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.35f)),shadowElevation=6.dp){
                Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(icon,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=.25f)))
                    Box(Modifier.fillMaxWidth(.7f).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
                }
            }
            Surface(Modifier.align(Alignment.Center).offset(x=64.dp,y=52.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer,border=BorderStroke(2.dp,MaterialTheme.colorScheme.background)){
                Icon(when(page){1->Icons.Outlined.Lock;2->Icons.Outlined.Search;else->Icons.Outlined.Done},null,Modifier.padding(12.dp).size(26.dp),tint=MaterialTheme.colorScheme.primary)
            }
        }
    }
}
