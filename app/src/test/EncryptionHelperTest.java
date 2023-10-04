package com.test

import org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import java.nio.charset.Charset;
import java.util.*;

class EncryptionHelperTest {

    private lateinit var validData: String
    private lateinit var encryptedData: String

    @Before
    fun setup() {
        // Setup: Generate a random string to ensure the encryption and decryption process is working with different inputs
        val byteArray = ByteArray(16)
        Random().nextBytes(byteArray)
        validData = String(byteArray, Charset.forName("UTF-8"))
        encryptedData = EncryptionHelper.encrypt(validData)
    }

    @Test
    fun decrypt_validInput_successful() {
        // Execute: Decrypt the data
        val decryptedData = EncryptionHelper.decrypt(encryptedData)

        // Verify: The decrypted data should match the original data
        assertEquals(validData, decryptedData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decrypt_invalidInput_throwsException() {
        // Setup: Create an invalid encrypted data for testing
        val invalidData = ""

        // Execute: Attempt to decrypt the data, expecting an exception to be thrown
        EncryptionHelper.decrypt(invalidData)
    }

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun decrypt_corruptedData_throwsAEADBadTagException() {
        // Setup: Corrupt the encrypted data by altering a few bytes
        val corruptedData = encryptedData.toByteArray().apply {
            this[0] = this[0].inc()
            this[1] = this[1].dec()
        }.toString(Charsets.UTF_8)

        // Execute: Attempt to decrypt the data, expecting an AEADBadTagException to be thrown
        EncryptionHelper.decrypt(corruptedData)
    }

    @Test
    fun encrypt_validInput_successful() {
        // Execute: Encrypt the data
        val newlyEncryptedData = EncryptionHelper.encrypt(validData)

        // Verify: The newly encrypted data should not be null and should be different from the original data
        assertNotNull(newlyEncryptedData)
        assertNotEquals(validData, newlyEncryptedData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encrypt_emptyInput_throwsException() {
        // Setup: Create an empty data for testing
        val emptyData = ""

        // Execute: Attempt to encrypt the data, expecting an exception to be thrown
        EncryptionHelper.encrypt(emptyData)
    }
}
