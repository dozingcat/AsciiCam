// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dozingcatsoftware.util.AsyncImageLoader;
import com.dozingcatsoftware.util.ScaledBitmapCache;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

/**
 * Activity which displays all pictures the user has taken in a scrolling grid. Selecting an image opens it
 * in a ViewImageActivity.
 */
public class LibraryActivity extends Activity {

    static int CELL_WIDTH = 92;
    static int CELL_HEIGHT = 69;

    String imageDirectory;

    GridView gridView;
    int selectedGridIndex;

    List<Map<String, Uri>> imageMaps = new ArrayList<Map<String, Uri>>();
    static String IMAGE_URI_KEY = "imageUri";

    // A cache of scaled Bitmaps for the image files, so we can avoid reloading them as the user scrolls.
    ScaledBitmapCache bitmapCache;
    AsyncImageLoader imageLoader = new AsyncImageLoader();

    public static Intent intentWithImageDirectory(Context parent, String imageDirectory, String thumbnailDirectory) {
        Intent intent = new Intent(parent, LibraryActivity.class);
        intent.putExtra("imageDirectory", imageDirectory);
        intent.putExtra("thumbnailDirectory", thumbnailDirectory);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.library_list);

        imageDirectory = getIntent().getStringExtra("imageDirectory");
        bitmapCache = new ScaledBitmapCache(this, getIntent().getStringExtra("thumbnailDirectory"));

        gridView = (GridView) findViewById(R.id.gridview);
        gridView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedGridIndex = position;
                ViewImageActivity.startActivityWithImageURI(LibraryActivity.this,
                        imageMaps.get(position).get(IMAGE_URI_KEY), "image/jpeg");
            }
        });
        readImageThumbnails();
        displayGrid();
    }

    void readImageThumbnails() {
        ImageLibrary library = new ImageLibrary(imageDirectory);
        imageMaps.clear();
        for (String path : library.allImagePaths()) {
            Map<String, Uri> dmap = new HashMap<String, Uri>();
            dmap.put(IMAGE_URI_KEY, Uri.fromFile(new File(path)));
            imageMaps.add(dmap);
        }
    }

    void displayGrid() {
        SimpleAdapter adapter = new SimpleAdapter(this, imageMaps,
                R.layout.library_cell,
                new String[] {IMAGE_URI_KEY},
                new int[] {R.id.grid_image});
        adapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data, String textRepresentation) {
                Uri imageUri = (Uri)data;
                imageLoader.loadImageIntoViewAsync(bitmapCache, imageUri, (ImageView)view, CELL_WIDTH, CELL_HEIGHT, getResources());
                return true;
            }
        });
        gridView.setAdapter(adapter);

        // show text message if no images available
        View noImagesView = findViewById(R.id.noImagesTextView);
        noImagesView.setVisibility(imageMaps.size()>0 ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode==ViewImageActivity.DELETE_RESULT) {
            bitmapCache.removeUri(imageMaps.get(selectedGridIndex).get(IMAGE_URI_KEY));
            imageMaps.remove(selectedGridIndex);
            displayGrid();
        }
    }

}
