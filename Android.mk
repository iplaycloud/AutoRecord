LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
#LOCAL_STATIC_JAVA_LIBRARIES := com.tchip.tachograph
LOCAL_STATIC_JAVA_LIBRARIES += jheader		
#LOCAL_STATIC_JAVA_LIBRARIES += kuwomusic-autosdk

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := AutoRecord

LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
include $(BUILD_PACKAGE)


include $(CLEAR_VARS) 

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := jheader:libs/jheader-0.1.jar
#LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += kuwomusic-autosdk:libs/kwmusic-autosdk-v1.2.jar	
		
include $(BUILD_MULTI_PREBUILT) 
