package com.dozingcatsoftware.asciicam;

import android.app.Activity;
import android.content.Context;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ListAdapter;

public class ImageGalleryActivity extends Activity {
	
	GridView gridView;
	ImageDirectory imageDirectory;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.imagegrid);
		
		imageDirectory = new ImageDirectory(getIntent().getStringExtra("imageDirectory"));
		ImageGridAdapter gridAdapter = new ImageGridAdapter(this, imageDirectory);
		gridView = (GridView)findViewById(R.id.gridview);
		gridView.setAdapter(gridAdapter);
	}

	static class ImageGridAdapter extends BaseAdapter {
		
		Context context;
		ImageDirectory imageDirectory;
		
		public ImageGridAdapter(Context context, ImageDirectory imageDirectory) {
			this.context = context;
			this.imageDirectory = imageDirectory;
		}

		@Override
		public int getCount() {
			return imageDirectory.getFileCount();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			WebView webview = (convertView instanceof WebView) ? (WebView)convertView : null;
			if (webview==null) {
				webview = new WebView(context);
			}
			webview.setLayoutParams(new GridView.LayoutParams(240, 160));
			webview.loadUrl(Uri.fromFile(imageDirectory.getFileForIndex(position)).toString());
			return webview;
		}

	}
}
