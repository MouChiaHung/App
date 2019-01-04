package com.ours.yours.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ImageView;

import com.orhanobut.logger.Logger;
import com.ours.yours.app.dao.FileHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

public class BitmapHelper {
    private static final int DEFAULT_BUFFER_SIZE = 1024*16;
    private static final int DEFAULT_TIMEOUT_MILLISEC = 15000;
    private static final int DEFAULT_QUALITY = 20;
    private static final int DEFAULT_REQUEST_LENGTH = -10;
    private static BitmapHelper INSTANCE = new BitmapHelper();

    /**
     *  a thread accessing this object to decode URL competes to others in race condition while reading while loop
     *  however, all of loops running for reading from stream referring to the URL breaks if either of them goes for cancel
     *  moreover, when any of loops completes reading, it updates "time_start" which is a global mark used to make sure
     *  there should be one task done consistently at least in a specific period, otherwise all of loops will break
     */
    private long time_start = 0;
    /**
     * all of bitmap helper decoding URL loops running on other different threads will break if either of them goes for cancel
     * resets when any gets the singleton reference of this object
     */
    private static boolean cancel = false;

    /**
     * true if other threads go ahead their racing for reading from stream on this object even one of them got time out break before
     */
    private boolean is_keep = false;

    public static BitmapHelper getInstance() {
        //return new BitmapHelper();
        cancel = false;
        return INSTANCE;
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    public void cancel(boolean now_to_cancel) {
        cancel = now_to_cancel;
    }

    /**
     * returns an immutable bitmap from the specified subset of the source
     * bitmap. The new bitmap may be the same object as source, or a copy may
     * have been made. It is initialized with the same density and color space
     * as the original bitmap
     */
    public Bitmap create(Bitmap old_bmp, int x, int y, int w, int h) {
        return Bitmap.createBitmap(old_bmp, x, y, w, h);
    }

    /**
     * returns a mutable bitmap with the specified width and height
     */
    public Bitmap create(int w, int h,  @NonNull Bitmap.Config config) {
        return Bitmap.createBitmap(w, h, config);
    }

    /**
     * returns a mutable bitmap with the specified width and height and pixels from the buffer
     * after this method returns, the current position of the buffer is updated:
     * the position is incremented by the number of elements read from the buffer
     * if need to read the bitmap from the buffer again you must first rewind the buffer
     */
    public Bitmap getFromGrayScaleBytes(int w, int h, byte[] buffer) {
        int index;
        byte[] pixels = new byte[w*h*4];
        int px;
        for (index = 0; index<w*h; index++) {
            px = (buffer[index] & 0xFF);
            // 8 bits to ARGB8888
            pixels[index++] = (byte) (px & 0xFF);
            pixels[index++] = (byte) (px & 0xFF);
            pixels[index++] = (byte) (px & 0xFF);
            pixels[index++] = (byte) 0xFF;
        }
        Bitmap bitmap = create(w, h,  Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        return bitmap;
    }

    public Bitmap getFromView(View view) {
        return null;
    }

    public Bitmap getFromImageView(ImageView view) {
        if (view == null) {
            Logger.e("!!! view is null");
            return null;
        }
        //return view.getDrawingCache();
        return ((BitmapDrawable)view.getDrawable()).getBitmap();
    }


    public File compressToExternalFile(String dir_name, String file_name, Bitmap bitmap, Context context) {
        if (bitmap == null) {
            Logger.e("!!! bitmap is null");
            return null;
        }
        File file_dest = FileHelper.getInstance().createExternalFile(dir_name, file_name, context);
        if (file_dest == null) {
            Logger.e("!!! file_dest is null");
            return null;
        }
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file_dest);
            bitmap.compress(Bitmap.CompressFormat.JPEG, DEFAULT_QUALITY, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e("!!! exception:" + e.getMessage());
            return null;
        }
        //MediaScannerConnection ...
        //MediaStore ...
        return file_dest;
    }

    public File compressToInternalFile(String dir_name, String file_name, Bitmap bitmap, Context context) {
        if (bitmap == null) {
            Logger.e("!!! bitmap is null");
            return null;
        }
        File file_dest = FileHelper.getInstance().createInternalFile(dir_name, file_name, context);
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file_dest);
            bitmap.compress(Bitmap.CompressFormat.JPEG, DEFAULT_QUALITY, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e("!!! exception:" + e.getMessage());
            return null;
        }
        return file_dest;
    }

