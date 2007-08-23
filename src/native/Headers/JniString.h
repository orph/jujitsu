#ifndef JNI_STRING_H_included
#define JNI_STRING_H_included

#include <jni.h>
#include <stdexcept>
#include <string>

/**
 * Copies the characters from a jstring and makes them available.
 */
class JniString {
    std::string m_utf8;
    
public:
    JniString(JNIEnv* env, jstring instance) {
        const char* utf8Chars = env->GetStringUTFChars(instance, 0);
        if (utf8Chars == 0) {
            throw std::runtime_error("GetStringUTFChars returned 0");
        }
        m_utf8.assign(utf8Chars);
        env->ReleaseStringUTFChars(instance, utf8Chars);
    }
    
    std::string str() const {
        return m_utf8;
    }
};

#endif
