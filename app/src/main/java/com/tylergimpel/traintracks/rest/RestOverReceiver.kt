package com.tylergimpel.traintracks.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * What the alarm wakes: the moment a rest is over, said out loud.
 *
 * A receiver rather than anything longer-lived, because there is nothing to keep alive. The app
 * may have been killed an hour ago; the alarm still fires, this runs for a few milliseconds, and
 * the lifter hears it.
 */
class RestOverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestNotifications(context).restIsOver()
    }
}
