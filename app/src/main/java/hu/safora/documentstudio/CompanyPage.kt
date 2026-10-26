package hu.safora.documentstudio

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private data class CompanyService(val title:String,val subtitle:String,val description:String,val bullets:List<String>,val image:Int)
private fun companyServices(english:Boolean):List<CompanyService> = if(english)listOf(
    CompanyService("Administration & operations","Order in everyday business","From incoming documents to everyday coordination, administrative support creates a clearer overview of recurring work. We help keep information structured so your team can focus on its core activities.",listOf("Document organization and office support","Data entry and information management","Coordination of recurring administrative tasks"),R.drawable.company_operations),
    CompanyService("People & customer care","Attentive support at every touchpoint","Clear communication helps businesses maintain reliable relationships. Customer care, helpdesk and HR administration bring routine requests, employee information and follow-up tasks into a consistent workflow.",listOf("Customer care and helpdesk assistance","HR records and payroll administration support","Request tracking and follow-up coordination"),R.drawable.company_support),
    CompanyService("Billing & financial administration","A clearer view of business documents","Well-organized invoices and supporting records make financial administration easier to follow. We support the preparation, registration and coordination of billing documents and related information.",listOf("Invoice registration and document preparation","Organization of supporting financial records","Coordination of billing-related administration"),R.drawable.company_team)
)else listOf(
    CompanyService("Adminisztráció és működés","Rend a mindennapi üzleti feladatokban","A beérkező dokumentumoktól a napi egyeztetésekig az adminisztratív támogatás átláthatóbbá teszi a visszatérő feladatokat. Segítünk rendszerezni az információkat, hogy a csapat a saját szakterületére figyelhessen.",listOf("Dokumentumrendszerezés és irodai támogatás","Adatrögzítés és információkezelés","Visszatérő adminisztratív feladatok koordinálása"),R.drawable.company_operations),
    CompanyService("Emberek és ügyfélkapcsolatok","Figyelmes támogatás minden kapcsolódási ponton","Az egyértelmű kommunikáció segíti a megbízható üzleti kapcsolatokat. Az ügyfélszolgálati, helpdesk- és HR-adminisztráció egységes folyamatba rendezi a megkereséseket, munkavállalói adatokat és utánkövetési feladatokat.",listOf("Ügyfélszolgálati és helpdesk-támogatás","HR-nyilvántartások és bérszámfejtési adminisztráció támogatása","Megkeresések nyomon követése és koordinálása"),R.drawable.company_support),
    CompanyService("Számlázás és pénzügyi adminisztráció","Átlátható üzleti dokumentumok","A rendezett számlák és alátámasztó iratok megkönnyítik a pénzügyi adminisztráció követését. Támogatást nyújtunk a számlázási dokumentumok és kapcsolódó információk előkészítésében, rögzítésében és koordinálásában.",listOf("Számlák rögzítése és dokumentumok előkészítése","Kapcsolódó pénzügyi iratok rendszerezése","Számlázáshoz kapcsolódó adminisztráció koordinálása"),R.drawable.company_team)
)

@Composable internal fun CompanyHomeBanner(english:Boolean,onClick:()->Unit){
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().padding(top=10.dp),shape=RoundedCornerShape(22.dp),color=Color(0xFF302752)){
        Box(Modifier.fillMaxWidth().height(174.dp)){
            Image(painterResource(R.drawable.company_team),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF21F1938),Color(0x88272344),Color(0x22272344)))))
            Column(Modifier.align(Alignment.CenterStart).padding(20.dp).fillMaxWidth(.84f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("SAFORA HUNGARY",style=MaterialTheme.typography.labelSmall,color=Color(0xFFD4C7FF),fontWeight=FontWeight.Bold)
                Text(if(english)"About Safora" else "A Saforáról",style=MaterialTheme.typography.headlineSmall,color=Color.White,fontWeight=FontWeight.Bold)
                Text(if(english)"People, services and everyday business support" else "Emberek, szolgáltatások és üzleti támogatás",color=Color(0xFFE8E2F4),style=MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ArrowForward,if(english)"Explore company" else "Cégünk megismerése",Modifier.align(Alignment.BottomEnd).padding(20.dp),tint=Color.White)
        }
    }
}

