package roy.ij.obscure

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

class App : Application() {

    companion object {
        val isForeground = AtomicBoolean(false) // tracks if app is visible
        lateinit var instance: App
            private set

        val context: Context
            get() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isForeground.set(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                isForeground.set(false)
            }
        })
    }
}