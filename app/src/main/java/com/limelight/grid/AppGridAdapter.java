package com.limelight.grid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.ImageViewBitmapInfo;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.bitmap.BitmapInfo;
import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.LimelightCryptoProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.concurrent.Future;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class AppGridAdapter extends GenericGridAdapter<AppView.AppObject> {

    private boolean listMode;
    private InetAddress address;
    private String uniqueId;
    private LimelightCryptoProvider cryptoProvider;
    private SSLContext sslContext;
    private final HashMap<ImageView, Future> pendingRequests = new HashMap<ImageView, Future>();

    public AppGridAdapter(Context context, boolean listMode, InetAddress address, String uniqueId) throws NoSuchAlgorithmException, KeyManagementException {
        super(context, listMode ? R.layout.simple_row : R.layout.app_grid_item, R.drawable.image_loading);

        this.address = address;
        this.uniqueId = uniqueId;

        cryptoProvider = PlatformBinding.getCryptoProvider(context);

        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(ourKeyman, trustAllCerts, new SecureRandom());
    }

    TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }};

    KeyManager[] ourKeyman = new KeyManager[] {
            new X509KeyManager() {
                public String chooseClientAlias(String[] keyTypes,
                                                Principal[] issuers, Socket socket) {
                    return "Limelight-RSA";
                }

                public String chooseServerAlias(String keyType, Principal[] issuers,
                                                Socket socket) {
                    return null;
                }

                public X509Certificate[] getCertificateChain(String alias) {
                    return new X509Certificate[] {cryptoProvider.getClientCertificate()};
                }

                public String[] getClientAliases(String keyType, Principal[] issuers) {
                    return null;
                }

                public PrivateKey getPrivateKey(String alias) {
                    return cryptoProvider.getClientPrivateKey();
                }

                public String[] getServerAliases(String keyType, Principal[] issuers) {
                    return null;
                }
            }
    };

    // Ignore differences between given hostname and certificate hostname
    HostnameVerifier hv = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) { return true; }
    };

    public void addApp(AppView.AppObject app) {
        itemList.add(app);
    }

    public void abortPendingRequests() {
        HashMap<ImageView, Future> tempMap;

        synchronized (pendingRequests) {
            // Copy the pending requests under a lock
            tempMap = new HashMap<ImageView, Future>(pendingRequests);
        }

        for (Future f : tempMap.values()) {
            if (!f.isCancelled() && !f.isDone()) {
                f.cancel(true);
            }
        }

        synchronized (pendingRequests) {
            // Remove cancelled requests
            for (ImageView v : tempMap.keySet()) {
                pendingRequests.remove(v);
            }
        }
    }

    private Bitmap checkBitmapCache(String addrStr, int appId) {
        File addrFolder = new File(context.getCacheDir(), addrStr);
        if (addrFolder.isDirectory()) {
            File bitmapFile = new File(addrFolder, appId+".png");
            if (bitmapFile.exists()) {
                InputStream fileIn = null;
                try {
                    fileIn = new BufferedInputStream(new FileInputStream(bitmapFile));
                    Bitmap bm = BitmapFactory.decodeStream(fileIn);
                    if (bm == null) {
                        // The image seems corrupt
                        bitmapFile.delete();
                    }

                    return bm;
                } catch (IOException e) {
                    e.printStackTrace();
                    bitmapFile.delete();
                } finally {
                    if (fileIn != null) {
                        try {
                            fileIn.close();
                        } catch (IOException ignored) {}
                    }
                }
            }
        }

        return null;
    }

    // TODO: Handle pruning of bitmap cache
    private void populateBitmapCache(String addrStr, int appId, Bitmap bitmap) {
        File addrFolder = new File(context.getCacheDir(), addrStr);
        addrFolder.mkdirs();

        File bitmapFile = new File(addrFolder, appId+".png");
        try {
            // PNG ignores quality setting
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, new FileOutputStream(bitmapFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean populateImageView(final ImageView imgView, final AppView.AppObject obj) {

        // Set SSL contexts correctly to allow us to authenticate
        Ion.getDefault(imgView.getContext()).getHttpClient().getSSLSocketMiddleware().setTrustManagers(trustAllCerts);
        Ion.getDefault(imgView.getContext()).getHttpClient().getSSLSocketMiddleware().setSSLContext(sslContext);

        // Check the on-disk cache
        Bitmap cachedBitmap = checkBitmapCache(address.getHostAddress(), obj.app.getAppId());
        if (cachedBitmap != null) {
            // Cache hit; we're done
            LimeLog.info("Image cache hit for ("+address.getHostAddress()+", "+obj.app.getAppId()+")");
            imgView.setImageBitmap(cachedBitmap);
            return true;
        }

        // Kick off the deferred image load
        synchronized (pendingRequests) {
            Future<ImageViewBitmapInfo> f = Ion.with(imgView)
                    .placeholder(defaultImageRes)
                    .error(defaultImageRes)
                    .load("https://" + address.getHostAddress() + ":47984/appasset?uniqueid=" + uniqueId + "&appid=" +
                            obj.app.getAppId() + "&AssetType=2&AssetIdx=0")
                    .withBitmapInfo()
                    .setCallback(
                            new FutureCallback<ImageViewBitmapInfo>() {
                                @Override
                                public void onCompleted(Exception e, ImageViewBitmapInfo result) {
                                    synchronized (pendingRequests) {
                                        pendingRequests.remove(imgView);
                                    }

                                    // Populate the cache if we got an image back
                                    if (result != null &&
                                        result.getBitmapInfo() != null &&
                                        result.getBitmapInfo().bitmap != null) {
                                        populateBitmapCache(address.getHostAddress(), obj.app.getAppId(),
                                                result.getBitmapInfo().bitmap);
                                    }
                                }
                            });
            pendingRequests.put(imgView, f);
        }

        return true;
    }

    @Override
    public boolean populateTextView(TextView txtView, AppView.AppObject obj) {
        // Select the text view so it starts marquee mode
        txtView.setSelected(true);

        // Return false to use the app's toString method
        return false;
    }

    @Override
    public boolean populateOverlayView(ImageView overlayView, AppView.AppObject obj) {
        if (obj.app.getIsRunning()) {
            // Show the play button overlay
            overlayView.setImageResource(R.drawable.play);
            return true;
        }

        // No overlay
        return false;
    }
}
