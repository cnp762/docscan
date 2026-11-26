package cam.docscan.engine.runtime

enum class WorkPriority(val band: Int) {
    FreshScan(100),
    UserSave(80),
    Suggest(40),
    Deferred(10),
}
