LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := asciiart
LOCAL_SRC_FILES := asciiart.c
LOCAL_CFLAGS := -std=c99

include $(BUILD_SHARED_LIBRARY)
