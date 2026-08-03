#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"
#include <jni.h>
#include <stdlib.h>

typedef struct {
    ma_engine engine;
    ma_sound sound;
    ma_bool32 has_sound;
} relay_audio;

JNIEXPORT jlong JNICALL Java_dev_relay_music_desktop_NativeAudio_create(JNIEnv* env, jobject self) {
    relay_audio* audio = calloc(1, sizeof(relay_audio));
    if (audio == NULL || ma_engine_init(NULL, &audio->engine) != MA_SUCCESS) {
        free(audio);
        return 0;
    }
    return (jlong)audio;
}

JNIEXPORT void JNICALL Java_dev_relay_music_desktop_NativeAudio_destroy(JNIEnv* env, jobject self, jlong handle) {
    relay_audio* audio = (relay_audio*)handle;
    if (audio == NULL) return;
    if (audio->has_sound) ma_sound_uninit(&audio->sound);
    ma_engine_uninit(&audio->engine);
    free(audio);
}

JNIEXPORT jboolean JNICALL Java_dev_relay_music_desktop_NativeAudio_load(JNIEnv* env, jobject self, jlong handle, jstring path) {
    relay_audio* audio = (relay_audio*)handle;
    if (audio == NULL) return JNI_FALSE;
    if (audio->has_sound) { ma_sound_uninit(&audio->sound); audio->has_sound = MA_FALSE; }
    const char* utf_path = (*env)->GetStringUTFChars(env, path, NULL);
    ma_result result = ma_sound_init_from_file(&audio->engine, utf_path, 0, NULL, NULL, &audio->sound);
    (*env)->ReleaseStringUTFChars(env, path, utf_path);
    audio->has_sound = result == MA_SUCCESS;
    return audio->has_sound ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_dev_relay_music_desktop_NativeAudio_play(JNIEnv* env, jobject self, jlong handle) { relay_audio* a=(relay_audio*)handle; if(a&&a->has_sound) ma_sound_start(&a->sound); }
JNIEXPORT void JNICALL Java_dev_relay_music_desktop_NativeAudio_pause(JNIEnv* env, jobject self, jlong handle) { relay_audio* a=(relay_audio*)handle; if(a&&a->has_sound) ma_sound_stop(&a->sound); }
JNIEXPORT void JNICALL Java_dev_relay_music_desktop_NativeAudio_seek(JNIEnv* env, jobject self, jlong handle, jlong ms) { relay_audio* a=(relay_audio*)handle; if(a&&a->has_sound) ma_sound_seek_to_pcm_frame(&a->sound, (ma_uint64)(ms * ma_engine_get_sample_rate(&a->engine) / 1000)); }
JNIEXPORT jlong JNICALL Java_dev_relay_music_desktop_NativeAudio_position(JNIEnv* env, jobject self, jlong handle) { relay_audio* a=(relay_audio*)handle; ma_uint64 f=0; if(!a||!a->has_sound) return 0; ma_sound_get_cursor_in_pcm_frames(&a->sound,&f); return (jlong)(f*1000/ma_engine_get_sample_rate(&a->engine)); }
JNIEXPORT jlong JNICALL Java_dev_relay_music_desktop_NativeAudio_duration(JNIEnv* env, jobject self, jlong handle) { relay_audio* a=(relay_audio*)handle; ma_uint64 f=0; if(!a||!a->has_sound) return 0; ma_sound_get_length_in_pcm_frames(&a->sound,&f); return (jlong)(f*1000/ma_engine_get_sample_rate(&a->engine)); }
JNIEXPORT void JNICALL Java_dev_relay_music_desktop_NativeAudio_speed(JNIEnv* env, jobject self, jlong handle, jfloat speed) { relay_audio* a=(relay_audio*)handle; if(a&&a->has_sound) ma_sound_set_pitch(&a->sound, speed); }