@Composable internal fun CompanyPage(english:Boolean){
    val tr:(String,String)->String={hu,en->if(english)en else hu}
    val services=remember(english){companyServices(english)}
    val context=LocalContext.current
    var gallery by remember{mutableIntStateOf(-1)}
    fun contact(intent:Intent){runCatching{context.startActivity(intent)}.onFailure{android.widget.Toast.makeText(context,tr("Nem található megfelelő alkalmazás.","No compatible app is available."),android.widget.Toast.LENGTH_LONG).show()}}
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)){
        Box(Modifier.fillMaxWidth().height(286.dp).clip(RoundedCornerShape(24.dp))){
            Image(painterResource(R.drawable.company_team),tr("Üzleti együttműködés – illusztráció","Business collaboration — illustrative image"),Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xEC181425)))))
            SaforaLogo(Modifier.align(Alignment.TopStart).padding(16.dp).size(72.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(tr("ÜZLETI HÁTTÉRTÁMOGATÁS","BUSINESS SUPPORT"),color=Color(0xFFD4C7FF),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Text("Safora Hungary Kft.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Color.White)
                Text(tr("Több figyelem arra, ami előrevisz.","More focus on moving your business forward."),style=MaterialTheme.typography.bodyMedium,color=Color.White)
            }
        }
        Text(tr("A mindennapi működés támogatója","Supporting everyday business"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(tr("A Safora Hungary Kft. adminisztratív és operatív támogatással segíti a vállalkozások mindennapi munkáját. A dokumentumok, adatok és kapcsolódó feladatok rendszerezése megkönnyíti az együttműködést és átláthatóbbá teszi a napi folyamatokat.","Safora Hungary Kft. provides administrative and operational support for businesses. Organizing documents, information and related tasks makes collaboration easier and brings greater clarity to everyday workflows."),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr("Mivel foglalkozunk?","What we do"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        val pager=rememberPagerState(pageCount={services.size})
        HorizontalPager(pager,pageSpacing=12.dp,contentPadding=PaddingValues(end=22.dp)) { index ->
            val item=services[index]
            Surface(onClick={gallery=index},shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){
                Column{
                    Box(Modifier.fillMaxWidth().height(176.dp)){
                        Image(painterResource(item.image),item.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Surface(Modifier.align(Alignment.TopEnd).padding(10.dp),shape=RoundedCornerShape(10.dp),color=Color.Black.copy(alpha=.55f)){
                            Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.OpenInFull,null,Modifier.size(16.dp),tint=Color.White);Spacer(Modifier.width(6.dp));Text(tr("Megnyitás","Open gallery"),color=Color.White,style=MaterialTheme.typography.labelSmall)}
                        }
                    }
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(item.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text(item.subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){repeat(services.size){index->Box(Modifier.padding(4.dp).size(if(index==pager.currentPage)20.dp else 8.dp,8.dp).clip(RoundedCornerShape(8.dp)).background(if(index==pager.currentPage)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))}}
        Text(tr("A képek mesterségesen generált illusztrációk, nem a Safora tényleges irodáját vagy munkatársait ábrázolják.","Images are AI-generated illustrations, not photographs of Safora's actual office or employees."),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        services.forEachIndexed{index,service->
            var expanded by remember{mutableStateOf(index==0)}
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(Modifier.fillMaxWidth().clickable{expanded=!expanded},verticalAlignment=Alignment.CenterVertically){
                        Text("0${index+1}",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Spacer(Modifier.width(12.dp));Text(service.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded)tr("Bezárás","Collapse service") else tr("Részletek","Expand service"))
                    }
                    AnimatedVisibility(expanded){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Text(service.description,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        service.bullets.forEach{line->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Outlined.CheckCircle,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary);Text(line,style=MaterialTheme.typography.bodyMedium)}}
                        TextButton(onClick={gallery=index}){Text(tr("Megtekintés a galériában","View in gallery"));Spacer(Modifier.width(6.dp));Icon(Icons.Outlined.OpenInFull,null,Modifier.size(16.dp))}
                    }}
                }
            }
        }
        Text(tr("Hogyan indulhat az együttműködés?","How to start working together"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        listOf(tr("01 · Ismerjük meg az igényeidet","01 · Tell us what you need") to tr("Vedd fel velünk a kapcsolatot, és mutasd be a támogatást igénylő feladatokat.","Get in touch and describe the tasks where you need support."),
            tr("02 · Egyeztessük a feladatokat","02 · Define the scope together") to tr("Az együttműködés előtt egyeztetjük a feladatokat, a szükséges információkat és a kapcsolattartást.","Before starting, agree on tasks, required information and communication."),
            tr("03 · Kövessük átláthatóan a munkát","03 · Keep work easy to follow") to tr("A rendezett dokumentumok és az egyértelmű egyeztetések segítik a napi együttműködést.","Organized documents and clear communication support day-to-day collaboration.")
        ).forEach{(title,body)->Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(body,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(tr("Beszéljünk a feladataidról","Let's talk about your business"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Kossuth Lajos utca 141/A\n6050 Lajosmizse, Hungary")
            Text("+12489735340",style=MaterialTheme.typography.bodyMedium);Text("kostovad502@gmail.com",style=MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={contact(Intent(Intent.ACTION_DIAL,Uri.parse("tel:+12489735340")))},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Call,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(tr("Hívás","Call"))}
                OutlinedButton(onClick={contact(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:kostovad502@gmail.com")))},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Email,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Email")}
            }
        }}
    }
    if(gallery>=0)CompanyGallery(services,initial=gallery,english=english,onDismiss={gallery=-1})
}

@Composable private fun CompanyGallery(items:List<CompanyService>,initial:Int,english:Boolean,onDismiss:()->Unit){
    val pager=rememberPagerState(initialPage=initial,pageCount={items.size});val scope=rememberCoroutineScope()
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),shape=RoundedCornerShape(24.dp)){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Text(if(english)"Services gallery" else "Szolgáltatásgaléria",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);OutlinedIconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,if(english)"Close gallery" else "Galéria bezárása")}}
                HorizontalPager(pager,modifier=Modifier.weight(1f)){index->val item=items[index]
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
                        Image(painterResource(item.image),item.title,Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(18.dp)),contentScale=ContentScale.Crop)
                        Text(item.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                        Text(item.description,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        item.bullets.forEach{Text("• $it")}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    OutlinedIconButton(onClick={scope.launch{pager.animateScrollToPage(pager.currentPage-1)}},enabled=pager.currentPage>0){Icon(Icons.Outlined.ArrowBack,if(english)"Previous service" else "Előző szolgáltatás")}
                    Text("${pager.currentPage+1} / ${items.size}")
                    OutlinedIconButton(onClick={scope.launch{pager.animateScrollToPage(pager.currentPage+1)}},enabled=pager.currentPage<items.lastIndex){Icon(Icons.Outlined.ArrowForward,if(english)"Next service" else "Következő szolgáltatás")}
                }
            }
        }
    }
}
