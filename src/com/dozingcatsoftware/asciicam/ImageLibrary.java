// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a directory where the PNG and HTML versions of saved pictures are stored.
 */
public class ImageLibrary {

    public class Entry {
        public final String imageName;
        public final String pngPath;
        public final String textPath;
        public final String htmlPath;
        public final String thumbnailPath;

        public Entry(String imageName, String pngPath, String textPath, String htmlPath, String thumbnailPath) {
            this.imageName = imageName;
            this.pngPath = pngPath;
            this.textPath = textPath;
            this.htmlPath = htmlPath;
            this.thumbnailPath = thumbnailPath;
        }
    }

    String baseDirectory;
    List<Entry> entries = new ArrayList<Entry>();

    public ImageLibrary(String basedir) {
        this.baseDirectory = basedir;
    }

    private Entry entryForNameAndThumbnail(String imageName, File thumbnailFile) {
        File imageDirFile = new File(makePath(baseDirectory, "images", imageName + ".png"));
        if (imageDirFile.isFile()) {
            return new Entry(
                    imageName,
                    imageDirFile.getAbsolutePath(),
                    absPathOrNull(makePath(baseDirectory, "text", imageName + ".txt")),
                    absPathOrNull(makePath(baseDirectory, "html", imageName + ".html")),
                    thumbnailFile.getAbsolutePath());
        }
        else {
            File singleDirImageFile = new File(makePath(baseDirectory, imageName, imageName + ".png"));
            if (singleDirImageFile.isFile()) {
                return new Entry(
                        imageName,
                        singleDirImageFile.getAbsolutePath(),
                        absPathOrNull(makePath(baseDirectory, imageName, imageName + ".txt")),
                        absPathOrNull(makePath(baseDirectory, imageName, imageName + ".html")),
                        thumbnailFile.getAbsolutePath());
            }
        }
        return null;
    }

    public Entry entryForName(String imageName) {
        File thumbnailFile = new File(makePath(baseDirectory, "thumbnails", imageName + ".png"));
        return entryForNameAndThumbnail(imageName, thumbnailFile);
    }

    public List<Entry> getAllEntries() {
        List<Entry> entries = new ArrayList<Entry>();
        File thumbnailDir = new File(makePath(baseDirectory, "thumbnails"));
        if (!thumbnailDir.isDirectory()) {
            return entries;
        }
        File[] thumbnailFiles = thumbnailDir.listFiles();
        for (File tf : thumbnailFiles) {
            if (tf.isFile() && tf.getName().endsWith(".png")) {
                String imageName = tf.getName().substring(0, tf.getName().length() - 4);
                Entry entry = entryForNameAndThumbnail(imageName, tf);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;

        /*
        // Look for directories
        for(File f : basedirContents) {
            if (f.isDirectory()) {
                File imageFile = new File(f.getAbsolutePath() + File.separator + f.getName() + ".html");
                if (imageFile.isFile()) {
                    imageFiles.add(imageFile);
                }
            }
        }
        */
    }

    /*
    public int getFileCount() {
        return entries.size();
    }

    public File getImageFileForIndex(int index) {
        return new File(entries.get(index).pngPath);
    }

    public String getImageFilePathForIndex(int index) {
        return entries.get(index).pngPath;
    }
    */

    private static String makePath(String... args) {
        StringBuilder buffer = new StringBuilder();
        boolean first = true;
        for (String s : args) {
            if (!first) {
                buffer.append(File.separator);
            }
            buffer.append(s);
            first = false;
        }
        return buffer.toString();
    }

    private static String absPathOrNull(String path) {
        File f = new File(path);
        return (f.isFile()) ? f.getAbsolutePath() : null;
    }
}
