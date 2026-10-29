package com.smartattendance.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smart Student Attendance - a thin Android shell around the Flask website.
 *
 * - Shows the existing Flask pages inside a WebView (design unchanged).
 * - Lets the user type the Flask server address once (saved on the phone).
 * - "Scan QR" opens the camera, reads the session QR code and opens
 *   /mark-attendance/<id> on the CURRENT server address.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "smart_attendance_prefs";
    private static final String KEY_SERVER = "server_url";
    private static final Pattern SESSION_PATTERN =
            Pattern.compile("/mark-attendance/(\\d+)");

    private WebView webView;
    private String serverUrl = "";

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    handleScannedText(result.getContents());
                }
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isInternal(uri)) {
                    return false; // keep inside the app
                }
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    showErrorPage();
                }
            }
        });

        findViewById(R.id.btnHome).setOnClickListener(v -> openHome());
        findViewById(R.id.btnScan).setOnClickListener(v -> startScan());
        findViewById(R.id.btnServer).setOnClickListener(v -> showServerDialog());

        // Android Back button = browser Back button
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        serverUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SERVER, "");
        if (serverUrl.isEmpty()) {
            showServerDialog();
        } else {
            openHome();
        }
    }

    // ---------- navigation ----------

    private void openHome() {
        if (serverUrl.isEmpty()) {
            showServerDialog();
            return;
        }
        webView.loadUrl(serverUrl + "/");
    }

    private boolean isInternal(Uri uri) {
        String host = Uri.parse(serverUrl).getHost();
        return host != null && host.equalsIgnoreCase(uri.getHost());
    }

    private void showErrorPage() {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "</head><body style='font-family:Arial;padding:28px;color:#1e293b;background:#f1f5f9'>"
                + "<h2 style='color:#b91c1c'>Cannot reach the server</h2>"
                + "<p>The app tried to open:<br><b>" + TextUtils.htmlEncode(serverUrl) + "</b></p>"
                + "<ul><li>Is the Flask server running on the computer?</li>"
                + "<li>Are the phone and computer on the same Wi-Fi?</li>"
                + "<li>Did the computer's IP address change?</li></ul>"
                + "<p>Tap <b>Server</b> below to change the address, or <b>Home</b> to try again.</p>"
                + "</body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    // ---------- QR scanning ----------

    private void startScan() {
        if (serverUrl.isEmpty()) {
            showServerDialog();
            return;
        }
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan the attendance QR code");
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private void handleScannedText(String text) {
        // The QR contains e.g. http://192.168.1.5:5000/mark-attendance/7
        // We only take the session number and use the server address saved in
        // the app, so old/incorrect IPs inside a QR code do not matter.
        Matcher m = SESSION_PATTERN.matcher(text);
        if (m.find()) {
            webView.loadUrl(serverUrl + "/mark-attendance/" + m.group(1));
        } else {
            Toast.makeText(this,
                    "This is not a Smart Attendance session QR code.",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---------- server address dialog ----------

    private void showServerDialog() {
        final boolean canCancel = !serverUrl.isEmpty();

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIANT_URI);
        input.setHint("http://192.168.1.5:5000");
        input.setText(canCancel ? serverUrl : "http://");
        input.setSelection(input.getText().length());

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Server address")
                .setMessage("Enter the address of the computer running the Flask server "
                        + "(example: http://192.168.1.5:5000)")
                .setView(box)
                .setCancelable(canCancel)
                .setPositiveButton("Save", (dialog, which) -> {
                    String url = normalizeUrl(input.getText().toString());
                    if (url.isEmpty() || Uri.parse(url).getHost() == null) {
                        Toast.makeText(this, "Please enter a valid address.",
                                Toast.LENGTH_LONG).show();
                        showServerDialog();
                        return;
                    }
                    serverUrl = url;
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .edit().putString(KEY_SERVER, serverUrl).apply();
                    openHome();
                });

        if (canCancel) {
            builder.setNegativeButton("Cancel", null);
        }
        builder.show();
    }

    private String normalizeUrl(String raw) {
        String u = raw == null ? "" : raw.trim();
        if (u.isEmpty() || u.equals("http://") || u.equals("https://")) {
            return "";
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