    public byte[] compressToBytes(Bitmap bitmap, int request_len) {
        if (bitmap == null) {
            Logger.e("!!! bitmap is null");
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int quality = 100;
        if (request_len == DEFAULT_REQUEST_LENGTH) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, DEFAULT_QUALITY, bos);
        } else {
            do {
                bos.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos);
                quality -= 10;
                if (quality <= 0) break;
            } while (bos.toByteArray().length > request_len);
        }
        return bos.toByteArray();
    }

    public Bitmap decodeFromExternalFile(String dir_name, String file_name, Context context) {
        Bitmap bitmap;
        File source = FileHelper.getInstance().getExternalFile(dir_name, file_name, context);
        if (source == null) {
            Logger.e("!!! source file is null");
            return null;
        }
        FileInputStream fis;
        try {
            fis = new FileInputStream(source);
            bitmap = BitmapFactory.decodeStream(fis);
            fis.close();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e("!!! exception:" + e.getMessage());
            return null;
        }
    }

    public Bitmap decodeFromInternalFile(String dir_name, String file_name, Context context) {
        Bitmap bitmap;
        File source = FileHelper.getInstance().getInternalFile(dir_name, file_name, context);
        if (source == null) {
            Logger.e("!!! source file is null");
            return null;
        }
        FileInputStream fis;
        try {
            fis = new FileInputStream(source);
            bitmap = BitmapFactory.decodeStream(fis);
            fis.close();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e("!!! exception:" + e.getMessage());
            return null;
        }
    }

    public Bitmap decodeFromUri(Uri uri, Context context) {
        Bitmap bitmap;
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(is);
            is.close();
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Bitmap decodeFromURL(final String http_url) {
        Bitmap bitmap;
        if (http_url == null) {
            Logger.e("!!! url is null");
            return null;
        }
        try {
            URL url = new URL(http_url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.connect();
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(is);
                connection.disconnect();
                return bitmap;
            } else {
                Logger.e("!!! connection response:" + connection.getResponseMessage());
                return null;
            }

        } catch (IOException e) {
            e.printStackTrace();
            Logger.e("!!! exception:" + e.getMessage());
            return null;
        }
    }

    public void decodeFromURL(final String http_url, OnHttpURLConnection listener) {
        Logger.d(">>>");
        Bitmap bitmap;
        int response;
        if (http_url == null) {
            Logger.e("!!! url is null");
            return;
        }
        try {
            URL url = new URL(http_url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            //connection.setReadTimeout(2000);
            connection.connect();
            response = connection.getResponseCode();
            switch (response) {
                case HttpURLConnection.HTTP_OK:
                    Logger.d("... HTTP_OK");
                    InputStream is = connection.getInputStream();
                    int len_of_content = connection.getContentLength();
                    int offset = 0;
                    int len_of_read = 0;
                    byte[] data = new byte[len_of_content];
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    time_start = System.currentTimeMillis();
                    while (true) {
                        if (cancel) {
                            Logger.e("!!! break due to cancel");
                            break;
                        }
                        if (System.currentTimeMillis() - time_start > DEFAULT_TIMEOUT_MILLISEC) {
                            if (is_keep) {
                                Logger.e("!!! break due to time out but still reset start time stamp");
                                time_start = System.currentTimeMillis();
                                break;
                            } else {
                                Logger.e("!!! break due to time out");
                                break;
                            }
                        }
                        len_of_read = is.read(buffer, 0, buffer.length);
                        if (len_of_read < 0) {
                            Logger.d("... break due to meet EOF");
                            break;
                        }
                        //Logger.d("... read length:" + len_of_read + " and offset:" + offset);
                        System.arraycopy(buffer, 0, data, offset, len_of_read);
                        offset += len_of_read;
                    }
                    if (offset != len_of_content) {
                        time_start = System.currentTimeMillis();
                        Logger.e("!!! download not complete" + " and offset:" + offset + " and length of content:" + len_of_content);
                        listener.onGet(null);
                        return;
                    } else {
                        time_start = System.currentTimeMillis();
                        Logger.d("... completed to download for " + len_of_content + " bytes");
                    }
                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    listener.onGet(bitmap);
                    connection.disconnect();
                    break;
                case java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT://504
                    Logger.e("!!! HTTP_GATEWAY_TIMEOUT");
                    break;
                case java.net.HttpURLConnection.HTTP_FORBIDDEN://403
                    Logger.e("!!! HTTP_FORBIDDEN");
                    break;
                case java.net.HttpURLConnection.HTTP_INTERNAL_ERROR://500
                    Logger.e("!!! HTTP_INTERNAL_ERROR");
                    break;
                case java.net.HttpURLConnection.HTTP_NOT_FOUND://404
                    Logger.e("!!! HTTP_NOT_FOUND");
                    break;
                case HttpURLConnection.HTTP_PAYMENT_REQUIRED://404
                    Logger.e("!!! HTTP_PAYMENT_REQUIRED");
                    break;
                default:
                    Logger.e("!!! response code:"+ response);
                    break;
            }
            connection.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
            Logger.e("!!! exception:" + e.getMessage());
            listener.onException(e);
        }
    }

    public Bitmap decodeFromBytes(byte[] source, int offset, int length) {
        Bitmap bitmap;
        if (source == null) {
            Logger.e("!!! source bytes is null");
            return null;
        }
        return BitmapFactory.decodeByteArray(source, offset, length);
    }

    public interface OnHttpURLConnection {
        void onConnect();
        void onGet(Bitmap bitmap);
        void onException(Exception e);
    }
}
