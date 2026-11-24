package app.gogamid.dmcoupons;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Activates available coupons in the official Mein dm app by pressing their
 * Aktivieren button. Success is read from the same button turning into
 * Deaktivieren, so up to two coupons can be in flight at once. It never presses
 * Deaktivieren and does not touch the app itself.
 */
public final class CouponAccessibilityService extends AccessibilityService {
    private static final String TARGET_PACKAGE = "de.dm.meindm.android";
    private static final String ACTIVATE = "Aktivieren";
    private static final String DEACTIVATE = "Deaktivieren";
    private static final String CHANNEL_ID = "dm_coupon_helper";
    private static final int NOTIFICATION_ID = 0x6463;
    private static final int MAX_IN_FLIGHT = 2;
    private static final long CLICK_COOLDOWN_MS = 600;
    private static final long SAME_BUTTON_COOLDOWN_MS = 45_000;
    private static final long PENDING_TIMEOUT_MS = 10_000;
    private static final long SCAN_DEBOUNCE_MS = 120;

    private static final class Pending {
        final String bounds;
        final long at;

        Pending(String bounds, long at) {
            this.bounds = bounds;
            this.at = at;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastClickByBounds = new HashMap<>();
    private final Deque<Pending> pending = new ArrayDeque<>();
    private long lastClickAt;
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
        handler.postDelayed(scanTask, SCAN_DEBOUNCE_MS);
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

        while (!pending.isEmpty() && now - pending.peekFirst().at > PENDING_TIMEOUT_MS) {
            pending.removeFirst();
        }
        pending.removeIf(p -> hasLabelAtBounds(root, p.bounds, DEACTIVATE));

        while (!pending.isEmpty() && hasLabelAtBounds(root, pending.peekFirst().bounds, DEACTIVATE)) {
            pending.removeFirst();
            confirmed++;
            notifyCount();
        }

        while (pending.size() < MAX_IN_FLIGHT && now - lastClickAt >= CLICK_COOLDOWN_MS) {
            AccessibilityNodeInfo button = findActivateButton(root, now);
            if (button == null || !press(button)) {
                break;
            }
            lastClickAt = now;
        }
    }

    private AccessibilityNodeInfo findActivateButton(AccessibilityNodeInfo node, long now) {
        if (node == null || !node.isVisibleToUser()) {
            return null;
        }
        if (matches(node.getText(), ACTIVATE) || matches(node.getContentDescription(), ACTIVATE)) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String key = bounds.flattenToString();
            Long previous = lastClickByBounds.get(key);
            if ((previous == null || now - previous > SAME_BUTTON_COOLDOWN_MS) && !isPending(key)) {
                lastClickByBounds.put(key, now);
                pending.addLast(new Pending(key, now));
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

    private boolean isPending(String bounds) {
        for (Pending p : pending) {
            if (p.bounds.equals(bounds)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(CharSequence value, String expected) {
        return value != null && expected.contentEquals(value.toString().trim());
    }

    private boolean hasLabelAtBounds(AccessibilityNodeInfo node, String boundsKey, String label) {
        if (node == null) {
            return false;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (boundsKey.equals(bounds.flattenToString())
                && (matches(node.getText(), label) || matches(node.getContentDescription(), label))) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (hasLabelAtBounds(node.getChild(i), boundsKey, label)) {
                return true;
            }
        }
        return false;
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
