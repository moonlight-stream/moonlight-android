package com.limelight.nvstream.http;

import android.util.Log;

import org.bitcoinj.core.Base58;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESLightEngine;
import org.bouncycastle.crypto.params.KeyParameter;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.LimeLog;

import java.net.URLEncoder;
import java.security.cert.Certificate;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.Arrays;
import java.util.Locale;

import com.limelight.solanaWallet.EncryptionHelper;
import com.limelight.solanaWallet.ShagaHotAccount;
import com.limelight.solanaWallet.SolanaPreferenceManager;
import com.solana.core.PublicKey;

public class PairingManager {

    private NvHTTP http;
    
    private PrivateKey pk;
    private X509Certificate cert;
    private byte[] pemCertBytes;

    private X509Certificate serverCert;
    
    public enum PairState {
        NOT_PAIRED,
        PAIRED,
        PIN_WRONG,
        FAILED,
        ALREADY_IN_PROGRESS
    }
    
    public PairingManager(NvHTTP http, LimelightCryptoProvider cryptoProvider) {
        this.http = http;
        this.cert = cryptoProvider.getClientCertificate();
        this.pemCertBytes = cryptoProvider.getPemEncodedClientCertificate();
        this.pk = cryptoProvider.getClientPrivateKey();
    }
    
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    private static byte[] hexToBytes(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Illegal string length: "+len);
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    
    private X509Certificate extractPlainCert(String text) throws XmlPullParserException, IOException
    {
        // Plaincert may be null if another client is already trying to pair
        String certText = NvHTTP.getXmlString(text, "plaincert", false);
        if (certText != null) {
            byte[] certBytes = hexToBytes(certText);

            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(certBytes));
            } catch (CertificateException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        else {
            return null;
        }
    }
    
    private byte[] generateRandomBytes(int length)
    {
        byte[] rand = new byte[length];
        new SecureRandom().nextBytes(rand);
        return rand;
    }
    
    private static byte[] saltPin(byte[] salt, String pin) throws UnsupportedEncodingException {
        byte[] saltedPin = new byte[salt.length + pin.length()];
        System.arraycopy(salt, 0, saltedPin, 0, salt.length);
        System.arraycopy(pin.getBytes("UTF-8"), 0, saltedPin, salt.length, pin.length());
        return saltedPin;
    }
    
    private static boolean verifySignature(byte[] data, byte[] signature, Certificate cert) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(cert.getPublicKey());
            sig.update(data);
            return sig.verify(signature);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    private static byte[] signData(byte[] data, PrivateKey key) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key);
            sig.update(data);
            byte[] signature = new byte[256];
            sig.sign(signature, 0, signature.length);
            return signature;
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static byte[] performBlockCipher(BlockCipher blockCipher, byte[] input) {
        int blockSize = blockCipher.getBlockSize();
        int blockRoundedSize = (input.length + (blockSize - 1)) & ~(blockSize - 1);

        byte[] blockRoundedInputData = Arrays.copyOf(input, blockRoundedSize);
        byte[] blockRoundedOutputData = new byte[blockRoundedSize];

        for (int offset = 0; offset < blockRoundedSize; offset += blockSize) {
            blockCipher.processBlock(blockRoundedInputData, offset, blockRoundedOutputData, offset);
        }

        return blockRoundedOutputData;
    }
    
    private static byte[] decryptAes(byte[] encryptedData, byte[] aesKey) {
        BlockCipher aesEngine = new AESLightEngine();
        aesEngine.init(false, new KeyParameter(aesKey));
        return performBlockCipher(aesEngine, encryptedData);
    }
    
    private static byte[] encryptAes(byte[] plaintextData, byte[] aesKey) {
        BlockCipher aesEngine = new AESLightEngine();
        aesEngine.init(true, new KeyParameter(aesKey));
        return performBlockCipher(aesEngine, plaintextData);
    }
    
    private static byte[] generateAesKey(PairingHashAlgorithm hashAlgo, byte[] keyData) {
        return Arrays.copyOf(hashAlgo.hashData(keyData), 16);
    }
    
