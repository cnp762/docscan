package cam.docscan

import android.app.Application
import cam.docscan.data.local.DocScanDatabase
import cam.docscan.data.local.RoomPageRepository
import cam.docscan.engine.DocScanRuntime

class DocScanApp : Application() {
    lateinit var runtime: DocScanRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        val dao = DocScanDatabase.getInstance(this).docScanDao()
        runtime = DocScanRuntime(
            context = this,
            repo = RoomPageRepository(dao),
        )
    }

    override fun onTerminate() {
        if (::runtime.isInitialized) runtime.close()
        super.onTerminate()
    }
}
