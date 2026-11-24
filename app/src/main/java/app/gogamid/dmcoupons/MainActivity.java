package app.gogamid.dmcoupons;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(24);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("dm Coupon Helper");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);

        TextView info = new TextView(this);
        info.setText("Aktiviert verfügbare Coupons im offiziellen Mein dm Coupons-Tab "
                + "über den Aktivieren-Knopf und meldet jede bestätigte Aktivierung.\n\n"
                + "1. Bedienungshilfe aktivieren\n"
                + "2. Benachrichtigungen erlauben\n"
                + "3. Mein dm öffnen und Coupons antippen\n\n"
                + "Der Helper ändert die App nicht, drückt nie Deaktivieren und sendet keine Daten.");
        info.setTextSize(15);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0, dp(12), 0, dp(20));

        Button accessibility = new Button(this);
        accessibility.setText("Bedienungshilfe aktivieren");
        accessibility.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button notifications = new Button(this);
        notifications.setText("Benachrichtigungen erlauben");
        notifications.setOnClickListener(v -> requestNotifications());

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(16), 0, 0);

        content.addView(title);
        content.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(accessibility);
        content.addView(notifications);
        content.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(isServiceEnabled()
                ? "Bedienungshilfe: aktiv"
                : "Bedienungshilfe: nicht aktiv");
        status.setTextColor(isServiceEnabled() ? Color.parseColor("#1B7F3B") : Color.parseColor("#B3261E"));
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        String me = getPackageName() + "/" + CouponAccessibilityService.class.getName();
        for (String entry : enabled.split(":")) {
            if (entry.equalsIgnoreCase(me)) {
                return true;
            }
        }
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
