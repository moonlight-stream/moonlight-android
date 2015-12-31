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
        computerDb.delete(COMPUTER_TABLE_NAME, COMPUTER_NAME_COLUMN_NAME+"='"+name+"'", null);
    }

    public boolean updateComputer(ComputerDetails details) {
        ContentValues values = new ContentValues();
        values.put(COMPUTER_NAME_COLUMN_NAME, details.name);
        values.put(COMPUTER_UUID_COLUMN_NAME, details.uuid.toString());
        values.put(LOCAL_IP_COLUMN_NAME, details.localIp.getAddress());
        values.put(REMOTE_IP_COLUMN_NAME, details.remoteIp.getAddress());
        values.put(MAC_COLUMN_NAME, details.macAddress);
        return -1 != computerDb.insertWithOnConflict(COMPUTER_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<ComputerDetails> getAllComputers() {
        Cursor c = computerDb.rawQuery("SELECT * FROM "+COMPUTER_TABLE_NAME, null);
        LinkedList<ComputerDetails> computerList = new LinkedList<ComputerDetails>();
        while (c.moveToNext()) {
            ComputerDetails details = new ComputerDetails();

            details.name = c.getString(0);

            String uuidStr = c.getString(1);
            try {
                details.uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                // We'll delete this entry
                LimeLog.severe("DB: Corrupted UUID for "+details.name);
            }

            try {
                details.localIp = InetAddress.getByAddress(c.getBlob(2));
            } catch (UnknownHostException e) {
                // We'll delete this entry
                LimeLog.severe("DB: Corrupted local IP for "+details.name);
            }

            try {
                details.remoteIp = InetAddress.getByAddress(c.getBlob(3));
            } catch (UnknownHostException e) {
                // We'll delete this entry
                LimeLog.severe("DB: Corrupted remote IP for "+details.name);
            }

            details.macAddress = c.getString(4);

            // This signifies we don't have dynamic state (like pair state)
            details.state = ComputerDetails.State.UNKNOWN;
            details.reachability = ComputerDetails.Reachability.UNKNOWN;

            // If a field is corrupt or missing, skip the database entry
            if (details.uuid == null || details.localIp == null || details.remoteIp == null ||
                    details.macAddress == null) {
                continue;
            }


            computerList.add(details);
        }

        c.close();

        return computerList;
    }

    public ComputerDetails getComputerByName(String name) {
        Cursor c = computerDb.rawQuery("SELECT * FROM "+COMPUTER_TABLE_NAME+" WHERE "+COMPUTER_NAME_COLUMN_NAME+"='"+name+"'", null);
        ComputerDetails details = new ComputerDetails();
        if (!c.moveToFirst()) {
            // No matching computer
            c.close();
            return null;
        }

        details.name = c.getString(0);

        String uuidStr = c.getString(1);
        try {
            details.uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            // We'll delete this entry
            LimeLog.severe("DB: Corrupted UUID for "+details.name);
        }

        try {
            details.localIp = InetAddress.getByAddress(c.getBlob(2));
        } catch (UnknownHostException e) {
            // We'll delete this entry
            LimeLog.severe("DB: Corrupted local IP for "+details.name);
        }

        try {
            details.remoteIp = InetAddress.getByAddress(c.getBlob(3));
        } catch (UnknownHostException e) {
            // We'll delete this entry
            LimeLog.severe("DB: Corrupted remote IP for "+details.name);
        }

        details.macAddress = c.getString(4);

        c.close();

        details.state = ComputerDetails.State.UNKNOWN;
        details.reachability = ComputerDetails.Reachability.UNKNOWN;

        // If a field is corrupt or missing, delete the database entry
        if (details.uuid == null || details.localIp == null || details.remoteIp == null ||
                details.macAddress == null) {
            deleteComputer(details.name);
            return null;
        }

        return details;
    }
}
