package hu.safora.documentstudio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImportAndScanRegressionTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=UiDevice.getInstance(instrumentation)
    private fun image(file:File):File{val bitmap=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888);Canvas(bitmap).drawColor(Color.WHITE);file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return file}
    private fun click(label:String){val selector=By.clickable(true).enabled(true).hasDescendant(By.text(label));assertTrue("Missing button $label",device.wait(Until.hasObject(selector),10000));device.findObject(selector).click()}
    @Test fun importedFileFromFavoritesOpensTheFullVaultAndClearsSearch(){
        val name="Import regression "+UUID.randomUUID().toString().take(6)
        val file=image(File(context.filesDir,"documents").apply{mkdirs()}.resolve("import-${UUID.randomUUID()}.png"))
        val previous=JSONObject(File(context.filesDir,"safora_data.json").readText()).optBoolean("english")
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.provider",file)
        val monitor=Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply{addDataType("*/*")},Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(uri)),true)
        var id="";var monitorAdded=false
        try{
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                scenario.onActivity{activity->Store(activity).apply{language(true);assertTrue(addFolder(name))}}
                scenario.recreate();click("Favorites")
                assertTrue(device.wait(Until.hasObject(By.text("Favorites by vault")),5000))
                device.findObject(By.clazz("android.widget.EditText")).text=name
                assertTrue(device.wait(Until.hasObject(By.text(name).clazz("android.widget.TextView")),5000));device.findObject(By.text(name).clazz("android.widget.TextView")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Favorite documents")),5000))
                device.findObject(By.clazz("android.widget.EditText")).text="unrelated filter"
                instrumentation.addMonitor(monitor);monitorAdded=true
                click("Import")
                assertTrue(device.wait(Until.hasObject(By.text(file.name)),10000))
                assertTrue(device.hasObject(By.text("Documents in this vault")))
                scenario.onActivity{activity->val doc=Store(activity).docs.single{it.name==file.name};id=doc.id;assertEquals(name,doc.folder);assertFalse(doc.favorite);assertTrue(File(doc.path).exists())}
                device.takeScreenshot(File(context.getExternalFilesDir(null),"import-from-favorites-fixed.png"))
            }
        }finally{
            if(monitorAdded)instrumentation.removeMonitor(monitor)
            ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->Store(activity).apply{if(id.isEmpty())id=docs.find{it.name==file.name}?.id?:"";if(id.isNotEmpty())deleteDoc(id);deleteVault(name)
                folders.filter{it.startsWith("Import regression ")&&docs.none{doc->doc.folder==it}}.forEach{deleteVault(it)}
                File(activity.filesDir,"documents").listFiles()?.filter{it.name.matches(Regex("import-[a-f0-9-]+\\.png"))&&docs.none{doc->doc.path==it.path}}?.forEach{it.delete()}
                language(previous)}}}
            file.delete()
        }
    }
    @Test fun lockedDefaultDraftDoesNotBlockScanningIntoAnotherVault(){
        val name="Scan regression "+UUID.randomUUID().toString().take(6)
        val title="Other-vault-scan-"+UUID.randomUUID().toString().take(6)
        val password="temporary-test-password"
        val record=VaultPasswords.create(password.toCharArray())
        var changedDefault=false;var createdDefaultDraft=false;var id="";var key=""
        try{
            ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
                val store=Store(activity);assertTrue(store.addFolder(name));key=store.scanSessionKey(name)
                val defaults=ScanFiles(activity,store.scanSessionKey("Unsorted"))
                if(defaults.pages().isEmpty()){defaults.setOwner("Unsorted");defaults.save(listOf(image(defaults.newFile())));createdDefaultDraft=true}
                if(!store.hasVaultPassword("Unsorted")){store.setVaultPassword("Unsorted",record);changedDefault=true}
                VaultSessions.lockAll();assertFalse(store.canEnterVault("Unsorted"));assertTrue(store.canEnterVault(name))
                val other=ScanFiles(activity,key);other.setOwner(name);other.save(listOf(image(other.newFile())))
            }}
            ActivityScenario.launch<ScannerActivity>(Intent(context,ScannerActivity::class.java).putExtra("folder",name).putExtra("english",true)).use{
                assertTrue(device.wait(Until.hasObject(By.text("Crop")),15000))
                assertFalse(device.hasObject(By.text("Unlock the vault to continue")))
                device.findObject(By.clazz("android.widget.EditText")).text=title
                click("Save PDF & recognize text")
                val deadline=System.currentTimeMillis()+15000
                while(System.currentTimeMillis()<deadline){
                    val docs=JSONObject(File(context.filesDir,"safora_data.json").readText()).getJSONArray("docs")
                    val doc=(0 until docs.length()).map{docs.getJSONObject(it)}.find{it.getString("name")=="$title.pdf"}
                    if(doc!=null){id=doc.getString("id");assertEquals(name,doc.getString("folder"));break};Thread.sleep(100)
                }
                assertTrue("Scan was not exported to the chosen vault",id.isNotEmpty())
            }
            ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->val store=Store(activity);assertFalse(store.canEnterVault("Unsorted"));assertTrue(ScanFiles(activity,store.scanSessionKey("Unsorted")).pages().isNotEmpty())}}
        }finally{
            ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->Store(activity).apply{
                if(id.isNotEmpty())deleteDoc(id)
                if(key.isNotEmpty())ScanFiles(activity,key).clear()
                deleteVault(name)
                if(createdDefaultDraft)ScanFiles(activity,scanSessionKey("Unsorted")).clear()
                if(changedDefault){assertTrue(unlockVault("Unsorted",password.toCharArray()));setVaultPassword("Unsorted",null)}
            }}}
        }
    }
    @Test fun legacyDraftMigrationAndVaultRenameKeepSessionOwnership(){
        val dir=File(context.cacheDir,UUID.randomUUID().toString()).apply{mkdirs()}
        try{instrumentation.runOnMainSync{
            val isolated=object:Activity(){override fun getFilesDir()=dir}
            val legacy=File(dir,"scan-session").apply{mkdirs()};File(legacy,"vault.txt").writeText("Protected")
            image(File(legacy,"legacy.jpg"));File(legacy,"pages.json").writeText("[\"legacy.jpg\"]")
            assertTrue(ScanFiles(isolated,"Other").pages().isEmpty())
            assertTrue(File(legacy,"legacy.jpg").exists())
            assertEquals(1,ScanFiles(isolated,"Protected").pages().size)
            assertFalse(legacy.exists())
            val store=Store(isolated);assertTrue(store.addFolder("Original"));val key=store.scanSessionKey("Original")
            val session=ScanFiles(isolated,key);session.setOwner("Original");session.save(listOf(image(session.newFile())))
            assertTrue(store.renameVault("Original","Renamed"))
            assertEquals(key,Store(isolated).scanSessionKey("Renamed"))
            assertEquals(1,ScanFiles(isolated,Store(isolated).scanSessionKey("Renamed")).pages().size)
        }}finally{dir.deleteRecursively()}
    }
}
