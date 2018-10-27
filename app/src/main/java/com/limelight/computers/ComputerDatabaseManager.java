package com.limelight.computers;

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
    private static final String COMPUTER_DB_NAME = "computers2.db";
    private static final String COMPUTER_TABLE_NAME = "Computers";
    private static final String COMPUTER_UUID_COLUMN_NAME = "UUID";
    private static final String COMPUTER_NAME_COLUMN_NAME = "ComputerName";
    private static final String LOCAL_ADDRESS_COLUMN_NAME = "LocalAddress";
    private static final String REMOTE_ADDRESS_COLUMN_NAME = "RemoteAddress";
    private static final String MANUAL_ADDRESS_COLUMN_NAME = "ManualAddress";
    private static final String MAC_ADDRESS_COLUMN_NAME = "MacAddress";

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
                "CREATE TABLE IF NOT EXISTS %s(%s TEXT PRIMARY KEY, %s TEXT NOT NULL, %s TEXT, %s TEXT, %s TEXT, %s TEXT)",
                COMPUTER_TABLE_NAME,
                COMPUTER_UUID_COLUMN_NAME, COMPUTER_NAME_COLUMN_NAME,
                LOCAL_ADDRESS_COLUMN_NAME, REMOTE_ADDRESS_COLUMN_NAME, MANUAL_ADDRESS_COLUMN_NAME,
                MAC_ADDRESS_COLUMN_NAME));

        // Move all computers from the old DB (if any) to the new one
        List<ComputerDetails> oldComputers = LegacyDatabaseReader.migrateAllComputers(c);
        for (ComputerDetails computer : oldComputers) {
            updateComputer(computer);
        }
    }

    public void deleteComputer(String name) {
        computerDb.delete(COMPUTER_TABLE_NAME, COMPUTER_NAME_COLUMN_NAME+"=?", new String[]{name});
    }

    public boolean updateComputer(ComputerDetails details) {
        ContentValues values = new ContentValues();
        values.put(COMPUTER_UUID_COLUMN_NAME, details.uuid.toString());
        values.put(COMPUTER_NAME_COLUMN_NAME, details.name);
        values.put(LOCAL_ADDRESS_COLUMN_NAME, details.localAddress);
        values.put(REMOTE_ADDRESS_COLUMN_NAME, details.remoteAddress);
        values.put(MANUAL_ADDRESS_COLUMN_NAME, details.manualAddress);
        values.put(MAC_ADDRESS_COLUMN_NAME, details.macAddress);
        return -1 != computerDb.insertWithOnConflict(COMPUTER_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private ComputerDetails getComputerFromCursor(Cursor c) {
        ComputerDetails details = new ComputerDetails();

        String uuidStr = c.getString(0);
        try {
            details.uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            // We'll delete this entry
            LimeLog.severe("DB: Corrupted UUID for "+details.name);
        }

        details.name = c.getString(1);
        details.localAddress = c.getString(2);
        details.remoteAddress = c.getString(3);
        details.manualAddress = c.getString(4);
        details.macAddress = c.getString(5);

        // This signifies we don't have dynamic state (like pair state)
        details.state = ComputerDetails.State.UNKNOWN;

        return details;
    }

    public List<ComputerDetails> getAllComputers() {
        Cursor c = computerDb.rawQuery("SELECT * FROM "+COMPUTER_TABLE_NAME, null);
        LinkedList<ComputerDetails> computerList = new LinkedList<>();
        while (c.moveToNext()) {
            ComputerDetails details = getComputerFromCursor(c);

            // If a critical field is corrupt or missing, skip the database entry
            if (details.uuid == null) {
                continue;
            }

            computerList.add(details);
        }

        c.close();

        return computerList;
    }

    public ComputerDetails getComputerByUUID(UUID uuid) {
        Cursor c = computerDb.query(COMPUTER_TABLE_NAME, null, COMPUTER_UUID_COLUMN_NAME+"=?", new String[]{ uuid.toString() }, null, null, null);
        if (!c.moveToFirst()) {
            // No matching computer
            c.close();
            return null;
        }

        ComputerDetails details = getComputerFromCursor(c);
        c.close();

        // If a critical field is corrupt or missing, delete the database entry
        if (details.uuid == null) {
            deleteComputer(details.name);
            return null;
        }

        return details;
    }
}
