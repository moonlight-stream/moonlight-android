package com.limelight.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

public class CacheHelper {
    private static File openPath(boolean createPath, File root, String... path) {
        File f = root;
        for (int i = 0; i < path.length; i++) {
            String component = path[i];

            if (i == path.length - 1) {
                // This is the file component so now we create parent directories
                if (createPath) {
                    f.mkdirs();
                }
            }

            f = new File(f, component);
        }
        return f;
    }

    public static boolean cacheFileExists(File root, String... path) {
        return openPath(false, root, path).exists();
    }

    public static InputStream openCacheFileForInput(File root, String... path) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(openPath(false, root, path)));
    }

    public static OutputStream openCacheFileForOutput(File root, String... path) throws FileNotFoundException {
        return new BufferedOutputStream(new FileOutputStream(openPath(true, root, path)));
    }

    public static void writeInputStreamToOutputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[4096];
        int bytesRead;

        while ((bytesRead = in.read(buf)) != -1) {
            out.write(buf, 0, bytesRead);
        }
    }

    public static String readInputStreamToString(InputStream in) throws IOException {
        Reader r = new InputStreamReader(in);

        StringBuilder sb = new StringBuilder();
        char[] buf = new char[256];
        int bytesRead;
        while ((bytesRead = r.read(buf)) != -1) {
            sb.append(buf, 0, bytesRead);
        }

        try {
            in.close();
        } catch (IOException ignored) {}

        return sb.toString();
    }

    public static void writeStringToOutputStream(OutputStream out, String str) throws IOException {
        out.write(str.getBytes("UTF-8"));
    }
}
