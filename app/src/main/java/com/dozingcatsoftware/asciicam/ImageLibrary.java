// Copyright (C) 2012 Brian Nenninger

package com.dozingcatsoftware.asciicam;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a directory where the PNG and HTML versions of saved pictures are stored.
 */
public class ImageLibrary {
    String baseDirectory;

    public ImageLibrary(String basedir) {
        this.baseDirectory = basedir;
    }

    public List<String> allImagePaths() {
        String[] filenameArray = (new File(baseDirectory)).list();
        List<String> filenames = filenameArray != null ?
                Arrays.asList(filenameArray) : Collections.<String>emptyList();
        Collections.sort(filenames);
        Collections.reverse(filenames);
        List<String> paths = new ArrayList<String>();
        // Look for "<base>/foo.png" and (for backwards compatibility) "<base>/foo/foo.png".
        for (String fn : filenames) {
            File f = new File(makePath(baseDirectory, fn));
            if (f.isDirectory()) {
                File nestedFile = new File(makePath(baseDirectory, fn, fn + ".png"));
                if (nestedFile.isFile()) {
                    paths.add(nestedFile.getAbsolutePath());
                }
            }
            else if (f.isFile() && fn.endsWith(".png")) {
                paths.add(f.getAbsolutePath());
            }
        }
        return paths;
    }

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
}
