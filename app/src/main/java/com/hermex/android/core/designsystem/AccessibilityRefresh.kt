package com.hermex.android.core.designsystem

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

// Compose sends no content-change events when accessibility is on with no service enabled (UiAutomation
// tools), so their node cache serves a stale tree; TalkBack is an enabled service and never takes this path.
@Composable
fun AutomationAccessibilityRefresh() {
    val view = LocalView.current
    DisposableEffect(view) {
        val root = view.rootView
        val manager = view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        var automationOnly = false
        fun evaluate() {
            automationOnly = manager.isEnabled && manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).isEmpty()
        }
        evaluate()
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { evaluate() }
        manager.addAccessibilityStateChangeListener(stateListener)
        val drawListener = ViewTreeObserver.OnDrawListener {
            if (automationOnly) {
                root.post { root.parent?.notifySubtreeAccessibilityStateChanged(root, root, AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) }
            }
        }
        root.viewTreeObserver.addOnDrawListener(drawListener)
        onDispose {
            root.viewTreeObserver.removeOnDrawListener(drawListener)
            manager.removeAccessibilityStateChangeListener(stateListener)
        }
    }
}
