package de.k3b.android.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import de.k3b.android.androFotoFinder.Global;
import de.k3b.android.androFotoFinder.R;
import de.k3b.android.androFotoFinder.queries.FotoSql;

/**
 * Since android.media.MediaScannerConnection does not work on my android-4.2
 * here is my own implementation.
 *
 * Created by k3b on 14.09.2015.
 */
public class MediaScanner extends AsyncTask<String,Object,Integer> {
    private static final String CONTEXT = "MediaScanner";
    private static SimpleDateFormat sFormatter;

    static {
        sFormatter = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
        sFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    protected final Context mContext;

    public MediaScanner(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    protected Integer doInBackground(String... pathNames) {
        return updateMediaDatabase_Android42(mContext, pathNames);
    }

    @Override
    protected void onPostExecute(Integer resultCount) {
        super.onPostExecute(resultCount);
        String message = this.mContext.getString(R.string.media_update_result, resultCount);
        Toast.makeText(this.mContext, message, Toast.LENGTH_LONG).show();
        if (Global.debugEnabled) {
            Log.i(Global.LOG_CONTEXT, CONTEXT + "A42 scanner finished: " + message);
        }
    }

    /** do not wait for result. Use buildIn scanner if a4.4 else own scanner. */
    public static void updateMediaDBInBackground(Context context, String[] pathNames) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // for android-4.4 and up use internal scanner
            MediaScanner.updateMediaDB_Androd44(context, pathNames);
        } else if (isGuiThread()) {
            // update_Android42 scanner in seperate background task for android-4.3 and below
            MediaScanner scanTask = new MediaScanner(context.getApplicationContext());
            scanTask.execute(pathNames);
        } else {
            // Continute in background task  for android-4.3 and below
            MediaScanner.updateMediaDatabase_Android42(context.getApplicationContext(), pathNames);
        }
    }

    public static boolean isGuiThread() {
        return (Looper.myLooper() == Looper.getMainLooper());
    }


    public static int updateMediaDatabase_Android42(Context context, String... pathNames) {
        if (Global.debugEnabled) {
            Log.i(Global.LOG_CONTEXT, CONTEXT + "A42 scanner starting with " + pathNames.length + " files " + pathNames[0] + "...");
        }

        // ignore non-jpeg
        for(int i = 0; i < pathNames.length; i++) {
            if (!MediaScanner.isJpeg(pathNames[i])) {
                pathNames[i] = null;
            }
        }

        Map<String, Integer> inMediaDb = FotoSql.execGetPathIdMap(context.getApplicationContext(), pathNames);

        int count = 0;
        for (String fileName : pathNames) {
            if (fileName != null) {
                Integer id = inMediaDb.get(fileName);
                if (id != null) {
                    // already exists
                    update_Android42(context, id, new File(fileName));
                } else {
                    insert_Android42(context, new File(fileName));
                }
                count++;
            }
        }

        return count;
    }

    /** updates values with current values of file */
    private static void getExifFromFile(ContentValues values, File file) {
        String absolutePath = file.getAbsolutePath();
        String title = values.getAsString(MediaStore.MediaColumns.TITLE);
        if (title == null || TextUtils.isEmpty(title.trim())) {
            title = generateTitleFromFilePath(absolutePath);
        }

        values.put(MediaStore.MediaColumns.DATA, absolutePath);
        values.put(MediaStore.MediaColumns.TITLE, title);
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, file.lastModified() / 1000);
        values.put(MediaStore.MediaColumns.SIZE, file.length());

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // only need with/height but not content
        BitmapFactory.decodeFile(absolutePath, options);
        int mHeight = options.outHeight;
        int mWidth = options.outWidth;
        if (mWidth > 0 && mHeight > 0) {
            values.put(MediaStore.MediaColumns.WIDTH, mWidth);
            values.put(MediaStore.MediaColumns.HEIGHT, mHeight);
        }
        String imageType = options.outMimeType;
        values.put(MediaStore.MediaColumns.MIME_TYPE, imageType);

