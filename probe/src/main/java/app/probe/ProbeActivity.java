package app.probe;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Tiny app used only to check that this device can install APKs from this build pipeline. */
public class ProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        t.setPadding(40, 40, 40, 40);
        t.setText("Install OK\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + Build.MANUFACTURER + " " + Build.MODEL + "\nABI: " + Build.CPU_ABI);
        setContentView(t);
    }
}
