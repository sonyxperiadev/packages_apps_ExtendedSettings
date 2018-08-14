LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := ExtendedSettings
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
ifeq (1,$(filter 1,$(shell echo "$$(( $(PLATFORM_SDK_VERSION) >= 28 ))" )))
    LOCAL_PRIVATE_PLATFORM_APIS := true
endif

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-preference \
    android-support-v7-recyclerview \
    android-support-v13 \
    android-support-v14-preference

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res \
    frameworks/support/v14/preference/res \
    frameworks/support/v7/appcompat/res \
    frameworks/support/v7/preference/res \
    frameworks/support/v7/recyclerview/res

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.v14.preference \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.v7.preference \
    --extra-packages android.support.v7.recyclerview

LOCAL_PROGUARD_FLAG_FILES := proguard-rules.pro

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call all-makefiles-under,$(LOCAL_PATH))
endif
