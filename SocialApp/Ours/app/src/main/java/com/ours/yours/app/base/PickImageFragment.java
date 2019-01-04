package com.ours.yours.app.base;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.orhanobut.logger.Logger;
import com.ours.yours.R;
import com.ours.yours.app.dao.FileHelper;
import com.ours.yours.app.dao.UriHelper;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

public abstract class PickImageFragment extends BaseChildFragment {
    protected static final String SAVED_STATE_IMAGE_URI = "PickImageFragment.SAVED_STATE_IMAGE_URI";
    private static final int OUR_PERMISSION_REQUEST_CODE = 1;
    private Uri mUriPickImage;

    protected abstract void onPick(Uri image);

    public Uri getUriOfPickImage() {
        return mUriPickImage;
    }


    public void setUriOfPickImage(Uri uri) {
        this.mUriPickImage = uri;
    }


    /**
     *  If any property is belonged to View, do the state saving/restoring inside View
     *  through having implements on View#onSaveInstanceState() and View#onRestoreInstanceState().
     *  If any property is belonged to Fragment, do it inside Fragment
     *  through having implements on Fragment#onSaveInstanceState() and Fragment#onActivityCreated().
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Logger.d(">>>");
        outState.putParcelable(SAVED_STATE_IMAGE_URI, mUriPickImage);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVED_STATE_IMAGE_URI)) {
                mUriPickImage = savedInstanceState.getParcelable(SAVED_STATE_IMAGE_URI);
                if (mUriPickImage != null) {
                    onPick(mUriPickImage);
                } else {
                    Logger.e("!!! mUriPickImage is null");
                }
            }
        }
        if (!makeSureStoragePermissionGranted()) {
            Logger.d("... going to request permissions for WRITE_EXTERNAL_STORAGE");
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        Logger.d(">>>");
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVED_STATE_IMAGE_URI)) {
                mUriPickImage = savedInstanceState.getParcelable(SAVED_STATE_IMAGE_URI);
                if (mUriPickImage != null) {
                    onPick(mUriPickImage);
                } else {
                    Logger.e("!!! mUriPickImage is null");
                }
            }
        }
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(">>> ");
        stringBuilder.append("requestCode:" + requestCode);
        stringBuilder.append(", resultCode:" + resultCode);
        if (data != null) stringBuilder.append(", action:" + data.getAction());
        Logger.d("... " + stringBuilder.toString());

        // For API >= 23 we need to check specifically that we have permissions to read external storage.
        if (CropImage.isReadExternalStoragePermissionsRequired(_mActivity, mUriPickImage)) {
            // request permissions and handle the result in onRequestPermissionsResult()
            requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, CropImage.PICK_IMAGE_PERMISSIONS_REQUEST_CODE);
        }
        if (requestCode == CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            mUriPickImage = CropImage.getPickImageResultUri(_mActivity, data);
            if (mUriPickImage != null ) {
                Logger.d("... mUriPickImage                           :" + mUriPickImage + "\n"
                        +"... mUriPickImage path                      :" + mUriPickImage.getPath() + "\n"
                        +"... mUriPickImage string                    :" + mUriPickImage.toString() + "\n"
                        +"... mUriPickImage UriHelper file scheme path:" + UriHelper.getInstance().getPath(mUriPickImage, _mActivity));
                onPick(mUriPickImage);
                /*
                if (false) { //file helper testing for normal text
                    Logger.d("... going to write bytes to file:");
                    //FileHelper.getInstance().writeBytesToFile(FileHelper.getInstance().createInternalFile("amo", "amo1.txt", _mActivity), "amo".getBytes(Charset.forName("UTF-8")));
                    FileHelper.getInstance().writeStringToFile(FileHelper.getInstance().createInternalFile("amo", "amo1.txt", _mActivity), "hello ours", null);
                    Logger.d("... going to copy bytes to file:");
                    FileHelper.getInstance().copy(FileHelper.getInstance().getInternalFile("amo", "amo1.txt", _mActivity)
                            , FileHelper.getInstance().createInternalFile("amo", "amo2.txt", _mActivity));
                    LinkedList<File> files = new LinkedList<>();
                    FileHelper.getInstance().list(FileHelper.getInstance().getInternalFile("amo", null, _mActivity).getAbsolutePath(), "", files);
                    stringBuilder = new StringBuilder();
                    int i = 0;
                    for (File f : files) {
                        i++;
                        stringBuilder.append("... the " + i + "th file path:" + f.getAbsoluteFile() + "\n");
                        stringBuilder.append("... the " + i + "th file data:");
                        for (byte b : FileHelper.getInstance().readBytesFromFile(f, true)) {
                            stringBuilder.append(String.valueOf((char) b));
                        }
                        stringBuilder.append("\n");
                        stringBuilder.append("... the " + i + "th file length:" + f.length());
                        stringBuilder.append("\n");
                    }
                    Logger.d(stringBuilder.toString());
                }
                */
            } else {
                Logger.e("!!! mUriPickImage is null");
            }
        } else if (requestCode != CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE) {
            Logger.e("!!!  requestCode is not CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE");
        } else if (data == null) {
            Logger.e("!!!  data is null");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Logger.d(">>>");
        switch (requestCode) {
            case OUR_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && permissions[0].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    // check whether storage permission granted or not.
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Logger.d("... OUR_PERMISSION_REQUEST_CODE got granted");
                    }
                }
                break;

            case CropImage.CAMERA_CAPTURE_PERMISSIONS_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Logger.d("... CAMERA_CAPTURE_PERMISSIONS got granted");
                    startPickImageActivity();
                } else {
                    showSnackBar(_mActivity.findViewById(android.R.id.content), getString(R.string.permissions_camera_capture_not_granted));
                    Logger.e("!!! CAMERA_CAPTURE_PERMISSIONS not granted");
                }
                break;

            case CropImage.PICK_IMAGE_PERMISSIONS_REQUEST_CODE:
                if (mUriPickImage != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Logger.d("... PICK_IMAGE_PERMISSIONS got granted");
                    if (mUriPickImage != null) {
                        onPick(mUriPickImage);
                    } else {
                        Logger.e("!!! mUriPickImage is null");
                    }
                } else {
                    showSnackBar(_mActivity.findViewById(android.R.id.content), getString(R.string.permissions_external_storage_not_granted));
                    Logger.e("!!! PICK_IMAGE_PERMISSIONS not granted");
                }
                break;

            default:
                break;
        }
    }

    protected void pickImage() {
        if (CropImage.isExplicitCameraPermissionRequired(_mActivity)) {
            Logger.d("... going to requestPermissions()");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CropImage.CAMERA_CAPTURE_PERMISSIONS_REQUEST_CODE);
        } else {
            Logger.d("... going to startPickImageActivity()");
            startPickImageActivity();
        }
    }

    private void startPickImageActivity() {
        Logger.d(">>>");
        CropImage.startPickImageActivity(_mActivity);
    }

    protected  boolean makeSureStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (_mActivity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Logger.d("... checkSelfPermission() granted");
                return true;
            } else {
                Logger.e("!!! checkSelfPermission() not granted");
                _mActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, OUR_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Logger.d("... permission is automatically granted");
            return true;
        }
    }
}
