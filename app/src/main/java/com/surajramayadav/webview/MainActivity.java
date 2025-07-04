package com.surajramayadav.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private long downloadID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        webView = new WebView(this);
        webView.setId(View.generateViewId());
        webView.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        layout.addView(webView);
        setContentView(layout);

        checkStoragePermission();
        setupWebView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // Add JavaScript interface for blob downloads
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidInterface");
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Sync cookies before loading URL
                syncCookies(url);
                view.loadUrl(url);
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject cookies after page loads
                injectCookies(url);
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {

                // Handle blob URLs using JavaScript interface
                if (url.startsWith("blob:")) {
                    // Inject JavaScript to handle blob download
                    String downloadScript = "javascript:(function() {" +
                            "var xhr = new XMLHttpRequest();" +
                            "xhr.open('GET', '" + url + "', true);" +
                            "xhr.responseType = 'blob';" +
                            "xhr.onload = function(e) {" +
                            "  if (this.status == 200) {" +
                            "    var blob = this.response;" +
                            "    var reader = new FileReader();" +
                            "    reader.onloadend = function() {" +
                            "      var fileName = '" + URLUtil.guessFileName(url, contentDisposition, mimetype) + "';" +
                            "      AndroidInterface.saveFile(reader.result, fileName);" +
                            "    };" +
                            "    reader.readAsDataURL(blob);" +
                            "  }" +
                            "};" +
                            "xhr.send();" +
                            "})()";

                    webView.loadUrl(downloadScript);
                    return;
                }

                // Handle regular HTTP/HTTPS downloads
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                request.setTitle(fileName);
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                downloadID = dm.enqueue(request);

                Log.d("DownloadTest", "Starting download: " + fileName + ", ID: " + downloadID);
                Toast.makeText(getApplicationContext(),
                        "Downloading File: " + fileName,
                        Toast.LENGTH_LONG).show();
            }
        });

        loadUrlWithCookies("https://bol.sudlife.in/Navigation/ResumeJourney?LeadId=WL78f50694a0256202512512549");
    }

    private void syncCookies(String url) {
        try {
            CookieManager.getInstance().flush();
        } catch (Exception e) {
            Log.e("CookieSync", "Error syncing cookies", e);
        }
    }

    private void loadUrlWithCookies(String url) {
        syncCookies(url);
        webView.loadUrl(url);
    }
    private void injectCookies(String url) {
        try {
            // Example: Add custom cookies if needed
            String cookieString = "example_cookie=value; path=/";
            CookieManager.getInstance().setCookie(url, cookieString);
        } catch (Exception e) {
            Log.e("CookieInjection", "Error injecting cookies", e);
        }
    }
    // JavaScript interface to handle blob downloads
    public class WebAppInterface {
        private Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void saveFile(String base64Data, String fileName) {
            try {
                // Extract the base64 part (remove data:image/png;base64, prefix)
                String base64 = base64Data.split(",")[1];
                byte[] data = Base64.decode(base64, Base64.DEFAULT);

                // Save to Downloads directory
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, fileName);

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(data);
                    fos.flush();
                }

                // Notify user
                Toast.makeText(mContext, "File saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();

                // Optional: Scan file so it appears immediately in Downloads
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(file));
                sendBroadcast(mediaScanIntent);

            } catch (Exception e) {
                Log.e("BlobDownload", "Error saving file", e);
                Toast.makeText(mContext, "Error saving file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else {
                registerDownloadReceiver();
            }
        } else {
            registerDownloadReceiver();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted.", Toast.LENGTH_SHORT).show();
                registerDownloadReceiver();
            } else {
                Toast.makeText(this, "Storage permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void registerDownloadReceiver() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(onDownloadComplete, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(onDownloadComplete, filter);
        }
    }

    private final android.content.BroadcastReceiver onDownloadComplete = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadID) {
                Toast.makeText(context, "Download complete!", Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(onDownloadComplete);
        } catch (IllegalArgumentException e) {
            Log.w("MainActivity", "Receiver not registered or already unregistered");
        }
    }
}