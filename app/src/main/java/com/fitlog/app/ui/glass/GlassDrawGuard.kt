package com.fitlog.app.ui.glass

/** Optional graphics may fail on a device; application content is drawn outside this guard. */
internal class GlassDrawGuard {
    var failed: Boolean = false
        private set

    fun render(effect: () -> Unit, fallback: () -> Unit, report: (Throwable) -> Unit) {
        if (failed) {
            fallback()
            return
        }
        try {
            effect()
        } catch (error: RuntimeException) {
            failed = true
            report(error)
            fallback()
        } catch (error: LinkageError) {
            // Missing/incompatible platform graphics APIs must not prevent startup.
            failed = true
            report(error)
            fallback()
        }
    }
}
