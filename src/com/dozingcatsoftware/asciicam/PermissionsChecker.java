package com.dozingcatsoftware.asciicam;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;

@TargetApi(23)
public class PermissionsChecker {
    public static final int CAMERA_AND_STORAGE_REQUEST_CODE = 1001;
    public static final int STORAGE_FOR_PHOTO_REQUEST_CODE = 1002;
    public static final int STORAGE_FOR_LIBRARY_REQUEST_CODE = 1003;

    static boolean hasPermission(Activity activity, String perm) {
        return activity.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasCameraPermission(Activity activity) {
        return hasPermission(activity, Manifest.permission.CAMERA);
    }

    public static boolean hasStoragePermission(Activity activity) {
        return hasPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                hasPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static void requestCameraAndStoragePermissions(Activity activity) {
        activity.requestPermissions(
                new String[] {
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                },
                CAMERA_AND_STORAGE_REQUEST_CODE);
    }

    public static void requestStoragePermissionsToTakePhoto(Activity activity) {
        activity.requestPermissions(
                new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                },
                STORAGE_FOR_PHOTO_REQUEST_CODE);
    }

    public static void requestStoragePermissionsToGoToLibrary(Activity activity) {
        activity.requestPermissions(
                new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                },
                STORAGE_FOR_LIBRARY_REQUEST_CODE);
    }
}
