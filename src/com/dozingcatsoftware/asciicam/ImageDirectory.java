// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageDirectory {
    
    String baseDirectory;
    List<File> imageFiles = new ArrayList<File>();

    public ImageDirectory(String basedir) {
        this.baseDirectory = basedir;
        updateImageList();
    }
    
    public void updateImageList() {
        imageFiles.clear();
        File[] basedirContents = (new File(baseDirectory)).listFiles();
        for(File f : basedirContents) {
            if (f.isDirectory()) {
                File imageFile = new File(f.getAbsolutePath() + File.separator + f.getName() + ".html");
                if (imageFile.isFile()) {
                    imageFiles.add(imageFile);
                }
            }
        }
    }
    
    public int getFileCount() {
        return imageFiles.size();
    }
    
    public File getFileForIndex(int index) {
        return imageFiles.get(index);
    }
    
    public String getFilePathForIndex(int index) {
        return imageFiles.get(index).getAbsolutePath();
    }
}
