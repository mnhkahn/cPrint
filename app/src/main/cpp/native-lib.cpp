#include <jni.h>
#include <string>
#include <android/log.h>
#include "escpr_writer.h"
#include "escp2_writer.h"

#define LOG_TAG "cPrintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Native method for optimized bitmap processing
 */
JNIEXPORT jobject JNICALL
Java_com_cprint_app_util_NativeUtils_processBitmap(
        JNIEnv *env,
        jclass clazz,
        jobject bitmap,
        jint width,
        jint height) {
    // Placeholder for native bitmap processing
    // In a real implementation, this would use native code for faster image processing
    LOGI("Processing bitmap: %dx%d", width, height);
    return bitmap;
}

/**
 * Native method for PCL data generation
 */
JNIEXPORT jbyteArray JNICALL
Java_com_cprint_app_util_NativeUtils_generatePclData(
        JNIEnv *env,
        jclass clazz,
        jbyteArray imageData,
        jint width,
        jint height) {
    // Placeholder for native PCL generation
    LOGI("Generating PCL data: %dx%d", width, height);
    return imageData;
}

/**
 * Native method for ESC/P data generation
 */
JNIEXPORT jbyteArray JNICALL
Java_com_cprint_app_util_NativeUtils_generateEscPData(
        JNIEnv *env,
        jclass clazz,
        jbyteArray imageData,
        jint width,
        jint height) {
    // Placeholder for native ESC/P generation
    LOGI("Generating ESC/P data: %dx%d", width, height);
    return imageData;
}

/**
 * Native method for ESC/P-R data generation using Epson ESC/P-R protocol.
 * Takes RGB byte array and generates complete printer command stream.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_cprint_app_util_NativeUtils_nativeGenerateEscprData(
        JNIEnv *env,
        jclass clazz,
        jbyteArray rgbData,
        jint width,
        jint height,
        jint dpi) {

    if (rgbData == nullptr || width <= 0 || height <= 0) {
        LOGE("Invalid arguments to nativeGenerateEscprData");
        return nullptr;
    }

    jsize rgbLen = env->GetArrayLength(rgbData);
    if (rgbLen < width * height * 3) {
        LOGE("RGB data too small: %d < %d", rgbLen, width * height * 3);
        return nullptr;
    }

    jbyte *rgbBytes = env->GetByteArrayElements(rgbData, nullptr);
    if (rgbBytes == nullptr) {
        LOGE("Failed to get byte array elements");
        return nullptr;
    }

    escpr_buffer buf;
    escpr_buffer_init(&buf);

    int ret = escpr_generate_print_data(
        reinterpret_cast<const uint8_t *>(rgbBytes),
        width,
        height,
        dpi,
        &buf
    );

    env->ReleaseByteArrayElements(rgbData, rgbBytes, JNI_ABORT);

    if (ret != 0 || buf.data == nullptr || buf.size == 0) {
        LOGE("Failed to generate ESC/P-R data");
        escpr_buffer_free(&buf);
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(buf.size));
    if (result == nullptr) {
        LOGE("Failed to allocate result byte array");
        escpr_buffer_free(&buf);
        return nullptr;
    }

    env->SetByteArrayRegion(result, 0, static_cast<jsize>(buf.size),
                            reinterpret_cast<const jbyte *>(buf.data));
    escpr_buffer_free(&buf);

    LOGI("Generated ESC/P-R data: %zu bytes (RGB raw: %d bytes)", buf.size, width * height * 3);
    return result;
}

/**
 * Native method for ESC/P2 data generation using Epson ESC/P2 protocol.
 * Takes RGB byte array and generates black/white raster printer command stream.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_cprint_app_util_NativeUtils_nativeGenerateEscp2Data(
        JNIEnv *env,
        jclass clazz,
        jbyteArray rgbData,
        jint width,
        jint height) {

    if (rgbData == nullptr || width <= 0 || height <= 0) {
        LOGE("Invalid arguments to nativeGenerateEscp2Data");
        return nullptr;
    }

    jsize rgbLen = env->GetArrayLength(rgbData);
    if (rgbLen < width * height * 3) {
        LOGE("RGB data too small: %d < %d", rgbLen, width * height * 3);
        return nullptr;
    }

    jbyte *rgbBytes = env->GetByteArrayElements(rgbData, nullptr);
    if (rgbBytes == nullptr) {
        LOGE("Failed to get byte array elements");
        return nullptr;
    }

    escp2_buffer buf;
    escp2_buffer_init(&buf);

    int ret = escp2_generate_print_data(
        reinterpret_cast<const uint8_t *>(rgbBytes),
        width,
        height,
        &buf
    );

    env->ReleaseByteArrayElements(rgbData, rgbBytes, JNI_ABORT);

    if (ret != 0 || buf.data == nullptr || buf.size == 0) {
        LOGE("Failed to generate ESC/P2 data");
        escp2_buffer_free(&buf);
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(buf.size));
    if (result == nullptr) {
        LOGE("Failed to allocate result byte array");
        escp2_buffer_free(&buf);
        return nullptr;
    }

    env->SetByteArrayRegion(result, 0, static_cast<jsize>(buf.size),
                            reinterpret_cast<const jbyte *>(buf.data));
    escp2_buffer_free(&buf);

    LOGI("Generated ESC/P2 data: %zu bytes", buf.size);
    return result;
}

} // extern "C"
