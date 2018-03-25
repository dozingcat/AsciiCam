# AsciiCam

As of March 2018 AsciiCam is not under active development. I may fix bugs but don't plan to add new features.
[Vector Camera](https://github.com/dozingcat/VectorCamera) is its successor.

AsciiCam is an Android application which converts the camera view to ASCII text and allows you to save pictures as PNG and HTML files. It is released under version 2.0 of the Apache License: http://www.apache.org/licenses/LICENSE-2.0.

The code shows a few interesting techniques:
- Converting camera preview images to RGB, in both Java and native code.
- Distributing image processing across multiple cores.
- Presenting images from a directory in a grid view which users can select from. 