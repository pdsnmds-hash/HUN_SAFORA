package hu.safora.documentstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
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

@RunWith(AndroidJUnit4::class)
class OnboardingBrandingTest{
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=UiDevice.getInstance(instrumentation)
    private fun click(text:String){val selector=By.clickable(true).enabled(true).hasDescendant(By.text(text));assertTrue("Missing $text",device.wait(Until.hasObject(selector),10000));device.findObject(selector).click()}
    @Test fun firstRunGuideSupportsLanguageRotationCompletionAndReplay(){
        val previousEnglish=runCatching{JSONObject(File(context.filesDir,"safora_data.json").readText()).optBoolean("english")}.getOrDefault(false)
        context.getSharedPreferences(OnboardingPreferences.FILE,Context.MODE_PRIVATE).edit().remove("completed_version").commit()
        try{
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                // Capture the native splash exit artwork without delaying the app to obtain a screenshot.
                val until=System.currentTimeMillis()+2500
                while(System.currentTimeMillis()<until){
                    var visible=false
                    scenario.onActivity{visible=it.window.decorView.findViewById<android.view.View>(R.id.safora_splash_overlay)!=null}
                    if(visible){device.takeScreenshot(File(context.getExternalFilesDir(null),"splash-launch.png"));break}
                    Thread.sleep(20)
                }
                scenario.onActivity{Store(it).language(false)};scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text("Üdv a Saforában")),10000))
                assertTrue(device.hasObject(By.desc("Safora logo")))
                assertFalse(OnboardingPreferences.completed(context))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"onboarding-welcome.png"))
                click("Következő");assertTrue(device.wait(Until.hasObject(By.text("2 / 4")),5000))
                scenario.recreate();assertTrue(device.wait(Until.hasObject(By.text("2 / 4")),5000))
                click("EN");assertTrue(device.wait(Until.hasObject(By.text("A place for every document")),5000))
                click("HU");assertTrue(device.wait(Until.hasObject(By.text("Minden dokumentumnak saját hely")),5000))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"onboarding-vaults.png"))
                click("Következő");assertTrue(device.wait(Until.hasObject(By.text("3 / 4")),5000))
                click("Következő");assertTrue(device.wait(Until.hasObject(By.text("4 / 4")),5000))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"onboarding-reminders.png"))
                click("Kezdjük!");assertTrue(device.wait(Until.hasObject(By.text("Dokumentumaim")),10000))
                assertTrue(OnboardingPreferences.completed(context))
            }
            ActivityScenario.launch(MainActivity::class.java).use{
                assertTrue(device.wait(Until.hasObject(By.text("Dokumentumaim")),10000))
                assertFalse(device.hasObject(By.text("Üdv a Saforában")))
                assertTrue(device.hasObject(By.desc("Safora logo")))
                device.takeScreenshot(File(context.getExternalFilesDir(null),"home-branded.png"))
                device.findObject(By.desc("Beállítások")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Alkalmazásbemutató")),5000))
                click("Alkalmazásbemutató")
                assertTrue(device.wait(Until.hasObject(By.text("Üdv a Saforában")),5000))
                click("Kihagyás");assertTrue(device.wait(Until.hasObject(By.text("Dokumentumaim")),5000))
                assertTrue(OnboardingPreferences.completed(context))
            }
        }finally{
            OnboardingPreferences.complete(context)
            ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{Store(it).language(previousEnglish)}}
        }
    }
    @Test fun installedLauncherIconUsesGeneratedTransparentMark(){
        val mark=BitmapFactory.decodeResource(context.resources,R.drawable.safora_mark)
        assertTrue(mark.hasAlpha());assertEquals(0,Color.alpha(mark.getPixel(0,0)));assertEquals(0,Color.alpha(mark.getPixel(mark.width-1,mark.height-1)))
        val icon=context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        val bitmap=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,432,432);icon.draw(Canvas(bitmap))
        var lavender=0
        for(y in 100 until 332)for(x in 100 until 332){val pixel=bitmap.getPixel(x,y);if(Color.blue(pixel)>100&&Color.red(pixel)>80)lavender++}
        assertTrue("Generated logo is missing from launcher icon",lavender>3000)
        File(context.getExternalFilesDir(null),"launcher-icon.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle();mark.recycle()
    }
}
