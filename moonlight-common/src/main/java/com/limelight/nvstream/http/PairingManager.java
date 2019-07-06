package com.limelight.nvstream.http;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.LimeLog;

import java.security.cert.Certificate;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public class PairingManager {

	private NvHTTP http;
	
	private PrivateKey pk;
	private X509Certificate cert;
	private SecretKey aesKey;
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
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	
	private X509Certificate extractPlainCert(String text) throws XmlPullParserException, IOException
	{
		String certText = NvHTTP.getXmlString(text, "plaincert");
		if (certText != null) {
			byte[] certBytes = hexToBytes(certText);

			try {
				CertificateFactory cf = CertificateFactory.getInstance("X.509");
				return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(certBytes));
			} catch (CertificateException e) {
				e.printStackTrace();
				return null;
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
	
	private static byte[] decryptAes(byte[] encryptedData, SecretKey secretKey) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		
			int blockRoundedSize = ((encryptedData.length + 15) / 16) * 16;
			byte[] blockRoundedEncrypted = Arrays.copyOf(encryptedData, blockRoundedSize);
			byte[] fullDecrypted = new byte[blockRoundedSize];

			cipher.init(Cipher.DECRYPT_MODE, secretKey);
			cipher.doFinal(blockRoundedEncrypted, 0,
					blockRoundedSize, fullDecrypted);
			return fullDecrypted;
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	private static byte[] encryptAes(byte[] data, SecretKey secretKey) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		
			int blockRoundedSize = ((data.length + 15) / 16) * 16;
			byte[] blockRoundedData = Arrays.copyOf(data, blockRoundedSize);

			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			return cipher.doFinal(blockRoundedData);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	private static SecretKey generateAesKey(PairingHashAlgorithm hashAlgo, byte[] keyData) {
		byte[] aesTruncated = Arrays.copyOf(hashAlgo.hashData(keyData), 16);
		return new SecretKeySpec(aesTruncated, "AES");
	}
	
	private static byte[] concatBytes(byte[] a, byte[] b) {
		byte[] c = new byte[a.length + b.length];
		System.arraycopy(a, 0, c, 0, a.length);
		System.arraycopy(b, 0, c, a.length, b.length);
		return c;
	}
	
	public static String generatePinString() {
		Random r = new Random();
		return String.format((Locale)null, "%d%d%d%d",
				r.nextInt(10), r.nextInt(10),
				r.nextInt(10), r.nextInt(10));
	}

	public X509Certificate getPairedCert() {
		return serverCert;
	}
	
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
		byte[] saltAndPin = saltPin(salt, pin);
		aesKey = generateAesKey(hashAlgo, saltAndPin);
		
		// Send the salt and get the server cert. This doesn't have a read timeout
		// because the user must enter the PIN before the server responds
		String getCert = http.openHttpConnectionToString(http.baseUrlHttp +
				"/pair?"+http.buildUniqueIdUuidString()+"&devicename=roth&updateState=1&phrase=getservercert&salt="+
				bytesToHex(salt)+"&clientcert="+bytesToHex(pemCertBytes),
				false);
		if (!NvHTTP.getXmlString(getCert, "paired").equals("1")) {
			return PairState.FAILED;
		}

		// Save this cert for retrieval later
		serverCert = extractPlainCert(getCert);
		if (serverCert == null) {
			// Attempting to pair while another device is pairing will cause GFE
			// to give an empty cert in the response.
			return PairState.ALREADY_IN_PROGRESS;
		}

		// Require this cert for TLS to this host
		http.setServerCert(serverCert);
		
		// Generate a random challenge and encrypt it with our AES key
		byte[] randomChallenge = generateRandomBytes(16);
		byte[] encryptedChallenge = encryptAes(randomChallenge, aesKey);
		
		// Send the encrypted challenge to the server
		String challengeResp = http.openHttpConnectionToString(http.baseUrlHttp + 
				"/pair?"+http.buildUniqueIdUuidString()+"&devicename=roth&updateState=1&clientchallenge="+bytesToHex(encryptedChallenge),
				true);
		if (!NvHTTP.getXmlString(challengeResp, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrlHttp + "/unpair?"+http.buildUniqueIdUuidString(), true);
			return PairState.FAILED;
		}
		
		// Decode the server's response and subsequent challenge
		byte[] encServerChallengeResponse = hexToBytes(NvHTTP.getXmlString(challengeResp, "challengeresponse"));
		byte[] decServerChallengeResponse = decryptAes(encServerChallengeResponse, aesKey);
		
		byte[] serverResponse = Arrays.copyOfRange(decServerChallengeResponse, 0, hashAlgo.getHashLength());
		byte[] serverChallenge = Arrays.copyOfRange(decServerChallengeResponse, hashAlgo.getHashLength(), hashAlgo.getHashLength() + 16);
		
		// Using another 16 bytes secret, compute a challenge response hash using the secret, our cert sig, and the challenge
		byte[] clientSecret = generateRandomBytes(16);
		byte[] challengeRespHash = hashAlgo.hashData(concatBytes(concatBytes(serverChallenge, cert.getSignature()), clientSecret));
		byte[] challengeRespEncrypted = encryptAes(challengeRespHash, aesKey);
		String secretResp = http.openHttpConnectionToString(http.baseUrlHttp +
				"/pair?"+http.buildUniqueIdUuidString()+"&devicename=roth&updateState=1&serverchallengeresp="+bytesToHex(challengeRespEncrypted),
				true);
		if (!NvHTTP.getXmlString(secretResp, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrlHttp + "/unpair?"+http.buildUniqueIdUuidString(), true);
			return PairState.FAILED;
		}
		
		// Get the server's signed secret
		byte[] serverSecretResp = hexToBytes(NvHTTP.getXmlString(secretResp, "pairingsecret"));
		byte[] serverSecret = Arrays.copyOfRange(serverSecretResp, 0, 16);
		byte[] serverSignature = Arrays.copyOfRange(serverSecretResp, 16, 272);

		// Ensure the authenticity of the data
		if (!verifySignature(serverSecret, serverSignature, serverCert)) {
			// Cancel the pairing process
			http.openHttpConnectionToString(http.baseUrlHttp + "/unpair?"+http.buildUniqueIdUuidString(), true);
			
			// Looks like a MITM
			return PairState.FAILED;
		}
		
		// Ensure the server challenge matched what we expected (aka the PIN was correct)
		byte[] serverChallengeRespHash = hashAlgo.hashData(concatBytes(concatBytes(randomChallenge, serverCert.getSignature()), serverSecret));
		if (!Arrays.equals(serverChallengeRespHash, serverResponse)) {
			// Cancel the pairing process
			http.openHttpConnectionToString(http.baseUrlHttp + "/unpair?"+http.buildUniqueIdUuidString(), true);
			
			// Probably got the wrong PIN
			return PairState.PIN_WRONG;
		}
		
		// Send the server our signed secret
		byte[] clientPairingSecret = concatBytes(clientSecret, signData(clientSecret, pk));
		String clientSecretResp = http.openHttpConnectionToString(http.baseUrlHttp + 
				"/pair?"+http.buildUniqueIdUuidString()+"&devicename=roth&updateState=1&clientpairingsecret="+bytesToHex(clientPairingSecret),
				true);
		if (!NvHTTP.getXmlString(clientSecretResp, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrlHttp + "/unpair?"+http.buildUniqueIdUuidString(), true);
			return PairState.FAILED;
		}
		
		// Do the initial challenge (seems neccessary for us to show as paired)
		String pairChallenge = http.openHttpConnectionToString(http.baseUrlHttps +
				"/pair?"+http.buildUniqueIdUuidString()+"&devicename=roth&updateState=1&phrase=pairchallenge", true);
		if (!NvHTTP.getXmlString(pairChallenge, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrlHttp + "/unpair?"+http.buildUniqueIdUuidString(), true);
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
		        // Shouldn't ever happen
		    	e.printStackTrace();
		    	return null;
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
		        // Shouldn't ever happen
		    	e.printStackTrace();
		    	return null;
		    }
		}
	}
}
