package com.lecture.screenshot;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

// Accessibility service required for overlay permission flow
public class FloatingButtonService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used — only needed for permission grant
    }

    @Override
    public void onInterrupt() {}
}
