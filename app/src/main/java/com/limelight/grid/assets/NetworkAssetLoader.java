package com.limelight.grid.assets;

import android.content.Context;
import android.graphics.Bitmap;

import com.koushikdutta.ion.Ion;
import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;

import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

public class NetworkAssetLoader implements CachedAppAssetLoader.NetworkLoader {
    private final Context context;
    private final LimelightCryptoProvider cryptoProvider;
    private final SSLContext sslContext;

    public NetworkAssetLoader(Context context) throws NoSuchAlgorithmException, KeyManagementException {
        this.context = context;

        cryptoProvider = PlatformBinding.getCryptoProvider(context);

        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(ourKeyman, trustAllCerts, new SecureRandom());
    }

    private final TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }};

    private final KeyManager[] ourKeyman = new KeyManager[] {
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
    private final HostnameVerifier hv = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) { return true; }
    };

    @Override
    public Bitmap loadBitmap(CachedAppAssetLoader.LoaderTuple tuple) {
        // Set SSL contexts correctly to allow us to authenticate
        Ion.getDefault(context).getHttpClient().getSSLSocketMiddleware().setTrustManagers(trustAllCerts);
        Ion.getDefault(context).getHttpClient().getSSLSocketMiddleware().setSSLContext(sslContext);
        Ion.getDefault(context).getHttpClient().getSSLSocketMiddleware().setHostnameVerifier(hv);
        Ion.getDefault(context).getBitmapCache().clear();

        Bitmap bmp = Ion.with(context)
                .load("https://" + getCurrentAddress(tuple.computer).getHostAddress() + ":47984/appasset?uniqueid=" +
                        tuple.uniqueId + "&appid=" + tuple.app.getAppId() + "&AssetType=2&AssetIdx=0")
                .asBitmap()
                .tryGet();
        if (bmp != null) {
            LimeLog.info("Network asset load complete: " + tuple);
        }
        else {
            LimeLog.info("Network asset load failed: " + tuple);
        }

        return bmp;
    }

    private static InetAddress getCurrentAddress(ComputerDetails computer) {
        if (computer.reachability == ComputerDetails.Reachability.LOCAL) {
            return computer.localIp;
        }
        else {
            return computer.remoteIp;
        }
    }
}
