package com.limelight.grid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.ImageViewBitmapInfo;
import com.koushikdutta.ion.Ion;
import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.utils.CacheHelper;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;
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

    private ComputerDetails computer;
    private String uniqueId;
    private LimelightCryptoProvider cryptoProvider;
    private SSLContext sslContext;
    private final HashMap<ImageView, Future> pendingRequests = new HashMap<ImageView, Future>();

    public AppGridAdapter(Context context, double gridScaleFactor, boolean listMode, ComputerDetails computer, String uniqueId) throws NoSuchAlgorithmException, KeyManagementException {
        super(context, listMode ? R.layout.simple_row : R.layout.app_grid_item, R.drawable.image_loading, gridScaleFactor);

        this.computer = computer;
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

    private void sortList() {
        Collections.sort(itemList, new Comparator<AppView.AppObject>() {
            @Override
            public int compare(AppView.AppObject lhs, AppView.AppObject rhs) {
                return lhs.app.getAppName().compareTo(rhs.app.getAppName());
            }
        });
    }

    private InetAddress getCurrentAddress() {
        if (computer.reachability == ComputerDetails.Reachability.LOCAL) {
            return computer.localIp;
        }
        else {
            return computer.remoteIp;
        }
    }

    public void addApp(AppView.AppObject app) {
        itemList.add(app);
        sortList();
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

    private Bitmap checkBitmapCache(int appId) {
        try {
            InputStream in = CacheHelper.openCacheFileForInput(context.getCacheDir(), "boxart", computer.uuid.toString(), appId+".png");
            Bitmap bm = BitmapFactory.decodeStream(in);
            in.close();
            return bm;
        } catch (IOException e) {}
        return null;
    }

    // TODO: Handle pruning of bitmap cache
    private void populateBitmapCache(UUID uuid, int appId, Bitmap bitmap) {
        try {
            // PNG ignores quality setting
            FileOutputStream out = CacheHelper.openCacheFileForOutput(context.getCacheDir(), "boxart", uuid.toString(), appId+".png");
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean populateImageView(final ImageView imgView, final AppView.AppObject obj) {

        // Set SSL contexts correctly to allow us to authenticate
        Ion.getDefault(imgView.getContext()).getHttpClient().getSSLSocketMiddleware().setTrustManagers(trustAllCerts);
        Ion.getDefault(imgView.getContext()).getHttpClient().getSSLSocketMiddleware().setSSLContext(sslContext);

        // Check the on-disk cache
        Bitmap cachedBitmap = checkBitmapCache(obj.app.getAppId());
        if (cachedBitmap != null) {
            // Cache hit; we're done
            LimeLog.info("Image cache hit for ("+computer.uuid+", "+obj.app.getAppId()+")");
            imgView.setImageBitmap(cachedBitmap);
            return true;
        }

        // Kick off the deferred image load
        synchronized (pendingRequests) {
            Future<ImageViewBitmapInfo> f = Ion.with(imgView)
                    .placeholder(defaultImageRes)
                    .error(defaultImageRes)
                    .load("https://" + getCurrentAddress().getHostAddress() + ":47984/appasset?uniqueid=" + uniqueId + "&appid=" +
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
                                        populateBitmapCache(computer.uuid, obj.app.getAppId(),
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
