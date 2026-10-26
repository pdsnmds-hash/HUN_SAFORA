package hu.safora.documentstudio

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HomeLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    @Test fun defaultPreferencesAndExplicitSelectionsArePreserved() {
        val dir=File(context.cacheDir,UUID.randomUUID().toString()).apply{mkdirs()}
        try {
            instrumentation.runOnMainSync {
                val isolated=object:Activity(){override fun getFilesDir()=dir}
                val fresh=Store(isolated)
                assertTrue(fresh.dark);assertFalse(fresh.english)
                fresh.theme(false);fresh.language(true)
                val restored=Store(isolated)
                assertFalse(restored.dark);assertTrue(restored.english)
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun scannerOutlinedControlsPreserveCurrentSession() {
        val session=ScanFiles(context)
        val existing=session.pages()
        var created=false
        try {
            if(existing.isEmpty()) {
                val bitmap=android.graphics.Bitmap.createBitmap(600,800,android.graphics.Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                val image=session.newFile();ScanImages.write(bitmap,image);bitmap.recycle();session.save(listOf(image));created=true
            }
            ActivityScenario.launch<ScannerActivity>(android.content.Intent(context,ScannerActivity::class.java).putExtra("english",false).putExtra("dark",true)).use {
                assertTrue(device.wait(Until.hasObject(By.clickable(true).enabled(true).hasDescendant(By.text("Kivágás"))),10000))
                assertTrue(device.hasObject(By.text("Forgatás")))
                assertTrue(device.hasObject(By.text("Törlés")))
                assertTrue(device.hasObject(By.desc("Előző helyre")))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"scanner-outlined.png"))
            }
            if(!created)assertEquals(existing,session.pages())
        } finally {if(created)session.clear()}
    }
    @Test fun homeShortcutsLargeSettingsPopupAndReturnNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Apply the requested default presentation to the preview emulator.
            scenario.onActivity { activity->Store(activity).apply{theme(true);language(false)} }
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.text("Dokumentumaim")),10000))
            assertTrue(device.wait(Until.hasObject(By.text("Kedvencek")),5000))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"home-compact.png"))
            device.findObject(By.text("Kedvencek")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Kedvencek tárhelyenként")),5000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Dokumentumaim")),5000))
            device.findObject(By.desc("Beállítások")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Beállítások bezárása")),5000))
            assertTrue(device.hasObject(By.text("Magyar")))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"settings-popup.png"))
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"settings-popup.xml"))
            device.findObject(By.desc("Beállítások bezárása")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Dokumentumaim")),5000))
            var company=device.findObject(By.text("A Saforáról"))
            if(company==null){device.swipe(device.displayWidth/2,device.displayHeight*3/4,device.displayWidth/2,device.displayHeight/3,20);company=device.wait(Until.findObject(By.text("A Saforáról")),5000)}
            assertNotNull(company);company.click()
            repeat(3){if(!device.hasObject(By.text("Mivel foglalkozunk?")))device.swipe(device.displayWidth/2,device.displayHeight*3/4,device.displayWidth/2,device.displayHeight/3,20)}
            assertTrue(device.hasObject(By.text("Mivel foglalkozunk?")))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"company.png"))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("Dokumentumaim")),5000))
        }
    }
}