        ExifInterface exif = null;
        try {
            exif = new ExifInterface(absolutePath);
        } catch (IOException ex) {
            // exif is null
        }

        if (exif != null) {
            float[] latlng = new float[2];
            if (exif.getLatLong(latlng)) {
                values.put(MediaStore.Images.Media.LATITUDE, latlng[0]);
                values.put(MediaStore.Images.Media.LONGITUDE, latlng[1]);
            }

            long time = getDateTime(exif);
            if (time != -1) {
                values.put(MediaStore.Images.Media.DATE_TAKEN, time);
            }

            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                // We only recognize a subset of orientation tag values.
                int degree;
                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degree = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degree = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degree = 270;
                        break;
                    default:
                        degree = 0;
                        break;
                }
                values.put(MediaStore.Images.Media.ORIENTATION, degree);
            }
        }
    }

    private static void update_Android42(Context context, Integer id, File file) {
        if ((file != null) && file.exists() && file.canRead()) {
            ContentValues values = new ContentValues();
            getExifFromFile(values, file);
            FotoSql.execUpdate(context, id, values);
        }
    }

    private static void insert_Android42(Context context, File file) {
        if ((file != null) && file.exists() && file.canRead()) {
            ContentValues values = new ContentValues();
            long now = new Date().getTime();
            values.put(MediaStore.Images.ImageColumns.DATE_ADDED, now / 1000);//sec

            getExifFromFile(values, file);
            FotoSql.execInsert(context, values);
        }
    }

    // generates a title based on file name
    public static String generateTitleFromFilePath(String filePath) {
        // extract file name after last slash
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0) {
            lastSlash++;
            if (lastSlash < filePath.length()) {
                filePath = filePath.substring(lastSlash);
            }
        }
        // truncate the file extension (if any)
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0) {
            filePath = filePath.substring(0, lastDot);
        }
        return filePath;
    }

    public static boolean isJpeg(String path) {
        String lcPath = path.toLowerCase();
        return lcPath.endsWith(".jpg") || lcPath.endsWith(".jpeg");
    }

    /**
     * Returns number of milliseconds since Jan. 1, 1970, midnight.
     * Returns -1 if the date time information if not available.
     * @hide
     */
    public static long getDateTime(ExifInterface exif) {
        String dateTimeString =  exif.getAttribute(ExifInterface.TAG_DATETIME);
        if (dateTimeString == null) return -1;

        ParsePosition pos = new ParsePosition(0);
        try {
            Date datetime = sFormatter.parse(dateTimeString, pos);
            if (datetime == null) return -1;
            return datetime.getTime();
        } catch (IllegalArgumentException ex) {
            return -1;
        }
    }

    /** update media db via android-s native scanner.
     * Requires android-4.4 and up to support single files
     * @param context
     * @param pathNames
     */
    public static void updateMediaDB_Androd44(Context context, String[] pathNames) {
        if (Global.debugEnabled) {
            Log.i(Global.LOG_CONTEXT, CONTEXT + "A44 scanner starting with " + pathNames.length + " files " + pathNames[0] + "...");
        }

        // this only works in android-4.4 and up but not below
        MediaScannerConnection.scanFile(
                // http://stackoverflow.com/questions/5739140/mediascannerconnection-produces-android-app-serviceconnectionleaked
                context.getApplicationContext(),
                pathNames, // mPathNames.toArray(new String[mPathNames.size()]),
                null, null);
    }

    // http://stackoverflow.com/questions/12136681/detect-if-media-scanner-running-on-android
    public static boolean isScannerActive(ContentResolver cr) {
        boolean result = false;
        Cursor cursor = null;

        try {
            cursor = cr.query(MediaStore.getMediaScannerUri(),
                    new String[]{MediaStore.MEDIA_SCANNER_VOLUME},
                    null, null, null);
            if (cursor != null) {
                if (cursor.getCount() == 1) {
                    cursor.moveToFirst();
                    result = "external".equals(cursor.getString(0));
                }
            }
            return result;
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}