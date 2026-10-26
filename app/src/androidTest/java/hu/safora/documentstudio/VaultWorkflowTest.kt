package hu.safora.documentstudio

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
class VaultWorkflowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=UiDevice.getInstance(instrumentation)
    private fun find(label:String){repeat(6){if(device.hasObject(By.text(label)))return;device.swipe(device.displayWidth/2,device.displayHeight*3/4,device.displayWidth/2,device.displayHeight/3,20)};assertTrue("Missing $label",device.hasObject(By.text(label)))}
    private fun click(label:String){val selector=By.clickable(true).enabled(true).hasDescendant(By.text(label));assertTrue(device.wait(Until.hasObject(selector),10000));device.findObject(selector).click()}
    @Test fun vaultLockPersistsAndManagementPreservesFiles(){
        val suffix=UUID.randomUUID().toString().take(6);val original="Private $suffix";val renamed="Renamed $suffix"
        val file=File(context.cacheDir,"test-vault-$suffix.pdf")
        val pdf=PdfDocument();val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,1).create());page.canvas.drawText("VAULT PRIVATE FILE",40f,100f,Paint().apply{textSize=24f});pdf.finishPage(page);file.outputStream().use{pdf.writeTo(it)};pdf.close()
        var id="";var path="";var current=original
        val previous=runCatching{JSONObject(File(context.filesDir,"safora_data.json").readText()).optBoolean("english")}.getOrDefault(false)
        val password="vault-pass-123";val record=VaultPasswords.create(password.toCharArray())
        try{
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                scenario.onActivity{activity->val store=Store(activity);store.language(true);assertTrue(store.addFolder(original));store.addScannedPdf(file,"Private acceptance file",original);val doc=store.docs.last();id=doc.id;path=doc.path;store.setVaultPassword(original,record);assertFalse(store.canAccess(doc));assertFalse(Store(activity).canAccess(doc))}
                scenario.recreate()
                assertFalse(device.hasObject(By.text("Private acceptance file.pdf")))
                click("Documents");find(original)
                assertFalse(device.hasObject(By.text("Private acceptance file.pdf")))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"vaults-overview.png"))
                device.findObject(By.text(original)).click()
                assertTrue(device.wait(Until.hasObject(By.text("Unlock vault")),5000))
                device.findObject(By.clazz("android.widget.EditText")).text="wrong-password";click("Unlock")
                assertTrue(device.wait(Until.hasObject(By.text("Incorrect password.")),10000))
                device.findObject(By.clazz("android.widget.EditText")).text=password;click("Unlock")
                assertTrue(device.wait(Until.hasObject(By.text("Private acceptance file.pdf")),10000))
                scenario.onActivity{VaultSessions.lockAll()}
                assertTrue(device.wait(Until.gone(By.text("Private acceptance file.pdf")),5000))
                assertTrue(device.wait(Until.hasObject(By.text("This vault is locked")),5000))
                // A direct activity launch (the notification path) must enforce the same password.
                scenario.onActivity{activity->activity.startActivity(Intent(activity,PreviewActivity::class.java).putExtra("path",path).putExtra("title","Private acceptance file.pdf").putExtra("type","application/pdf"))}
                assertTrue(device.wait(Until.hasObject(By.text("Unlock the vault to continue")),10000))
                assertFalse(device.hasObject(By.text("Private acceptance file.pdf")))
                assertFalse(device.hasObject(By.text("1 / 1")))
                device.pressBack()
                assertTrue(device.wait(Until.hasObject(By.text("This vault is locked")),10000))
                scenario.onActivity{activity->val store=Store(activity);assertTrue(store.unlockVault(original,password.toCharArray()));assertTrue(store.renameVault(original,renamed));current=renamed;assertEquals(renamed,store.docs.single{it.id==id}.folder);assertTrue(Store(activity).hasVaultPassword(renamed))}
                scenario.recreate()
                scenario.onActivity{activity->val store=Store(activity);assertTrue(store.canEnterVault(renamed));store.setVaultPassword(renamed,null)
                    if(store.canEnterVault("Unsorted")){assertTrue(store.deleteVault(renamed));assertEquals("Unsorted",store.docs.single{it.id==id}.folder);assertTrue(File(path).exists())}
                    else{assertFalse(store.deleteVault(renamed));assertEquals(renamed,store.docs.single{it.id==id}.folder);assertTrue(File(path).exists());store.deleteDoc(id);id="";assertTrue(store.deleteVault(renamed))}}
            }
        }finally{
            ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->val store=Store(activity);if(store.hasVaultPassword(current))store.unlockVault(current,password.toCharArray());if(id.isNotEmpty()&&store.docs.find{it.id==id}?.let{store.canAccess(it)}==true)store.deleteDoc(id);if(current in store.vaultNames&&current!="Unsorted")store.deleteVault(current);store.language(previous)}}
            file.delete()
        }
    }
    @Test fun companyBannerServicesAndFullscreenCarousel(){
        val previous=runCatching{JSONObject(File(context.filesDir,"safora_data.json").readText()).optBoolean("english")}.getOrDefault(false)
        try{ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{Store(it).language(true)};scenario.recreate()
            find("About Safora");device.takeScreenshot(File(context.getExternalFilesDir(null),"home-company-banner.png"));device.findObject(By.text("About Safora")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Safora Hungary Kft.")),10000))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"company-new.png"))
            find("Open gallery");click("Open gallery")
            assertTrue(device.wait(Until.hasObject(By.text("Services gallery")),5000))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"company-gallery.png"))
            device.findObject(By.desc("Next service")).click();assertTrue(device.wait(Until.hasObject(By.text("2 / 3")),5000))
            device.findObject(By.desc("Next service")).click();assertTrue(device.wait(Until.hasObject(By.text("3 / 3")),5000))
            device.findObject(By.desc("Close gallery")).click();assertTrue(device.wait(Until.gone(By.text("Services gallery")),5000))
        }}finally{ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{Store(it).language(previous)}}}
    }
}
