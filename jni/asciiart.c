#include <jni.h>
#include <stdlib.h>

/* These functions are the C versions of the ASCII conversion algorithms in AsciiConverter.java. */

void Java_com_dozingcatsoftware_asciicam_AsciiConverter_getAsciiValuesBWNative(JNIEnv* env, jobject thiz, 
        jbyteArray jdata, jint imageWidth, jint imageHeight, 
        jint asciiRows, jint asciiCols, jint numAsciiChars, jintArray jasciiOutput,
        jint startRow, jint endRow) {

    jbyte *data = (*env)->GetByteArrayElements(env, jdata, 0);
    jint *asciiOutput = (*env)->GetIntArrayElements(env, jasciiOutput, 0);
    
    int asciiIndex = startRow * asciiCols;
    for(int r=startRow; r<endRow; r++) {
        // compute grid of data pixels whose brightness to average
        int ymin = imageHeight * r / asciiRows;
        int ymax = imageHeight * (r+1) / asciiRows;
        for(int c=0; c<asciiCols; c++) {
            int xmin = imageWidth * c / asciiCols;
            int xmax = imageWidth * (c+1) / asciiCols;
            
            int totalBright = 0;
            int samples = 0;
            for(int y=ymin; y<ymax; y++) {
                int rowoffset = imageWidth * y;
                for(int x=xmin; x<xmax; x++) {
                    samples++;
                    totalBright += (0xff & data[rowoffset+x]);
                }
            }
            int averageBright = totalBright / samples;
            asciiOutput[asciiIndex++] = (averageBright * numAsciiChars) / 256;
        }
    }    
    
    (*env)->ReleaseByteArrayElements(env, jdata, data, 0);
    (*env)->ReleaseIntArrayElements(env, jasciiOutput, asciiOutput, 0);
}


void Java_com_dozingcatsoftware_asciicam_AsciiConverter_getAsciiValuesWithColorNative(JNIEnv* env, jobject thiz, 
        jbyteArray jdata, jint imageWidth, jint imageHeight, 
        jint asciiRows, jint asciiCols, jint numAsciiChars, jboolean ansiColor,
        jintArray jasciiOutput, jintArray jcolorOutput, jint startRow, jint endRow) {
    
    jbyte *data = (*env)->GetByteArrayElements(env, jdata, 0);
    jint *asciiOutput = (*env)->GetIntArrayElements(env, jasciiOutput, 0);
    jint *colorOutput = (*env)->GetIntArrayElements(env, jcolorOutput, 0);
    
    static int MAX_COLOR_VAL = 262143; // 2**18-1
    static float ANSI_COLOR_RATIO = 7.0f/8;
    int asciiIndex = startRow * asciiCols;
    for(int r=startRow; r<endRow; r++) {
        // compute grid of data pixels whose brightness to average
        int ymin = imageHeight * r / asciiRows;
        int ymax = imageHeight * (r+1) / asciiRows;
        for(int c=0; c<asciiCols; c++) {
            int xmin = imageWidth * c / asciiCols;
            int xmax = imageWidth * (c+1) / asciiCols;
            
            int totalBright = 0;
            int totalRed=0, totalGreen=0, totalBlue=0;
            int samples = 0;
            for(int y=ymin; y<ymax; y++) {
                int rowoffset = imageWidth * y;
                int uvoffset = imageWidth * imageHeight + (imageWidth * (y / 2));
                for(int x=xmin; x<xmax; x++) {
                    samples++;
                    int bright = 0xff & data[rowoffset+x];
                    totalBright += bright;
                    // YUV to RGB conversion
                    int yy = bright - 16;
                    if (yy < 0) yy = 0;
                    
                    int uvindex = uvoffset + (x & ~1);
                    int v = (0xff & data[uvindex]) - 128;
                    int u = (0xff & data[uvindex + 1]) - 128;
                    int y1192 = 1192 * yy;
                    int red = (y1192 + 1634 * v);  
                    int green = (y1192 - 833 * v - 400 * u);  
                    int blue = (y1192 + 2066 * u);
                    
                    if (red<0) red=0; if (red>MAX_COLOR_VAL) red=MAX_COLOR_VAL;
                    if (green<0) green=0; if (green>MAX_COLOR_VAL) green=MAX_COLOR_VAL;
                    if (blue<0) blue=0; if (blue>MAX_COLOR_VAL) blue=MAX_COLOR_VAL;
                    
                    totalRed += red;
                    totalGreen += green;
                    totalBlue += blue;
                }
            }
            int averageBright = totalBright / samples;
            asciiOutput[asciiIndex] = (averageBright * numAsciiChars) / 256;
            int averageRed = totalRed / samples;
            int averageGreen = totalGreen / samples;
            int averageBlue = totalBlue / samples;
            
            if (ansiColor) {
            // force highest color component to maximum (brightness is already handled by char)
                int maxRG = (averageRed > averageGreen) ? averageRed : averageGreen;
                int maxColor = (averageBlue > maxRG) ? averageBlue : maxRG;
                if (maxColor > 0) {
                    int threshold = (int)(maxColor * ANSI_COLOR_RATIO);
                    averageRed = (averageRed >= threshold) ? MAX_COLOR_VAL : 0;
                    averageGreen = (averageGreen >= threshold) ? MAX_COLOR_VAL : 0;
                    averageBlue = (averageBlue >= threshold) ? MAX_COLOR_VAL : 0;
                }
            }
            colorOutput[asciiIndex] = (0xff000000) | ((averageRed << 6) & 0xff0000) |
            ((averageGreen >> 2) & 0xff00) | ((averageBlue >> 10));
            ++asciiIndex;
        }
    }
    
    (*env)->ReleaseByteArrayElements(env, jdata, data, 0);
    (*env)->ReleaseIntArrayElements(env, jasciiOutput, asciiOutput, 0);
    (*env)->ReleaseIntArrayElements(env, jcolorOutput, colorOutput, 0);
}
