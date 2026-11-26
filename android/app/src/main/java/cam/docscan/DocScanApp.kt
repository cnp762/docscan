package cam.docscan

import android.app.Application
import cam.docscan.engine.DocScanRuntime

class DocScanApp : Application() {
    lateinit var runtime: DocScanRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = DocScanRuntime(this)
    }

    override fun onTerminate() {
        if (::runtime.isInitialized) runtime.close()
        super.onTerminate()
    }
}
