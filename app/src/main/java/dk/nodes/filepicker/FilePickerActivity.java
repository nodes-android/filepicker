package dk.nodes.filepicker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import java.io.File;

import dk.nodes.filepicker.bitmapHelper.FilePickerBitmapHelper;
import dk.nodes.filepicker.intentHelper.FilePickerCameraIntent;
import dk.nodes.filepicker.intentHelper.FilePickerChooserIntent;
import dk.nodes.filepicker.intentHelper.FilePickerFileIntent;
import dk.nodes.filepicker.processors.UriProcessListener;
import dk.nodes.filepicker.processors.UriProcessor;
import dk.nodes.filepicker.utils.Logger;

import static dk.nodes.filepicker.BuildConfig.DEBUG;
import static dk.nodes.filepicker.FilePickerConstants.CAMERA;
import static dk.nodes.filepicker.FilePickerConstants.CHOOSER_TEXT;
import static dk.nodes.filepicker.FilePickerConstants.DOWNLOAD_IF_NON_LOCAL;
import static dk.nodes.filepicker.FilePickerConstants.FILE;
import static dk.nodes.filepicker.FilePickerConstants.MULTIPLE_TYPES;
import static dk.nodes.filepicker.FilePickerConstants.PERMISSION_REQUEST_CODE;
import static dk.nodes.filepicker.FilePickerConstants.REQUEST_CODE;
import static dk.nodes.filepicker.FilePickerConstants.RESULT_CODE_FAILURE;
import static dk.nodes.filepicker.FilePickerConstants.TYPE;
import static dk.nodes.filepicker.FilePickerConstants.URI;
import static dk.nodes.filepicker.permissionHelper.FilePickerPermissionHelper.askPermission;
import static dk.nodes.filepicker.permissionHelper.FilePickerPermissionHelper.requirePermission;

public class FilePickerActivity extends AppCompatActivity implements UriProcessListener {
    public static final String TAG = FilePickerActivity.class.getSimpleName();
    Uri outputFileUri;
    FrameLayout rootFl;
    String chooserText = "Choose an action";
    private boolean download = true;
    UriProcessor uriProcessor;
    String uriString = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_picker);
        rootFl = (FrameLayout) findViewById(R.id.rootFl);

        uriProcessor = new UriProcessor();

        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey(CHOOSER_TEXT)) {
            chooserText = getIntent().getStringExtra(CHOOSER_TEXT);
        }
        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey(DOWNLOAD_IF_NON_LOCAL)) {
            download = getIntent().getBooleanExtra(DOWNLOAD_IF_NON_LOCAL, true);
        }
        if (requirePermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)) {
            askPermission(this, PERMISSION_REQUEST_CODE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA);
        } else {
            start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions.length == 0 || grantResults.length == 0) {
            setResult(RESULT_FIRST_USER);
            finish();
            return;
        }
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if ((permissions[0].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    && (permissions[1].equals(Manifest.permission.CAMERA) && grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                start();
            } else {
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE) {
                if (data != null && data.getData() != null) {
                    uriString = data.getData().toString();
                } else if (outputFileUri != null) {
                    uriString = outputFileUri.toString();
                } else if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
                    uriString = data.getExtras().get("data").toString();
                    try {
                        File file = FilePickerBitmapHelper.writeBitmap(this, (Bitmap) data.getExtras().get("data"), false);
                        uriString = Uri.fromFile(file).toString();
                    } catch (Exception e) {
                        Logger.loge(TAG, e.toString());
                    }
                }

                if (uriString == null) {
                    setResult(RESULT_FIRST_USER);
                    finish();
                    return;
                }

                Logger.loge(TAG, "Original URI = " + uriString);

                Uri uri = Uri.parse(uriString);

                // Android 4.4 throws:
                // java.lang.SecurityException: Permission Denial: opening provider com.android.providers.media.MediaDocumentsProvider
                // So we do this
                try {
                    grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    }
                } catch (Exception e) {
                    Logger.loge(TAG, e.toString());
                }
                if(uri != null) {
                    uriProcessor.process(getBaseContext(), uri, this);
                }
                else
                {
                    Logger.loge(TAG, "Uri is NULL");
                    setResult(RESULT_CODE_FAILURE);
                    finish();
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED);
            finish();
        } else {
            setResult(RESULT_CODE_FAILURE);
            finish();
        }
    }

    void start() {
        final Intent intent;
        showProgress();
        if (getIntent().getBooleanExtra(CAMERA, false)) {
            outputFileUri = FilePickerCameraIntent.setUri(this);
            intent = FilePickerCameraIntent.cameraIntent(outputFileUri);
        } else if (getIntent().getBooleanExtra(FILE, false)) {
            //Only file
            intent = FilePickerFileIntent.fileIntent("image/*");
            if (null != getIntent().getStringArrayExtra(MULTIPLE_TYPES)) {
                //User can specify multiple types for the intent.
                FilePickerFileIntent.setTypes(intent, getIntent().getStringArrayExtra(MULTIPLE_TYPES));
            } else if (null != getIntent().getStringExtra(TYPE)) {
                //If no types defaults to image files, if just 1 type applies type
                FilePickerFileIntent.setType(intent, getIntent().getStringExtra(TYPE));
            }
        } else {
            //We assume its an image since developer didn't specify anything and we will show chooser with Camera, File explorers (including gdrive, dropbox...)
            outputFileUri = FilePickerCameraIntent.setUri(this);

            if (null != getIntent().getStringArrayExtra(MULTIPLE_TYPES)) {
                //User can specify multiple types for the intent.
                intent = FilePickerChooserIntent.chooserMultiIntent(chooserText, outputFileUri, getIntent().getStringArrayExtra(MULTIPLE_TYPES));
            } else if (null != getIntent().getStringExtra(TYPE)) {
                //If no types defaults to image files, if just 1 type applies type
                intent = FilePickerChooserIntent.chooserSingleIntent(chooserText, outputFileUri, getIntent().getStringExtra(TYPE));
            }
            else {
                intent = FilePickerChooserIntent.chooserIntent(chooserText, outputFileUri);
            }
        }

        if (intent.resolveActivity(getPackageManager()) != null) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivityForResult(intent, REQUEST_CODE);
                }
            }, 500);

        } else {
            setResult(RESULT_FIRST_USER);
            finish();
        }
    }

    void showProgress()
    {
        rootFl.setVisibility(View.VISIBLE);
    }

    void hideProgress()
    {
        rootFl.setVisibility(View.GONE);
    }


    @Override
    public void onProcessingSuccess(Intent intent) {
        hideProgress();
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onProcessingFailure() {
        hideProgress();
        if (uriString == null) {    // this shouldn't be necessary since we already check before doing the URI processing
            setResult(RESULT_FIRST_USER);
            finish();
        }
        else {
            // if no processors worked, return original uri
            Intent intent = new Intent();
            intent.putExtra(URI, uriString);
            setResult(RESULT_OK, intent);
            finish();
        }
    }
}