    private static byte[] concatBytes(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
    
    public static String generatePinString() {
        SecureRandom r = new SecureRandom();
        return String.format((Locale)null, "%d%d%d%d",
                r.nextInt(10), r.nextInt(10),
                r.nextInt(10), r.nextInt(10));
    }

    public X509Certificate getPairedCert() {
        return serverCert;
    }
    // Shaga

    public PairState publicPairShaga(String serverInfo, String pin, byte[] x25519PublicKey) throws IOException, XmlPullParserException {
        return pairShaga(serverInfo, pin, x25519PublicKey);
    }


    private PairState pairShaga(String serverInfo, String pin, byte[] x25519ServerPublicKey) throws IOException, XmlPullParserException {

        PairingHashAlgorithm hashAlgo;

        int serverMajorVersion = http.getServerMajorVersion(serverInfo);
        LimeLog.info("Pairing with server generation: " + serverMajorVersion);

        if (serverMajorVersion >= 7) {
            hashAlgo = new Sha256PairingHash();
            Log.d("ShagaPair", "Using SHA-256 for pairing hash algorithm");
        } else {
            hashAlgo = new Sha1PairingHash();
            Log.d("ShagaPair", "Using SHA-1 for pairing hash algorithm");
        }

        // Retrieve stored ShagaHotAccount from SolanaPreferenceManager
        ShagaHotAccount shagaHotAccount = SolanaPreferenceManager.getStoredHotAccount();
        // Check if ShagaHotAccount is null
        if (shagaHotAccount == null) {
            Log.e("ShagaPair", "No stored ShagaHotAccount found.");
            return PairState.FAILED;
        }

        PublicKey publicKey = shagaHotAccount.getPublicKey();
        byte[] ed25519PrivateKey =  shagaHotAccount.obtainSecretKey();
        Log.d("EncryptionDebug", "Original Ed25519 Public Key: " + bytesToHex(publicKey.toByteArray()));
        Log.d("EncryptionDebug", "Original Ed25519 Private Key: " + bytesToHex(ed25519PrivateKey));

        byte[] x25519ClientPrivateKey = EncryptionHelper.mapSecretEd25519ToX25519(ed25519PrivateKey);
        byte[] x25519ClientPublicKey = EncryptionHelper.mapPublicEd25519ToX25519(publicKey.toByteArray());
        Log.d("EncryptionDebug", "Converted X25519 Private Key: " + bytesToHex(x25519ClientPrivateKey));
        Log.d("EncryptionDebug", "Converted X25519 Public Key: " + bytesToHex(x25519ClientPublicKey));

        byte[] encryptedPin = EncryptionHelper.encryptPinWithX25519PublicKey(pin, x25519ServerPublicKey, x25519ClientPrivateKey);
        Log.d("EncryptionDebug", "Encrypted PIN in Bytes: " + bytesToHex(encryptedPin));


        String hexEncryptedPin = bytesToHex(encryptedPin);
        String x25519ClientPublicKeyBase58 = Base58.encode(x25519ClientPublicKey);
        String publicKeyBase58 = Base58.encode(publicKey.toByteArray());
        Log.d("EncryptionDebug", "x25519ClientPublicKey in Base58: " + x25519ClientPublicKeyBase58);
        Log.d("EncryptionDebug", "publicKey in Base58: " + publicKeyBase58);
        Log.d("EncryptionDebug", "Encrypted PIN in Hex: " + hexEncryptedPin);

        String encodedEdPublicKey = URLEncoder.encode(publicKeyBase58, "UTF-8");
        String encodedHexEncryptedPin = URLEncoder.encode(hexEncryptedPin, "UTF-8");
        String encodedXPublicKey = URLEncoder.encode(x25519ClientPublicKeyBase58, "UTF-8");
        Log.d("ShagaPair", "Encoded Ed25519 Public Key: " + encodedEdPublicKey);
        Log.d("ShagaPair", "Encoded Hex Encrypted Pin: " + encodedHexEncryptedPin);
        Log.d("ShagaPair", "Encoded X25519 Public Key: " + encodedXPublicKey);

        byte[] salt = generateRandomBytes(16);
        byte[] aesKey = generateAesKey(hashAlgo, saltPin(salt, pin));

        String getCert = http.executeShagaPairingCommand(
                "phrase=getservercert&salt=" +
                        bytesToHex(salt) +
                        "&clientcert=" + bytesToHex(pemCertBytes) +
                        "&encryptedPin=" + encodedHexEncryptedPin +
                        "&Ed25519PublicKey=" + encodedEdPublicKey +
                        "&X25519PublicKey=" + encodedXPublicKey,
                false
        );
        Log.d("ShagaPair", "Executed pairing command, received certificate: " + getCert);

        if (!NvHTTP.getXmlString(getCert, "paired", true).equals("1")) {
            Log.e("ShagaPair", "Pairing failed during certificate check");
            return PairState.FAILED;
        }
        Log.d("ShagaPair", "Certificate check passed, pairing successful");

        serverCert = extractPlainCert(getCert);
        if (serverCert == null) {
            Log.e("ShagaPair", "Server certificate extraction failed, pairing already in progress");
            http.unpair();
            return PairState.ALREADY_IN_PROGRESS;
        }
        Log.d("ShagaPair", "Successfully extracted server certificate");

        http.setServerCert(serverCert);
        Log.d("ShagaPair", "Set server certificate for TLS");

        byte[] randomChallenge = generateRandomBytes(16);
        Log.d("ShagaPair", "Generated random challenge bytes");

        byte[] encryptedChallenge = encryptAes(randomChallenge, aesKey);
        Log.d("ShagaPair", "Encrypted the random challenge");

        String challengeResp = http.executePairingCommand("clientchallenge="+bytesToHex(encryptedChallenge), true);
        if (!NvHTTP.getXmlString(challengeResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        // Decode the server's response and subsequent challenge
        byte[] encServerChallengeResponse = hexToBytes(NvHTTP.getXmlString(challengeResp, "challengeresponse", true));
        byte[] decServerChallengeResponse = decryptAes(encServerChallengeResponse, aesKey);

        byte[] serverResponse = Arrays.copyOfRange(decServerChallengeResponse, 0, hashAlgo.getHashLength());
        byte[] serverChallenge = Arrays.copyOfRange(decServerChallengeResponse, hashAlgo.getHashLength(), hashAlgo.getHashLength() + 16);

        // Using another 16 bytes secret, compute a challenge response hash using the secret, our cert sig, and the challenge
        byte[] clientSecret = generateRandomBytes(16);
        byte[] challengeRespHash = hashAlgo.hashData(concatBytes(concatBytes(serverChallenge, cert.getSignature()), clientSecret));
        byte[] challengeRespEncrypted = encryptAes(challengeRespHash, aesKey);
        String secretResp = http.executePairingCommand("serverchallengeresp="+bytesToHex(challengeRespEncrypted), true);
        if (!NvHTTP.getXmlString(secretResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        // Get the server's signed secret
        byte[] serverSecretResp = hexToBytes(NvHTTP.getXmlString(secretResp, "pairingsecret", true));
        byte[] serverSecret = Arrays.copyOfRange(serverSecretResp, 0, 16);
        byte[] serverSignature = Arrays.copyOfRange(serverSecretResp, 16, 272);

        // Ensure the authenticity of the data
        if (!verifySignature(serverSecret, serverSignature, serverCert)) {
            // Cancel the pairing process
            http.unpair();

            // Looks like a MITM
            return PairState.FAILED;
        }

        // Ensure the server challenge matched what we expected (aka the PIN was correct)
        byte[] serverChallengeRespHash = hashAlgo.hashData(concatBytes(concatBytes(randomChallenge, serverCert.getSignature()), serverSecret));
        if (!Arrays.equals(serverChallengeRespHash, serverResponse)) {
            // Cancel the pairing process
            http.unpair();

            // Probably got the wrong PIN
            return PairState.PIN_WRONG;
        }

        // Send the server our signed secret
        byte[] clientPairingSecret = concatBytes(clientSecret, signData(clientSecret, pk));
        String clientSecretResp = http.executePairingCommand("clientpairingsecret="+bytesToHex(clientPairingSecret), true);
        if (!NvHTTP.getXmlString(clientSecretResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        // Do the initial challenge (seems necessary for us to show as paired)
        String pairChallenge = http.executePairingChallenge();
        if (!NvHTTP.getXmlString(pairChallenge, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        return PairState.PAIRED;
    }
    // Shaga
    
    public PairState pair(String serverInfo, String pin) throws IOException, XmlPullParserException {
        PairingHashAlgorithm hashAlgo;

        int serverMajorVersion = http.getServerMajorVersion(serverInfo);
        LimeLog.info("Pairing with server generation: "+serverMajorVersion);
        if (serverMajorVersion >= 7) {
            // Gen 7+ uses SHA-256 hashing
            hashAlgo = new Sha256PairingHash();
        }
        else {
            // Prior to Gen 7, SHA-1 is used
            hashAlgo = new Sha1PairingHash();
        }
        
        // Generate a salt for hashing the PIN
        byte[] salt = generateRandomBytes(16);

        // Combine the salt and pin, then create an AES key from them
        byte[] aesKey = generateAesKey(hashAlgo, saltPin(salt, pin));
        
        // Send the salt and get the server cert. This doesn't have a read timeout
        // because the user must enter the PIN before the server responds
        String getCert = http.executePairingCommand("phrase=getservercert&salt="+
                bytesToHex(salt)+"&clientcert="+bytesToHex(pemCertBytes),
                false);
        if (!NvHTTP.getXmlString(getCert, "paired", true).equals("1")) {
            return PairState.FAILED;
        }

        // Save this cert for retrieval later
        serverCert = extractPlainCert(getCert);
        if (serverCert == null) {
            // Attempting to pair while another device is pairing will cause GFE
            // to give an empty cert in the response.
            http.unpair();
            return PairState.ALREADY_IN_PROGRESS;
        }

        // Require this cert for TLS to this host
        http.setServerCert(serverCert);
        
        // Generate a random challenge and encrypt it with our AES key
        byte[] randomChallenge = generateRandomBytes(16);
        byte[] encryptedChallenge = encryptAes(randomChallenge, aesKey);
        
        // Send the encrypted challenge to the server
        String challengeResp = http.executePairingCommand("clientchallenge="+bytesToHex(encryptedChallenge), true);
        if (!NvHTTP.getXmlString(challengeResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }
        
        // Decode the server's response and subsequent challenge
        byte[] encServerChallengeResponse = hexToBytes(NvHTTP.getXmlString(challengeResp, "challengeresponse", true));
        byte[] decServerChallengeResponse = decryptAes(encServerChallengeResponse, aesKey);
        
        byte[] serverResponse = Arrays.copyOfRange(decServerChallengeResponse, 0, hashAlgo.getHashLength());
        byte[] serverChallenge = Arrays.copyOfRange(decServerChallengeResponse, hashAlgo.getHashLength(), hashAlgo.getHashLength() + 16);
        
        // Using another 16 bytes secret, compute a challenge response hash using the secret, our cert sig, and the challenge
        byte[] clientSecret = generateRandomBytes(16);
        byte[] challengeRespHash = hashAlgo.hashData(concatBytes(concatBytes(serverChallenge, cert.getSignature()), clientSecret));
        byte[] challengeRespEncrypted = encryptAes(challengeRespHash, aesKey);
        String secretResp = http.executePairingCommand("serverchallengeresp="+bytesToHex(challengeRespEncrypted), true);
        if (!NvHTTP.getXmlString(secretResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }
        
        // Get the server's signed secret
        byte[] serverSecretResp = hexToBytes(NvHTTP.getXmlString(secretResp, "pairingsecret", true));
        byte[] serverSecret = Arrays.copyOfRange(serverSecretResp, 0, 16);
        byte[] serverSignature = Arrays.copyOfRange(serverSecretResp, 16, 272);

        // Ensure the authenticity of the data
        if (!verifySignature(serverSecret, serverSignature, serverCert)) {
            // Cancel the pairing process
            http.unpair();
            
            // Looks like a MITM
            return PairState.FAILED;
        }
        
        // Ensure the server challenge matched what we expected (aka the PIN was correct)
        byte[] serverChallengeRespHash = hashAlgo.hashData(concatBytes(concatBytes(randomChallenge, serverCert.getSignature()), serverSecret));
        if (!Arrays.equals(serverChallengeRespHash, serverResponse)) {
            // Cancel the pairing process
            http.unpair();
            
            // Probably got the wrong PIN
            return PairState.PIN_WRONG;
        }
        
        // Send the server our signed secret
        byte[] clientPairingSecret = concatBytes(clientSecret, signData(clientSecret, pk));
        String clientSecretResp = http.executePairingCommand("clientpairingsecret="+bytesToHex(clientPairingSecret), true);
        if (!NvHTTP.getXmlString(clientSecretResp, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }
        
        // Do the initial challenge (seems necessary for us to show as paired)
        String pairChallenge = http.executePairingChallenge();
        if (!NvHTTP.getXmlString(pairChallenge, "paired", true).equals("1")) {
            http.unpair();
            return PairState.FAILED;
        }

        return PairState.PAIRED;
    }
    
    private interface PairingHashAlgorithm {
        int getHashLength();
        byte[] hashData(byte[] data);
    }
    
    private static class Sha1PairingHash implements PairingHashAlgorithm {
        public int getHashLength() {
            return 20;
        }
        
        public byte[] hashData(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                return md.digest(data);
            }
            catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
    
    private static class Sha256PairingHash implements PairingHashAlgorithm {
        public int getHashLength() {
            return 32;
        }
        
        public byte[] hashData(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return md.digest(data);
            }
            catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
