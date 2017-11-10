// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

import com.dozingcatsoftware.util.AndroidUtils;

/** Activity for displaying a single image. The user can delete the image, or share it in
 * its PNG or HTML representations.
 */
public class ViewImageActivity extends Activity {

    enum ShareFileType {
        IMAGE,
        HTML,
        TEXT,
    }

    public static final int DELETE_RESULT = Activity.RESULT_FIRST_USER;

    ImageView imageView;
    Uri imageUri;
    ShareFileType shareFileType;

    public static Intent startActivityWithImageURI(Activity parent, Uri imageURI, String type) {
        Intent intent = new Intent(parent, ViewImageActivity.class);
        intent.setDataAndType(imageURI, type);
        parent.startActivityForResult(intent, 0);
        return intent;
    }

    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.imageview);

        imageView = (ImageView)findViewById(R.id.imageView);
        imageUri = getIntent().getData();

        // assume full screen, there's no good way to get notified once layout happens and views have nonzero width/height
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        try {
            imageView.setImageBitmap(AndroidUtils.scaledBitmapFromURIWithMinimumSize(this, imageUri,
                    dm.widthPixels, dm.heightPixels));
        }
        catch(Exception ex) {}
    }

    // Methods called via onClick bindings in imageview.xml
    public void goBack(View view) {
        this.finish();
    }

    public void deleteImage(View view) {
        String path = this.getIntent().getData().getPath();
        (new File(path)).delete();
        this.setResult(DELETE_RESULT);
        this.finish();
    }

    public void sharePicture(View view) {
        shareFileType = ShareFileType.IMAGE;
        final String[] shareTypeLabels = {
            getString(R.string.shareImageOptionLabel),
            getString(R.string.shareHtmlOptionLabel),
            getString(R.string.shareTextOptionLabel),
        };
        final ShareFileType[] shareTypes = {
            ShareFileType.IMAGE,
            ShareFileType.HTML,
            ShareFileType.TEXT,
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.shareDialogTitle)
                .setSingleChoiceItems(shareTypeLabels, 0, new DialogInterface.OnClickListener() {
                    // Called when the user selects a file type, does not close the dialog.
                    @Override public void onClick(DialogInterface dialog, int which) {
                        shareFileType = shareTypes[which];
                    }
                })
                .setPositiveButton(R.string.shareDialogYesLabel, new DialogInterface.OnClickListener() {
                    // Called when the user accepts the dialog.
                    @Override public void onClick(DialogInterface dialog, int which) {
                        switch (shareFileType) {
                            case IMAGE:
                                shareImage();
                                break;
                            case HTML:
                                shareHtml();
                                break;
                            case TEXT:
                                shareText();
                                break;
                        }
                    }
                })
                .setNegativeButton(R.string.shareDialogNoLabel, null)
                .show();
    }

    // Share methods.
    public void shareImage() {
        shareFile(imageUri, this.getIntent().getType(), getString(R.string.shareImageTitle));
    }

    public void shareHtml() {
        // hack: replace .png with .html in URI sent to this activity.
        String htmlPath = imageUri.getPath().substring(0, imageUri.getPath().length()-4) + ".html";
        Uri htmlUri = Uri.fromFile(new File(htmlPath));
        shareFile(htmlUri, "text/html", getString(R.string.shareHtmlTitle));
    }

    public void shareText() {
        // hack: replace .png with .txt in URI sent to this activity.
        String textPath = imageUri.getPath().substring(0, imageUri.getPath().length()-4) + ".txt";
        Uri textUri = Uri.fromFile(new File(textPath));
        shareFile(textUri, "text/plain", getString(R.string.shareTextTitle));
    }

    private void shareFile(Uri uri, String mimeType, String shareLabel) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, shareLabel));
    }
}
