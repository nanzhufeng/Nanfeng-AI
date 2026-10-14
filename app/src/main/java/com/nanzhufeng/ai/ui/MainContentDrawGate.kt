package com.nanzhufeng.ai.ui

import android.view.View
import android.view.ViewTreeObserver

/** The first application frame is real content; no intermediate Activity loading view. */
internal class MainContentDrawGate(private val root: View) : ViewTreeObserver.OnPreDrawListener {
    private var ready = false

    init { root.viewTreeObserver.addOnPreDrawListener(this) }

    override fun onPreDraw(): Boolean {
        if (ready) dispose()
        return ready
    }

    fun release() {
        ready = true
        root.postInvalidateOnAnimation()
    }

    fun dispose() {
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(this)
    }
}
