package com.limelight.computers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.limelight.LimeLog;
import com.limelight.nvstream.http.ComputerDetails;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

public class ComputerDatabaseManager {
    private static final String COMPUTER_DB_NAME = "computers.db";
    private static final String COMPUTER_TABLE_NAME = "Computers";
    private static final String COMPUTER_NAME_COLUMN_NAME = "ComputerName";
    private static final String COMPUTER_UUID_COLUMN_NAME = "UUID";
    private static final String LOCAL_IP_COLUMN_NAME = "LocalIp";
    private static final String REMOTE_IP_COLUMN_NAME = "RemoteIp";
    private static final String MAC_COLUMN_NAME = "Mac";

    private static final String ADDRESS_PREFIX = "ADDRESS_PREFIX__";

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
        initializeDb();
    }

    public void close() {
        computerDb.close();
    }

    private void initializeDb() {
        // Create tables if they aren't already there
        computerDb.execSQL(String.format((Locale)null, "CREATE TABLE IF NOT EXISTS %s(%s TEXT PRIMARY KEY," +
                " %s TEXT NOT NULL, %s TEXT NOT NULL, %s TEXT NOT NULL, %s TEXT NOT NULL)",
                COMPUTER_TABLE_NAME,
                COMPUTER_NAME_COLUMN_NAME, COMPUTER_UUID_COLUMN_NAME, LOCAL_IP_COLUMN_NAME,
                REMOTE_IP_COLUMN_NAME, MAC_COLUMN_NAME));
    }

    public void deleteComputer(String name) {
        computerDb.delete(COMPUTER_TABLE_NAME, COMPUTER_NAME_COLUMN_NAME+"=?", new String[]{name});
    }

    public boolean updateComputer(ComputerDetails details) {
        ContentValues values = new ContentValues();
        values.put(COMPUTER_NAME_COLUMN_NAME, details.name);
        values.put(COMPUTER_UUID_COLUMN_NAME, details.uuid.toString());
        values.put(LOCAL_IP_COLUMN_NAME, ADDRESS_PREFIX+details.localAddress);
        values.put(REMOTE_IP_COLUMN_NAME, ADDRESS_PREFIX+details.remoteAddress);
        values.put(MAC_COLUMN_NAME, details.macAddress);
        return -1 != computerDb.insertWithOnConflict(COMPUTER_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private ComputerDetails getComputerFromCursor(Cursor c) {
        ComputerDetails details = new ComputerDetails();

        details.name = c.getString(0);

        String uuidStr = c.getString(1);
        try {
            details.uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            // We'll delete this entry
            LimeLog.severe("DB: Corrupted UUID for "+details.name);
        }

        // An earlier schema defined addresses as byte blobs. We'll
        // gracefully migrate those to strings so we can store DNS names
        // too. To disambiguate, we'll need to prefix them with a string
        // greater than the allowable IP address length.
        try {
            details.localAddress = InetAddress.getByAddress(c.getBlob(2)).getHostAddress();
            LimeLog.warning("DB: Legacy local address for "+details.name);
        } catch (UnknownHostException e) {
            // This is probably a hostname/address with the prefix string
            String stringData = c.getString(2);
            if (stringData.startsWith(ADDRESS_PREFIX)) {
                details.localAddress = c.getString(2).substring(ADDRESS_PREFIX.length());
            }
            else {
                LimeLog.severe("DB: Corrupted local address for "+details.name);
            }
        }

        try {
            details.remoteAddress = InetAddress.getByAddress(c.getBlob(3)).getHostAddress();
            LimeLog.warning("DB: Legacy remote address for "+details.name);
        } catch (UnknownHostException e) {
            // This is probably a hostname/address with the prefix string
            String stringData = c.getString(3);
            if (stringData.startsWith(ADDRESS_PREFIX)) {
                details.remoteAddress = c.getString(3).substring(ADDRESS_PREFIX.length());
            }
            else {
                LimeLog.severe("DB: Corrupted local address for "+details.name);
            }
        }

        details.macAddress = c.getString(4);

        // This signifies we don't have dynamic state (like pair state)
        details.state = ComputerDetails.State.UNKNOWN;
        details.reachability = ComputerDetails.Reachability.UNKNOWN;

        return details;
    }

    public List<ComputerDetails> getAllComputers() {
        Cursor c = computerDb.rawQuery("SELECT * FROM "+COMPUTER_TABLE_NAME, null);
        LinkedList<ComputerDetails> computerList = new LinkedList<>();
        while (c.moveToNext()) {
            ComputerDetails details = getComputerFromCursor(c);

            // If a field is corrupt or missing, skip the database entry
            if (details.uuid == null || details.localAddress == null || details.remoteAddress == null ||
                    details.macAddress == null) {
                continue;
            }


            computerList.add(details);
        }

        c.close();

        return computerList;
    }

    public ComputerDetails getComputerByName(String name) {
        Cursor c = computerDb.query(COMPUTER_TABLE_NAME, null, COMPUTER_NAME_COLUMN_NAME+"=?", new String[]{name}, null, null, null);
        if (!c.moveToFirst()) {
            // No matching computer
            c.close();
            return null;
        }

        ComputerDetails details = getComputerFromCursor(c);
        c.close();

        // If a field is corrupt or missing, delete the database entry
        if (details.uuid == null || details.localAddress == null || details.remoteAddress == null ||
                details.macAddress == null) {
            deleteComputer(details.name);
            return null;
        }

        return details;
    }
}
