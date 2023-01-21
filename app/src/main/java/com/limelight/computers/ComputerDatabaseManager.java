package com.limelight.computers;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import com.limelight.LimeLog;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvHTTP;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ComputerDatabaseManager {
    private static final String COMPUTER_DB_NAME = "computers3.db";
    private static final String COMPUTER_TABLE_NAME = "Computers";
    private static final String COMPUTER_UUID_COLUMN_NAME = "UUID";
    private static final String COMPUTER_NAME_COLUMN_NAME = "ComputerName";
    private static final String ADDRESSES_COLUMN_NAME = "Addresses";
    private static final String MAC_ADDRESS_COLUMN_NAME = "MacAddress";
    private static final String SERVER_CERT_COLUMN_NAME = "ServerCert";

    private SQLiteDatabase computerDb;

    public ComputerDatabaseManager(Context c) {
        try {
            // Create or open an existing DB
            computerDb = c.openOrCreateDatabase(COMPUTER_DB_NAME, 0, null);
        } catch (SQLiteException e) {
            // Delete the DB and try again
            c.deleteDatabase(COMPUTER_DB_NAME);
            computerDb = c.openOrCreateDatabase(COMPUTER_DB_NAME, 0, null);
        }
        initializeDb(c);
    }

    public void close() {
        computerDb.close();
    }

    private void initializeDb(Context c) {
        // Create tables if they aren't already there
        computerDb.execSQL(String.format((Locale)null,
                "CREATE TABLE IF NOT EXISTS %s(%s TEXT PRIMARY KEY, %s TEXT NOT NULL, %s TEXT NOT NULL, %s TEXT, %s TEXT)",
                COMPUTER_TABLE_NAME, COMPUTER_UUID_COLUMN_NAME, COMPUTER_NAME_COLUMN_NAME,
                ADDRESSES_COLUMN_NAME, MAC_ADDRESS_COLUMN_NAME, SERVER_CERT_COLUMN_NAME));

        // Move all computers from the old DB (if any) to the new one
        List<ComputerDetails> oldComputers = LegacyDatabaseReader.migrateAllComputers(c);
        for (ComputerDetails computer : oldComputers) {
            updateComputer(computer);
        }
        oldComputers = LegacyDatabaseReader2.migrateAllComputers(c);
        for (ComputerDetails computer : oldComputers) {
            updateComputer(computer);
        }
    }

    public void deleteComputer(ComputerDetails details) {
        computerDb.delete(COMPUTER_TABLE_NAME, COMPUTER_UUID_COLUMN_NAME+"=?", new String[]{details.uuid});
    }

    public boolean updateComputer(ComputerDetails details) {
        ContentValues values = new ContentValues();
        values.put(COMPUTER_UUID_COLUMN_NAME, details.uuid);
        values.put(COMPUTER_NAME_COLUMN_NAME, details.name);

        try {
            JSONObject addresses = new JSONObject();
            addresses.put("local", ComputerDetails.AddressTuple.toJson((details.localAddress)));
            addresses.put("remote", ComputerDetails.AddressTuple.toJson((details.remoteAddress)));
            addresses.put("manual", ComputerDetails.AddressTuple.toJson((details.manualAddress)));
            addresses.put("ipv6", ComputerDetails.AddressTuple.toJson((details.ipv6Address)));
            values.put(ADDRESSES_COLUMN_NAME, addresses.toString());
        } catch (JSONException e) {
            LimeLog.warning("JSON error, failed to write computer address information, " + e.getMessage());
        }

        values.put(MAC_ADDRESS_COLUMN_NAME, details.macAddress);
        try {
            if (details.serverCert != null) {
                values.put(SERVER_CERT_COLUMN_NAME, details.serverCert.getEncoded());
            }
            else {
                values.put(SERVER_CERT_COLUMN_NAME, (byte[])null);
            }
        } catch (CertificateEncodingException e) {
            values.put(SERVER_CERT_COLUMN_NAME, (byte[])null);
            e.printStackTrace();
        }
        return -1 != computerDb.insertWithOnConflict(COMPUTER_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String readNonEmptyString(String input) {
        if (input.isEmpty()) {
            return null;
        }

        return input;
    }

    private ComputerDetails getComputerFromCursor(Cursor c) {
        ComputerDetails details = new ComputerDetails();

        details.uuid = c.getString(0);
        details.name = c.getString(1);
        try {
            JSONObject addresses = new JSONObject(c.getString(2));
            details.localAddress = ComputerDetails.AddressTuple.fromJson(addresses.getJSONObject("local"));
            details.remoteAddress = ComputerDetails.AddressTuple.fromJson(addresses.getJSONObject("remote"));
            details.manualAddress = ComputerDetails.AddressTuple.fromJson(addresses.getJSONObject("manual"));
            details.ipv6Address = ComputerDetails.AddressTuple.fromJson(addresses.getJSONObject("ipv6"));
        } catch (JSONException e) {
            LimeLog.warning("JSON error, failed to read computer address information, " + e.getMessage());
         }

        // External port is persisted in the remote address field
        if (details.remoteAddress != null) {
            details.externalPort = details.remoteAddress.port;
        }
        else {
            details.externalPort = NvHTTP.DEFAULT_HTTP_PORT;
        }

        details.macAddress = c.getString(3);

        try {
            byte[] derCertData = c.getBlob(4);

            if (derCertData != null) {
                details.serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(derCertData));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        // This signifies we don't have dynamic state (like pair state)
        details.state = ComputerDetails.State.UNKNOWN;

        return details;
    }

    public List<ComputerDetails> getAllComputers() {
        try (final Cursor c = computerDb.rawQuery("SELECT * FROM "+COMPUTER_TABLE_NAME, null)) {
            LinkedList<ComputerDetails> computerList = new LinkedList<>();
            while (c.moveToNext()) {
                computerList.add(getComputerFromCursor(c));
            }
            return computerList;
        }
    }

    public ComputerDetails getComputerByUUID(String uuid) {
        try (final Cursor c = computerDb.query(
                COMPUTER_TABLE_NAME, null, COMPUTER_UUID_COLUMN_NAME+"=?",
                new String[]{ uuid }, null, null, null)
        ) {
            if (!c.moveToFirst()) {
                // No matching computer
                return null;
            }

            return getComputerFromCursor(c);
        }
    }
}
