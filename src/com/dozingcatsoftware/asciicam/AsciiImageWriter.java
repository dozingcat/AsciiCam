package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.graphics.Bitmap;
import android.os.Environment;

public class AsciiImageWriter {
    
    public interface HtmlProvider {
        void writeHtml(Writer writer, String imageName) throws IOException;
    }

    DateFormat filenameDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    
    String basePictureDirectory = Environment.getExternalStorageDirectory() + File.separator + "AsciiCam";
    
    public String getBasePictureDirectory() {
        return basePictureDirectory;
    }
    
    public String getThumbnailDirectory() {
        return basePictureDirectory + File.separator + "thumbnails";
    }

    public String saveImageAndThumbnail(Bitmap image, Bitmap thumbnail, HtmlProvider htmlProvider) 
            throws IOException {
        String datestr = filenameDateFormat.format(new Date());
        String dir = getBasePictureDirectory() + File.separator + datestr;
        // make sure image and thumbnail directories exist
        (new File(dir)).mkdirs();
        if (!((new File(dir)).isDirectory())) {
            return null;
        }
        String pngPath = saveBitmap(image, dir, datestr);
        
        String htmlPath = dir + File.separator + datestr + ".html";
        FileWriter htmlOutput = new FileWriter(htmlPath);
        try {
            htmlProvider.writeHtml(htmlOutput, datestr);
        }
        finally {
            htmlOutput.close();
        }
        
        if (thumbnail!=null) {
            String thumbnailDir = getThumbnailDirectory();
            (new File(thumbnailDir)).mkdirs();
            // create .noindex file so thumbnail pictures won't be indexed and show up in the gallery app
            (new File(thumbnailDir + File.separator + ".nomedia")).createNewFile();

            saveBitmap(thumbnail, thumbnailDir, datestr);
        }
        
        return pngPath;
    }
    
    String saveBitmap(Bitmap bitmap, String dir, String imageName) throws IOException {
        String outputFilePath;
        FileOutputStream output = null;
        try {
            outputFilePath = dir + File.separator + imageName + ".png";
            output = new FileOutputStream(outputFilePath);
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, output);
            output.close();
        }
        finally {
            if (output!=null) output.close();
        }
        return outputFilePath;
    }

}
