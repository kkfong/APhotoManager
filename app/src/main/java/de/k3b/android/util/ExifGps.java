package de.k3b.android.util;

import android.media.ExifInterface;

import java.io.File;
import java.io.IOException;

/**
 * Based on http://stackoverflow.com/questions/5280479/how-to-save-gps-coordinates-in-exif-data-on-android
 * Created by k3b on 25.08.2015.
 */
public class ExifGps {
    public static void saveLatLon(File filePath, double latitude, double longitude) {
        try {
            ExifInterface exif = new ExifInterface(filePath.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convert(latitude));
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitudeRef(latitude));
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convert(longitude));
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitudeRef(longitude));
            exif.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * returns ref for latitude which is S or N.
     * @param latitude
     * @return S or N
     */
    private static String latitudeRef(double latitude) {
        return latitude<0.0d?"S":"N";
    }

    /**
     * returns ref for latitude which is S or N.
     * @param longitude
     * @return W or E
     */
    private static String longitudeRef(double longitude) {
        return longitude<0.0d?"W":"E";
    }

    /**
     * convert latitude into DMS (degree minute second) format. For instance<br/>
     * -79.948862 becomes<br/>
     *  79/1,56/1,55903/1000<br/>
     * It works for latitude and longitude<br/>
     * @param latitude could be longitude.
     * @return
     */
    private static final String convert(double latitude) {
        latitude=Math.abs(latitude);
        int degree = (int) latitude;
        latitude *= 60;
        latitude -= (degree * 60.0d);
        int minute = (int) latitude;
        latitude *= 60;
        latitude -= (minute * 60.0d);
        int second = (int) (latitude*1000.0d);

        StringBuilder sb = new StringBuilder(20);
        sb.append(degree);
        sb.append("/1,");
        sb.append(minute);
        sb.append("/1,");
        sb.append(second);
        sb.append("/1000,");
        return sb.toString();
    }
}
