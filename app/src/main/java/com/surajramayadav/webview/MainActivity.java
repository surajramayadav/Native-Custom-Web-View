package com.surajramayadav.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {

                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                request.setTitle(fileName);
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                // Save to app-specific external files directory (no extra permission required)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);



                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                downloadID = dm.enqueue(request);

                Log.d("DownloadTest", "Starting download: " + fileName + ", ID: " + downloadID);

                Toast.makeText(getApplicationContext(),
                        "Downloading File: " + fileName,
                        Toast.LENGTH_LONG).show();
            }
        });

        webView.loadUrl("https://file-examples.com/index.php/sample-documents-download/sample-pdf-download/");
    }

    private final android.content.BroadcastReceiver onDownloadComplete = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            Log.d("DownloadReceiver", "recive");

            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            Log.d("DownloadReceiver", "Download complete broadcast received, id=" + id + ", expected id=" + downloadID);

            if (id == downloadID) {
                Toast.makeText(context, "Download complete!", Toast.LENGTH_LONG).show();
            }
        }
    };


    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else {
                // Permission already granted
                registerDownloadReceiver();
            }
        } else {
            // No permission needed or handled by scoped storage on Android 11+
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            Log.d("registerDownloadReceiver ", "registerDownloadReceiver: 1st condition");
            MainActivity.this.registerReceiver(onDownloadComplete, filter, Context.RECEIVER_NOT_EXPORTED);

        } else {
            Log.d("registerDownloadReceiver ", "registerDownloadReceiver: 2st condition");
            registerReceiver(onDownloadComplete, filter);
        }
    }


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
//            unregisterReceiver(onDownloadComplete);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered or already unregistered
            Log.w("MainActivity", "Receiver not registered or already unregistered");
        }
    }
}
