package com.limelight.computers;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import com.limelight.nvstream.http.ComputerDetails;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

public class ComputerDatabaseManager {
    private static final String COMPUTER_DB_NAME = "computers3.db";
    private static final String COMPUTER_TABLE_NAME = "Computers";
    private static final String COMPUTER_UUID_COLUMN_NAME = "UUID";
    private static final String COMPUTER_NAME_COLUMN_NAME = "ComputerName";
    private static final String ADDRESSES_COLUMN_NAME = "Addresses";
    private static final String MAC_ADDRESS_COLUMN_NAME = "MacAddress";
    private static final String SERVER_CERT_COLUMN_NAME = "ServerCert";

    private static final char ADDRESS_DELIMITER = ';';

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

        StringBuilder addresses = new StringBuilder();
        addresses.append(details.localAddress != null ? details.localAddress : "");
        addresses.append(ADDRESS_DELIMITER).append(details.remoteAddress != null ? details.remoteAddress : "");
        addresses.append(ADDRESS_DELIMITER).append(details.manualAddress != null ? details.manualAddress : "");
        addresses.append(ADDRESS_DELIMITER).append(details.ipv6Address != null ? details.ipv6Address : "");

        values.put(ADDRESSES_COLUMN_NAME, addresses.toString());
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

        String[] addresses = c.getString(2).split(""+ADDRESS_DELIMITER, -1);

        details.localAddress = readNonEmptyString(addresses[0]);
        details.remoteAddress = readNonEmptyString(addresses[1]);
        details.manualAddress = readNonEmptyString(addresses[2]);
        details.ipv6Address = readNonEmptyString(addresses[3]);

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
        Cursor c = computerDb.rawQuery("SELECT * FROM "+COMPUTER_TABLE_NAME, null);
        LinkedList<ComputerDetails> computerList = new LinkedList<>();
        while (c.moveToNext()) {
            computerList.add(getComputerFromCursor(c));
        }

        c.close();

        return computerList;
    }

    public ComputerDetails getComputerByUUID(String uuid) {
        Cursor c = computerDb.query(COMPUTER_TABLE_NAME, null, COMPUTER_UUID_COLUMN_NAME+"=?", new String[]{ uuid }, null, null, null);
        if (!c.moveToFirst()) {
            // No matching computer
            c.close();
            return null;
        }

        ComputerDetails details = getComputerFromCursor(c);
        c.close();

        return details;
    }
}
