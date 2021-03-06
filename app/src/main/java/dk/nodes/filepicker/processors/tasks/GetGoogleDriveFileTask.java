package dk.nodes.filepicker.processors.tasks;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import dk.nodes.filepicker.BuildConfig;
import dk.nodes.filepicker.uriHelper.FilePickerUriHelper;
import dk.nodes.filepicker.utils.Logger;

/**
 * Created by Nicolaj on 15-10-2015.
 */
public class GetGoogleDriveFileTask extends AsyncTask<Void, String, String> {
    private static final String TAG = GetGoogleDriveFileTask.class.getSimpleName();

    private Context context;
    private Uri uri;
    private TaskListener listener;

    public GetGoogleDriveFileTask(Context context, Uri uri, TaskListener listener) {
        this.context = context;
        this.uri = uri;
        this.listener = listener;
    }

    @Override
    protected String doInBackground(Void... params) {
        Log.i(TAG, "Google drive: " + uri.toString());

        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        File outputFile = null;
        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {

                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                String displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                Log.i(TAG, "Display Name: " + displayName);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                // If the size is unknown, the value stored is null.  But since an
                // int can't be null in Java, the behavior is implementation-specific,
                // which is just a fancy term for "unpredictable".  So as
                // a rule, check if it's null before assigning to an int.  This will
                // happen often:  The storage API allows for remote files, whose
                // size might not be locally known.
                String size = null;
                if (!cursor.isNull(sizeIndex)) {
                    // Technically the column stores an int, but cursor.getString()
                    // will do the conversion automatically.
                    size = cursor.getString(sizeIndex);
                } else {
                    size = "Unknown";
                }
                Log.i(TAG, "Size: " + size);
                //outputFile = new File(Environment.getExternalStorageDirectory(), displayName.replace(" ","_"));

                outputFile = new File(Environment.getExternalStorageDirectory(), displayName);
                OutputStream os = new FileOutputStream(outputFile);

                InputStream is = null;
                if(FilePickerUriHelper.isVirtualFile(context, uri))
                {
                    Logger.logd(TAG, "File is virtual, commencing virtual file procedures");
                    try {
                        is = FilePickerUriHelper.getInputStreamForVirtualFile(context, uri, "*/*");
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                        throw(new FileNotFoundException("Could not get file descriptor for virtual file"));
                    }
                }
                else {
                    Logger.loge(TAG, "Looks like we got ourselves a regular ole' file");
                    is = context.getContentResolver().openInputStream(uri);
                }

                final int bufferSize = 1024;
                try {
                    byte[] bytes = new byte[bufferSize];
                    while (true) {
                        int count = is.read(bytes, 0, bufferSize);
                        if (count == - 1) {
                            break;
                        }
                        os.write(bytes, 0, count);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Logger.loge(TAG, ex.toString());
                }

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Logger.loge(TAG, e.toString());
        } finally {
            if(cursor != null)
                cursor.close();

        }
        return Uri.fromFile(outputFile).toString();
    }

    @Override
    protected void onPostExecute(String s) {
        if( listener != null && context != null ) {
            if( s != null ) {
                listener.didSucceed(s);
            } else {
                listener.didFail();
            }
        }
    }

    public interface TaskListener{
        void didSucceed(String path);
        void didFail();
    }
}
