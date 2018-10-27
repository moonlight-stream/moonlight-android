package com.limelight.computers;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.limelight.LimeLog;
import com.limelight.nvstream.http.ComputerDetails;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class LegacyDatabaseReader {
    private static final String COMPUTER_DB_NAME = "computers.db";
    private static final String COMPUTER_TABLE_NAME = "Computers";

    private static final String ADDRESS_PREFIX = "ADDRESS_PREFIX__";

    private static ComputerDetails getComputerFromCursor(Cursor c) {
        ComputerDetails details = new ComputerDetails();

        details.name = c.getString(0);

        String uuidStr = c.getString(1);
        try {
            details.uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            // We'll delete this entry
            LimeLog.severe("DB: Corrupted UUID for " + details.name);
        }

        // An earlier schema defined addresses as byte blobs. We'll
        // gracefully migrate those to strings so we can store DNS names
        // too. To disambiguate, we'll need to prefix them with a string
        // greater than the allowable IP address length.
        try {
            details.localAddress = InetAddress.getByAddress(c.getBlob(2)).getHostAddress();
            LimeLog.warning("DB: Legacy local address for " + details.name);
        } catch (UnknownHostException e) {
            // This is probably a hostname/address with the prefix string
            String stringData = c.getString(2);
            if (stringData.startsWith(ADDRESS_PREFIX)) {
                details.localAddress = c.getString(2).substring(ADDRESS_PREFIX.length());
            } else {
                LimeLog.severe("DB: Corrupted local address for " + details.name);
            }
        }

        try {
            details.remoteAddress = InetAddress.getByAddress(c.getBlob(3)).getHostAddress();
            LimeLog.warning("DB: Legacy remote address for " + details.name);
        } catch (UnknownHostException e) {
            // This is probably a hostname/address with the prefix string
            String stringData = c.getString(3);
            if (stringData.startsWith(ADDRESS_PREFIX)) {
                details.remoteAddress = c.getString(3).substring(ADDRESS_PREFIX.length());
            } else {
                LimeLog.severe("DB: Corrupted remote address for " + details.name);
            }
        }

        // On older versions of Moonlight, this is typically where manual addresses got stored,
        // so let's initialize it just to be safe.
        details.manualAddress = details.remoteAddress;

        details.macAddress = c.getString(4);

        // This signifies we don't have dynamic state (like pair state)
        details.state = ComputerDetails.State.UNKNOWN;

        return details;
    }

    private static List<ComputerDetails> getAllComputers(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT * FROM " + COMPUTER_TABLE_NAME, null);
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

    public static List<ComputerDetails> migrateAllComputers(Context c) {
        SQLiteDatabase computerDb = null;
        try {
            // Open the existing database
            computerDb = SQLiteDatabase.openDatabase(c.getDatabasePath(COMPUTER_DB_NAME).getPath(), null, SQLiteDatabase.OPEN_READONLY);
            return getAllComputers(computerDb);
        } catch (SQLiteException e) {
            return new LinkedList<ComputerDetails>();
        } finally {
            // Close and delete the old DB
            if (computerDb != null) {
                computerDb.close();
            }
            c.deleteDatabase(COMPUTER_DB_NAME);
        }
    }
}