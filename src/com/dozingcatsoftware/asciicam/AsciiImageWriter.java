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

/**
 * Writes bitmaps and HTML to directories on the external storage directory.
 */
public class AsciiImageWriter {

    DateFormat filenameDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    String basePictureDirectory = Environment.getExternalStorageDirectory() + File.separator + "AsciiCam";

    public String getBasePictureDirectory() {
        return basePictureDirectory;
    }

    public String getThumbnailDirectory() {
        return basePictureDirectory + File.separator + "thumbnails";
    }

    public String saveImageAndThumbnail(Bitmap image, Bitmap thumbnail, AsciiConverter.Result asciiResult)
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
            writeHtml(asciiResult, htmlOutput, datestr);
        }
        finally {
            htmlOutput.close();
        }

        String textPath = dir + File.separator + datestr + ".txt";
        FileWriter textOutput = new FileWriter(textPath);
        try {
            writeText(asciiResult, textOutput, datestr);
        }
        finally {
            textOutput.close();
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

    public void writeHtml(AsciiConverter.Result result, Writer writer, String imageName) throws IOException {
        writer.write("<html><head></title>Ascii Picture " + imageName + "</title></head>");
        writer.write("<body style=\"background:black\"><div style=\"background:black; letter-spacing:3px;\">\n");

        writer.write("<pre>");
        for(int r=0; r<result.rows; r++) {
            boolean hasSetColor = false;
            int lastColor = 0;
            // loop precondition: output is in the middle of a <span> tag.
            // This allows skipping the tag if it's a space or the same color as previous char.
            writer.write("<span>");
            for(int c=0; c<result.columns; c++) {
                String asciiChar = result.stringAtRowColumn(r, c);
                // don't use span tag for space
                if (" ".equals(asciiChar)) {
                    writer.write(asciiChar);
                    continue;
                }
                int color = result.colorAtRowColumn(r, c);
                if (hasSetColor && color==lastColor) {
                    writer.write(asciiChar);
                    continue;
                }
                String htmlColor = Integer.toHexString(color & 0x00ffffff);
                while (htmlColor.length() < 6) {
                    htmlColor = "0" + htmlColor;
                }
                lastColor = color;
                hasSetColor = true;
                writer.write(String.format("</span><span style=\"color:%s\">%s", htmlColor, asciiChar));
            }
            writer.write("</span>\n");
        }
        writer.write("</pre>\n");
        writer.write("</div></body></html>");
    }

    public void writeText(AsciiConverter.Result result, Writer writer, String imageName) throws IOException {
        for(int r=0; r<result.rows; r++) {
            for(int c=0; c<result.columns; c++) {
                writer.write(result.stringAtRowColumn(r, c));
            }
            writer.write("\n");
        }
    }
}
