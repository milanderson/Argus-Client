#include <jni.h>
#include <stdint.h>
#include <android/log.h>
#include <libyuv/scale.h>
#include <libyuv.h>
#include <stdio.h>

#define  LOG_TAG    "libyuv-jni"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS_)


struct YuvFrame {
    int width;
    int height;
    uint8_t *data;
    uint8_t *y;
    uint8_t *u;
    uint8_t *v;
};

static struct YuvFrame i420_input_frame;
static struct YuvFrame i420_output_frame;

extern "C" JNIEXPORT jbyteArray JNICALL

Java_sbu_IRClient_Net_1ClassRequester_resizeYUV420(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray yuvByteArray_,
        jint src_width, jint src_height,
        jint out_width, jint out_height) {

    jbyte *yuvByteArray = env->GetByteArrayElements(yuvByteArray_, NULL);

    int in_size = src_width * src_height;
    int out_size = out_height * out_width;


    //Generate input frame
    i420_input_frame.width = src_width;
    i420_input_frame.height = src_height;
    i420_input_frame.data = (uint8_t *) yuvByteArray;
    i420_input_frame.y = i420_input_frame.data;
    i420_input_frame.u = i420_input_frame.y + in_size;
    i420_input_frame.v = i420_input_frame.u + (in_size / 4);

    //Generate output frame
    i420_output_frame.width = out_width;
    i420_output_frame.height = out_height;
    i420_output_frame.data = new unsigned char[out_size * 3 / 2];
    i420_output_frame.y = i420_output_frame.data;
    i420_output_frame.u = i420_output_frame.y + out_size;
    i420_output_frame.v = i420_output_frame.u + (out_size / 4);
    libyuv::FilterMode mode = libyuv::FilterModeEnum::kFilterBilinear;

    int result = libyuv::I420Scale(i420_input_frame.y, i420_input_frame.width,
                           i420_input_frame.u, i420_input_frame.width / 2,
                           i420_input_frame.v, i420_input_frame.width / 2,
                           i420_input_frame.width, i420_input_frame.height,
                           i420_output_frame.y, i420_output_frame.width,
                           i420_output_frame.u, i420_output_frame.width / 2,
                           i420_output_frame.v, i420_output_frame.width / 2,
                           i420_output_frame.width, i420_output_frame.height,
                           mode);
    //env->ReleaseByteArrayElements(yuvByteArray_, yuvByteArray, 0);

    int outArraySize = (out_size * 3) / 2;
    jbyteArray out = env->NewByteArray (outArraySize);
    env->SetByteArrayRegion (out, 0, outArraySize, reinterpret_cast<jbyte*>(i420_output_frame.data));
    return out;
}
