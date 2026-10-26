package hu.safora.documentstudio

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.splashscreen.SplashScreen

@Composable internal fun SaforaLogo(modifier:Modifier=Modifier){
    Image(painterResource(R.drawable.safora_mark),contentDescription="Safora logo",modifier=modifier)
}

/** Generated artwork is part of the native splash exit, not a second splash activity or loading delay. */
internal fun Activity.configureBrandSplash(splash:SplashScreen){
    splash.setOnExitAnimationListener { provider ->
        val host=window.decorView as ViewGroup
        fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
        val overlay=FrameLayout(this).apply{
            id=R.id.safora_splash_overlay
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setBackgroundColor(AndroidColor.rgb(23,23,30))
        }
        overlay.addView(ImageView(this).apply{setImageResource(R.drawable.safora_splash);scaleType=ImageView.ScaleType.CENTER_CROP},FrameLayout.LayoutParams(-1,-1))
        overlay.addView(ImageView(this).apply{setImageResource(R.drawable.safora_mark);scaleType=ImageView.ScaleType.FIT_CENTER},FrameLayout.LayoutParams(dp(196),dp(196),Gravity.CENTER))
        val wordmark=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;translationY=dp(144).toFloat()}
        wordmark.addView(TextView(this).apply{text="SAFORA";textSize=30f;letterSpacing=.22f;setTextColor(AndroidColor.WHITE);typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);gravity=Gravity.CENTER})
        wordmark.addView(TextView(this).apply{text="DOCUMENT STUDIO";textSize=11f;letterSpacing=.24f;setTextColor(AndroidColor.rgb(199,188,226));gravity=Gravity.CENTER;setPadding(0,dp(8),0,0)})
        overlay.addView(wordmark,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        host.addView(overlay,ViewGroup.LayoutParams(-1,-1))
        provider.remove()
        overlay.animate().alpha(0f).setStartDelay(100).setDuration(420).withEndAction{host.removeView(overlay)}.start()
    }
}
