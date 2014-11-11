package com.limelight.nvstream.http;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import org.xmlpull.v1.XmlPullParserException;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.io.*;
import java.net.MalformedURLException;
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
	
	public enum PairState {
		NOT_PAIRED,
		PAIRED,
		PIN_WRONG,
		FAILED
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
	
	private X509Certificate extractPlainCert(String text) throws XmlPullParserException, IOException, CertificateException
	{
		String certText = NvHTTP.getXmlString(text, "plaincert");
		byte[] certBytes = hexToBytes(certText);
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
	    return (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(certBytes));
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
	
	private static byte[] toSHA1Bytes(byte[] data) {
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
	
	private static boolean verifySignature(byte[] data, byte[] signature, Certificate cert) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		Signature sig = Signature.getInstance("SHA256withRSA");
		sig.initVerify(cert.getPublicKey());
		sig.update(data);
		return sig.verify(signature);
	}
	
	private static byte[] signData(byte[] data, PrivateKey key) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
		Signature sig = Signature.getInstance("SHA256withRSA");
		sig.initSign(key);
		sig.update(data);
		byte[] signature = new byte[256];
		sig.sign(signature, 0, signature.length);
		return signature;
	}
	
	private static byte[] decryptAes(byte[] encryptedData, SecretKey secretKey) throws NoSuchAlgorithmException, SignatureException,
	InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException {
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		
		int blockRoundedSize = ((encryptedData.length + 15) / 16) * 16;
		byte[] blockRoundedEncrypted = Arrays.copyOf(encryptedData, blockRoundedSize);
		byte[] fullDecrypted = new byte[blockRoundedSize];

		cipher.init(Cipher.DECRYPT_MODE, secretKey);
		cipher.doFinal(blockRoundedEncrypted, 0,
				blockRoundedSize, fullDecrypted);
		return fullDecrypted;
	}
	
	private static byte[] encryptAes(byte[] data, SecretKey secretKey) throws NoSuchAlgorithmException, SignatureException,
	InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException {
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		
		int blockRoundedSize = ((data.length + 15) / 16) * 16;
		byte[] blockRoundedData = Arrays.copyOf(data, blockRoundedSize);
		
		cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		return cipher.doFinal(blockRoundedData);
	}
	
	private static SecretKey generateAesKey(byte[] keyData) {
		byte[] aesTruncated = Arrays.copyOf(toSHA1Bytes(keyData), 16);
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
	
	public PairState getPairState(String serverInfo) throws MalformedURLException, IOException, XmlPullParserException  {
		if (!NvHTTP.getXmlString(serverInfo, "PairStatus").equals("1")) {
			return PairState.NOT_PAIRED;
		}
		
		return PairState.PAIRED;
	}
	
	public PairState pair(String uniqueId, String pin) throws MalformedURLException, IOException, XmlPullParserException, CertificateException, InvalidKeyException, NoSuchAlgorithmException, SignatureException, ShortBufferException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException {
		// Generate a salt for hashing the PIN
		byte[] salt = generateRandomBytes(16);

		// Combine the salt and pin, then create an AES key from them
		byte[] saltAndPin = saltPin(salt, pin);
		aesKey = generateAesKey(saltAndPin);
		
		// Send the salt and get the server cert. This doesn't have a read timeout
		// because the user must enter the PIN before the server responds
		String getCert = http.openHttpConnectionToString(http.baseUrl +
				"/pair?uniqueid="+uniqueId+"&devicename=roth&updateState=1&phrase=getservercert&salt="+
				bytesToHex(salt)+"&clientcert="+bytesToHex(pemCertBytes),
				false);
		if (!NvHTTP.getXmlString(getCert, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrl + "/unpair?uniqueid="+uniqueId, true);
			return PairState.FAILED;
		}
		X509Certificate serverCert = extractPlainCert(getCert);
		
		// Generate a random challenge and encrypt it with our AES key
		byte[] randomChallenge = generateRandomBytes(16);
		byte[] encryptedChallenge = encryptAes(randomChallenge, aesKey);
		
		// Send the encrypted challenge to the server
		String challengeResp = http.openHttpConnectionToString(http.baseUrl + 
				"/pair?uniqueid="+uniqueId+"&devicename=roth&updateState=1&clientchallenge="+bytesToHex(encryptedChallenge),
				true);
		if (!NvHTTP.getXmlString(challengeResp, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrl + "/unpair?uniqueid="+uniqueId, true);
			return PairState.FAILED;
		}
		
		// Decode the server's response and subsequent challenge
		byte[] encServerChallengeResponse = hexToBytes(NvHTTP.getXmlString(challengeResp, "challengeresponse"));
		byte[] decServerChallengeResponse = decryptAes(encServerChallengeResponse, aesKey);
		
		byte[] serverResponse = Arrays.copyOfRange(decServerChallengeResponse, 0, 20);
		byte[] serverChallenge = Arrays.copyOfRange(decServerChallengeResponse, 20, 36);
		
		// Using another 16 bytes secret, compute a challenge response hash using the secret, our cert sig, and the challenge
		byte[] clientSecret = generateRandomBytes(16);
		byte[] challengeRespHash = toSHA1Bytes(concatBytes(concatBytes(serverChallenge, cert.getSignature()), clientSecret));
		byte[] challengeRespEncrypted = encryptAes(challengeRespHash, aesKey);
		String secretResp = http.openHttpConnectionToString(http.baseUrl +
				"/pair?uniqueid="+uniqueId+"&devicename=roth&updateState=1&serverchallengeresp="+bytesToHex(challengeRespEncrypted),
				true);
		if (!NvHTTP.getXmlString(secretResp, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrl + "/unpair?uniqueid="+uniqueId, true);
			return PairState.FAILED;
		}
		
		// Get the server's signed secret
		byte[] serverSecretResp = hexToBytes(NvHTTP.getXmlString(secretResp, "pairingsecret"));
		byte[] serverSecret = Arrays.copyOfRange(serverSecretResp, 0, 16);
		byte[] serverSignature = Arrays.copyOfRange(serverSecretResp, 16, 272);

		// Ensure the authenticity of the data
		if (!verifySignature(serverSecret, serverSignature, serverCert)) {
			// Cancel the pairing process
			http.openHttpConnectionToString(http.baseUrl + "/unpair?uniqueid="+uniqueId, true);
			
			// Looks like a MITM
			return PairState.FAILED;
		}
		
		// Ensure the server challenge matched what we expected (aka the PIN was correct)
		byte[] serverChallengeRespHash = toSHA1Bytes(concatBytes(concatBytes(randomChallenge, serverCert.getSignature()), serverSecret));
		if (!Arrays.equals(serverChallengeRespHash, serverResponse)) {
			// Cancel the pairing process
			http.openHttpConnectionToString(http.baseUrl + "/unpair?uniqueid="+uniqueId, true);
			
			// Probably got the wrong PIN
			return PairState.PIN_WRONG;
		}
		
		// Send the server our signed secret
		byte[] clientPairingSecret = concatBytes(clientSecret, signData(clientSecret, pk));
		String clientSecretResp = http.openHttpConnectionToString(http.baseUrl + 
				"/pair?uniqueid="+uniqueId+"&devicename=roth&updateState=1&clientpairingsecret="+bytesToHex(clientPairingSecret),
				true);
		if (!NvHTTP.getXmlString(clientSecretResp, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrl + "/unpair?uniqueid="+uniqueId, true);
			return PairState.FAILED;
		}
		
		// Do the initial challenge (seems neccessary for us to show as paired)
		String pairChallenge = http.openHttpConnectionToString(http.baseUrl +
				"/pair?uniqueid="+uniqueId+"&devicename=roth&updateState=1&phrase=pairchallenge", true);
		if (!NvHTTP.getXmlString(pairChallenge, "paired").equals("1")) {
			http.openHttpConnectionToString(http.baseUrl + "/unpair?uniqueid="+uniqueId, true);
			return PairState.FAILED;
		}
		
		return PairState.PAIRED;
	}
}
