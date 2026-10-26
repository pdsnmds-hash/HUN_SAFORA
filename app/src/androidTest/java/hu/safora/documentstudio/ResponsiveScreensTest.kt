package hu.safora.documentstudio

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

class ResponsiveScreensTest{
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val device get()=UiDevice.getInstance(instrumentation)
    private var width=411
    private var english=false
    private val lines=mutableListOf<String>()
    private fun tr(hu:String,en:String)=if(english)en else hu
    private fun click(text:String)=compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun check(screen:String){
        compose.waitForIdle()
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).fetchSemanticsNodes()
        val failed=mutableListOf<String>();var checked=0
        compose.runOnIdle{
            nodes.filter{it.boundsInRoot.width>0&&it.boundsInRoot.height>0}.forEach{node->
                val layouts=mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                layouts.forEach{layout->
                    checked++
                    // File/user-supplied names deliberately use ellipsis; other UI text must fit.
                    val widest=(0 until layout.lineCount).maxOfOrNull{layout.getLineRight(it)-layout.getLineLeft(it)}?:0f
                    val bottom=if(layout.lineCount>0)layout.getLineBottom(layout.lineCount-1)else 0f
                    // Compose can retain a wider cached Paragraph than the wrap-content Text size.
                    // Compare actual line extents instead of that cached paragraph width.
                    val clipped=widest>layout.size.width+1.5f||bottom>layout.size.height+1.5f||layout.multiParagraph.didExceedMaxLines
                    if(clipped&&layout.layoutInput.overflow!=TextOverflow.Ellipsis)failed+=(layout.layoutInput.text.text+" [size=${layout.size}; line=$widest x $bottom]")
                }
            }
        }
        lines+="$width ${if(english)"EN" else "HU"} $screen: $checked text layouts; overflow=${failed.size}"
        File(context.getExternalFilesDir(null),"responsive-report.txt").writeText(lines.joinToString("\n"))
        device.takeScreenshot(File(context.getExternalFilesDir(null),"responsive-$width-${if(english)"en" else "hu"}-$screen.png"))
        assertTrue("Overflow at $width / $screen: ${failed.joinToString()}",failed.isEmpty())
    }
    @Test fun allScreensFit411380320Dp(){
        val initialSize=device.executeShellCommand("wm size").lineSequence().find{it.startsWith("Override size:")}?.substringAfter(": ")
        val state=JSONObject(File(context.filesDir,"safora_data.json").readText())
        val originalLanguage=state.optBoolean("english");val originalDark=state.optBoolean("dark",true)
        val vault="Layout QA "+UUID.randomUUID().toString().take(6)
        var key=""
        try{
            compose.runOnIdle{
                val store=Store(compose.activity);store.theme(true);assertTrue(store.addFolder(vault));key=store.scanSessionKey(vault)
                val files=ScanFiles(compose.activity,key);files.setOwner(vault)
                val bitmap=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888);Canvas(bitmap).drawColor(Color.WHITE)
                val file=files.newFile();ScanImages.write(bitmap,file);bitmap.recycle();files.save(listOf(file))
            }
            for(w in (InstrumentationRegistry.getArguments().getString("widths")?.split(",")?.map{it.toInt()} ?: listOf(411,380,320))){
                width=w
                val density=compose.activity.resources.displayMetrics.density
                val height=if(w==320)710 else 852
                device.executeShellCommand("wm size ${(w*density).roundToInt()}x${(height*density).roundToInt()}")
                compose.waitUntil(15000){runCatching{kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp-w)<=1}.getOrDefault(false)}
                for(en in (InstrumentationRegistry.getArguments().getString("languages")?.split(",")?.map{it=="en"} ?: listOf(false,true))){
                    english=en
                    compose.runOnIdle{Store(compose.activity).language(en)}
                    compose.activityRule.scenario.recreate()
                    compose.waitForIdle()
                    check("home")
                    compose.onNodeWithContentDescription(tr("Beállítások","Settings")).performScrollTo().performClick()
                    check("settings")
                    click(tr("Alkalmazásbemutató","App guide"))
                    for(page in 1..4){
                        compose.onNodeWithText("$page / 4").assertExists()
                        check("intro$page")
                        click(if(page==4)tr("Kezdjük!","Get started")else tr("Következő","Next"))
                    }
                    click(tr("Iratok","Documents"));check("vaults")
                    compose.onNode(hasSetTextAction()).performTextReplacement(vault)
                    compose.onNode(hasText(vault) and hasClickAction() and !hasSetTextAction()).performClick()
                    check("vault")
                    click(tr("Határidők","Deadlines"));check("deadlines")
                    click(tr(" Határidő hozzáadása"," Add deadline"));check("deadline-editor")
                    click(tr("Mégse","Cancel"))
                    click(tr("Főoldal","Home"))
                    compose.onNode(hasText(tr("A Saforáról","About Safora")) and hasClickAction()).performScrollTo().performClick()
                    compose.onNodeWithText("Safora Hungary Kft.").performScrollTo()
                    check("company")
                    compose.onAllNodes(hasText(tr("Megnyitás","Open gallery")) and hasClickAction()).onFirst().performScrollTo().performClick()
                    check("gallery")
                    compose.onNodeWithContentDescription(tr("Galéria bezárása","Close gallery")).performClick()
                    click(tr("Főoldal","Home"))
                    val monitor=instrumentation.addMonitor(ScannerActivity::class.java.name,null,false)
                    try{
                        compose.runOnIdle{compose.activity.startActivity(Intent(compose.activity,ScannerActivity::class.java).putExtra("folder",vault).putExtra("english",en))}
                        val scanner=instrumentation.waitForMonitorWithTimeout(monitor,15000) as ScannerActivity
                        check("scanner")
                        click(tr("Kivágás","Crop"));check("crop")
                        compose.runOnIdle{scanner.finish()}
                        compose.waitForIdle()
                    }finally{instrumentation.removeMonitor(monitor)}
                }
            }
        }finally{
            device.executeShellCommand(if(initialSize==null)"wm size reset" else "wm size $initialSize")
            compose.waitForIdle()
            compose.runOnIdle{Store(compose.activity).apply{if(key.isNotEmpty())ScanFiles(compose.activity,key).clear();deleteVault(vault);language(originalLanguage);theme(originalDark)}}
            File(context.getExternalFilesDir(null),"responsive-report.txt").writeText(lines.joinToString("\n"))
        }
    }
}
