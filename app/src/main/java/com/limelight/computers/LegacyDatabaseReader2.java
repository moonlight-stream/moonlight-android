package com.limelight.computers;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

public class LegacyDatabaseReader2 {
    private static final String COMPUTER_DB_NAME = "computers2.db";
    private static final String COMPUTER_TABLE_NAME = "Computers";

    private static ComputerDetails getComputerFromCursor(Cursor c) {
        ComputerDetails details = new ComputerDetails();

        details.uuid = c.getString(0);
        details.name = c.getString(1);
        details.localAddress = new ComputerDetails.AddressTuple(c.getString(2), NvHTTP.DEFAULT_HTTP_PORT);
        details.remoteAddress = new ComputerDetails.AddressTuple(c.getString(3), NvHTTP.DEFAULT_HTTP_PORT);
        details.manualAddress = new ComputerDetails.AddressTuple(c.getString(4), NvHTTP.DEFAULT_HTTP_PORT);
        details.macAddress = c.getString(5);

        // This column wasn't always present in the old schema
        if (c.getColumnCount() >= 7) {
            try {
                byte[] derCertData = c.getBlob(6);

                if (derCertData != null) {
                    details.serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                            .generateCertificate(new ByteArrayInputStream(derCertData));
                }
            } catch (CertificateException e) {
                e.printStackTrace();
            }
        }

        // This signifies we don't have dynamic state (like pair state)
        details.state = ComputerDetails.State.UNKNOWN;

        return details;
    }

    public static List<ComputerDetails> getAllComputers(SQLiteDatabase computerDb) {
        try (final Cursor c = computerDb.rawQuery("SELECT * FROM "+COMPUTER_TABLE_NAME, null)) {
            LinkedList<ComputerDetails> computerList = new LinkedList<>();
            while (c.moveToNext()) {
                ComputerDetails details = getComputerFromCursor(c);

                // If a critical field is corrupt or missing, skip the database entry
                if (details.uuid == null) {
                    continue;
                }

                computerList.add(details);
            }

            return computerList;
        }
    }

    public static List<ComputerDetails> migrateAllComputers(Context c) {
        try (final SQLiteDatabase computerDb = SQLiteDatabase.openDatabase(
                c.getDatabasePath(COMPUTER_DB_NAME).getPath(),
                null, SQLiteDatabase.OPEN_READONLY)
        ) {
            // Open the existing database
            return getAllComputers(computerDb);
        } catch (SQLiteException e) {
            return new LinkedList<ComputerDetails>();
        } finally {
            // Close and delete the old DB
            c.deleteDatabase(COMPUTER_DB_NAME);
        }
    }
}