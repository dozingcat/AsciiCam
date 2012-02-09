// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;

import com.dozingcatsoftware.util.AndroidUtils;
import com.dozingcatsoftware.asciicam.R;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

public class ViewImageActivity extends Activity {
    
    public static final int DELETE_RESULT = Activity.RESULT_FIRST_USER;
    
    ImageView imageView;
    Uri imageUri;

    public static Intent startActivityWithImageURI(Activity parent, Uri imageURI, String type) {
        Intent intent = new Intent(parent, ViewImageActivity.class);
        intent.setDataAndType(imageURI, type);
        parent.startActivityForResult(intent, 0);
        return intent;
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
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
    
    // touch handler methods called via onClick bindings in imageview.xml
    public void goBack(View view) {
        this.finish();
    }
    
    public void deleteImage(View view) {
        String path = this.getIntent().getData().getPath();
        (new File(path)).delete();
        this.setResult(DELETE_RESULT);
        this.finish();
    }
    
    public void shareImage(View view) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(this.getIntent().getType());
        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share Picture Using:"));
    }
    
    public void shareHtml(View view) {
        // hack: replace .png with .html in URI sent to this activity.
        String pngPath = imageUri.getPath().substring(0, imageUri.getPath().length()-4) + ".html";
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/html");
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(pngPath)));
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share HTML Using:"));
    }
}
