package app.gogamid.dmcoupons;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Activates available coupons in the official Mein dm app by pressing their
 * Aktivieren button. It never presses Deaktivieren and does not touch the app
 * itself. A notification is posted only when Mein dm shows its own success
 * message for an activation.
 */
public final class CouponAccessibilityService extends AccessibilityService {
    private static final String TARGET_PACKAGE = "de.dm.meindm.android";
    private static final String ACTIVATE = "Aktivieren";
    private static final String DEACTIVATE = "Deaktivieren";
    private static final String SUCCESS = "Dein Coupon wurde erfolgreich aktiviert";
    private static final String CHANNEL_ID = "dm_coupon_helper";
    private static final int NOTIFICATION_ID = 0x6463;
    private static final long CLICK_COOLDOWN_MS = 1_500;
    private static final long SAME_BUTTON_COOLDOWN_MS = 60_000;
    private static final long CONFIRM_TIMEOUT_MS = 12_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastClickByBounds = new HashMap<>();
    private long lastClickAt;
    private boolean awaitingConfirmation;
    private long awaitingSince;
    private int confirmed;

    private final Runnable scanTask = this::scan;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        createChannel();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !TARGET_PACKAGE.contentEquals(event.getPackageName())) {
            return;
        }
        handler.removeCallbacks(scanTask);
        handler.postDelayed(scanTask, 350);
    }

    @Override
    public void onInterrupt() {
    }

    private void scan() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        boolean successVisible = containsText(root, SUCCESS);

        if (awaitingConfirmation) {
            if (successVisible) {
                awaitingConfirmation = false;
                confirmed++;
                notifyCount();
            } else if (now - awaitingSince > CONFIRM_TIMEOUT_MS) {
                awaitingConfirmation = false;
            } else {
                return;
            }
        }
        if (now - lastClickAt < CLICK_COOLDOWN_MS) {
            return;
        }
        AccessibilityNodeInfo button = findActivateButton(root, now);
        if (button == null) {
            return;
        }
        if (press(button)) {
            lastClickAt = now;
            awaitingConfirmation = true;
            awaitingSince = now;
        }
    }

    private AccessibilityNodeInfo findActivateButton(AccessibilityNodeInfo node, long now) {
        if (node == null || !node.isVisibleToUser()) {
            return null;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (matchesActivate(text) || matchesActivate(description)) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String key = bounds.flattenToString();
            Long previous = lastClickByBounds.get(key);
            if (previous == null || now - previous > SAME_BUTTON_COOLDOWN_MS) {
                lastClickByBounds.put(key, now);
                return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findActivateButton(node.getChild(i), now);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static boolean matchesActivate(CharSequence value) {
        if (value == null) {
            return false;
        }
        String label = value.toString().trim();
        return label.equalsIgnoreCase(ACTIVATE) && !label.equalsIgnoreCase(DEACTIVATE);
    }

    private boolean press(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        for (int depth = 0; depth < 6 && target != null; depth++) {
            if (target.isClickable() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            target = target.getParent();
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return false;
        }
        Path path = new Path();
        path.moveTo(bounds.exactCenterX(), bounds.exactCenterY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private boolean containsText(AccessibilityNodeInfo node, String expected) {
        if (node == null) {
            return false;
        }
        CharSequence text = node.getText();
        if (text != null && expected.contentEquals(text)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsText(node.getChild(i), expected)) {
                return true;
            }
        }
        return false;
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Coupon-Aktivierung", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    private void notifyCount() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) {
            return;
        }
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("dm-Coupons aktiviert")
                .setContentText(confirmed == 1
                        ? "1 Coupon automatisch aktiviert"
                        : confirmed + " Coupons automatisch aktiviert")
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build();
        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // Notification permission can be revoked between the check and the post.
        }
    }
}
