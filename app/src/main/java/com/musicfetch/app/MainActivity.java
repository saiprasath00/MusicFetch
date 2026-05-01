package com.musicfetch.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText searchInput;
    private ImageButton searchButton;
    private ProgressBar progressBar;
    private LinearLayout searchPanel;
    private TextView statusText;

    // The JS we inject once lucida.to has loaded.
    // It fills the search box, selects Amazon Music, then clicks Go.
    private static final String INJECT_JS_TEMPLATE =
        "(function() {" +
        "  var maxTries = 30;" +
        "  var tries = 0;" +
        "  var query = '%s';" +
        "  var timer = setInterval(function() {" +
        "    tries++;" +
        "    if (tries > maxTries) { clearInterval(timer); return; }" +
        // Find the search/URL input box
        "    var input = document.querySelector('input[placeholder*=\"Search\"]')" +
        "               || document.querySelector('input[placeholder*=\"URL\"]')" +
        "               || document.querySelector('input[type=\"text\"]')" +
        "               || document.querySelector('input[type=\"search\"]');" +
        "    if (!input) return;" +
        // Fill the input using React/Vue-friendly value setter
        "    var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;" +
        "    nativeInputValueSetter.call(input, query);" +
        "    input.dispatchEvent(new Event('input', { bubbles: true }));" +
        "    input.dispatchEvent(new Event('change', { bubbles: true }));" +
        // Find and click the Amazon Music option in the service dropdown
        "    var selects = document.querySelectorAll('select');" +
        "    var serviceSelect = null;" +
        "    for (var i = 0; i < selects.length; i++) {" +
        "      var opts = selects[i].options;" +
        "      for (var j = 0; j < opts.length; j++) {" +
        "        if (opts[j].text.toLowerCase().includes('amazon')) {" +
        "          serviceSelect = selects[i];" +
        "          serviceSelect.value = opts[j].value;" +
        "          serviceSelect.dispatchEvent(new Event('change', { bubbles: true }));" +
        "          break;" +
        "        }" +
        "      }" +
        "      if (serviceSelect) break;" +
        "    }" +
        // Also try custom dropdown buttons (not native <select>)
        "    if (!serviceSelect) {" +
        "      var allBtns = document.querySelectorAll('button, [role=\"option\"], [role=\"listbox\"] *, .option, li');" +
        "      for (var k = 0; k < allBtns.length; k++) {" +
        "        if (allBtns[k].textContent.toLowerCase().includes('amazon')) {" +
        "          allBtns[k].click();" +
        "          break;" +
        "        }" +
        "      }" +
        "    }" +
        // Click Go button
        "    var goBtn = null;" +
        "    var btns = document.querySelectorAll('button, input[type=\"submit\"]');" +
        "    for (var b = 0; b < btns.length; b++) {" +
        "      var t = btns[b].textContent.trim().toLowerCase();" +
        "      if (t === 'go' || t === 'go!' || t === 'search' || t === 'download') {" +
        "        goBtn = btns[b];" +
        "        break;" +
        "      }" +
        "    }" +
        "    if (goBtn) {" +
        "      goBtn.click();" +
        "      clearInterval(timer);" +
        "    }" +
        "  }, 300);" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchPanel  = findViewById(R.id.searchPanel);
        searchInput  = findViewById(R.id.searchInput);
        searchButton = findViewById(R.id.searchButton);
        progressBar  = findViewById(R.id.progressBar);
        statusText   = findViewById(R.id.statusText);
        webView      = findViewById(R.id.webView);

        setupWebView();
        setupSearch();

        // Load lucida.to on start
        webView.loadUrl("https://lucida.to/");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setSupportZoom(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Make lucida.to think it's a real browser
        ws.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
            @Override
            public void onReceivedTitle(WebView view, String title) {
                // Optional: update toolbar title
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // If we have a pending search query, inject it now
                String pendingQuery = (String) webView.getTag();
                if (pendingQuery != null && !pendingQuery.isEmpty() && url.contains("lucida.to")) {
                    injectSearch(pendingQuery);
                    webView.setTag(null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep all navigation inside the WebView
                return false;
            }
        });

        // Handle file downloads triggered by lucida.to
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimeType,
                                        long contentLength) {
                requestStorageAndDownload(url, userAgent, contentDisposition, mimeType);
            }
        });
    }

    private void setupSearch() {
        searchButton.setOnClickListener(v -> performSearch());

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            searchInput.setError("Enter a song name");
            return;
        }

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);

        statusText.setText("Loading lucida.to…");
        statusText.setVisibility(View.VISIBLE);

        String currentUrl = webView.getUrl();

        if (currentUrl != null && currentUrl.contains("lucida.to")) {
            // Already on lucida.to — just reload home and inject
            webView.setTag(query);
            webView.loadUrl("https://lucida.to/");
        } else {
            // Navigate to lucida.to first, inject after page loads
            webView.setTag(query);
            webView.loadUrl("https://lucida.to/");
        }
    }

    private void injectSearch(String query) {
        // Escape query for JS string injection
        String safe = query
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", " ");

        String js = String.format(INJECT_JS_TEMPLATE, safe);

        // Small delay to let JS framework hydrate
        webView.postDelayed(() -> {
            webView.evaluateJavascript(js, result -> {
                runOnUiThread(() -> {
                    statusText.setText("Searching for: " + query);
                    statusText.setVisibility(View.VISIBLE);
                });
            });
        }, 800);
    }

    private void requestStorageAndDownload(String url, String userAgent,
                                           String contentDisposition, String mimeType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return;
            }
        }
        startDownload(url, userAgent, contentDisposition, mimeType);
    }

    private void startDownload(String url, String userAgent,
                                String contentDisposition, String mimeType) {
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        String cookies  = CookieManager.getInstance().getCookie(url);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(mimeType);
        if (cookies != null) request.addRequestHeader("cookie", cookies);
        request.addRequestHeader("User-Agent", userAgent);
        request.setDescription("Downloading " + fileName);
        request.setTitle(fileName);
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_MUSIC, fileName);

        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.enqueue(request);

        Toast.makeText(this,
            "Downloading: " + fileName + "\nCheck your Music folder.",
            Toast.LENGTH_LONG).show();
    }

    // Handle back button inside WebView
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
