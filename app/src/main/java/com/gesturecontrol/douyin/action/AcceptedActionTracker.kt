package com.gesturecontrol.douyin.action

internal class AcceptedActionTracker(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    var lastAction: DouyinAction? = null
        private set

    @Volatile
    var lastActionTime: Long = 0L
        private set

    fun record(action: DouyinAction, accepted: Boolean): Boolean {
        if (!accepted) return false
        lastAction = action
        lastActionTime = clock()
        return true
    }
}
