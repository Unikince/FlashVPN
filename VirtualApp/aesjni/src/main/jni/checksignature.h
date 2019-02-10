//
// Created by wei on 16-12-4.
//

#define   LOG_TAG    "code"
#define   LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#ifndef AESJNIENCRYPT_SIGNACTURECHECK_H
#define AESJNIENCRYPT_SIGNACTURECHECK_H

/**
 * whatsclone -1048786450
 * domultiple.jks -1208429200
 * polestar.jks 1642031816
 * polestar-team.jks 1404035346
 * domultipe-new.jks 128670161
 * nova-vpn.jks -2145233489
 */
//合法的APP包名
//static const char *app_packageName = "com.androidyuan.aesjniencrypt";
//合法的hashcode -625644214:这个值是我生成的这个可以store文件的hash值
static const int app_signature_hash_code = -1085342784;

static const int signature_hash_codes[] = {-1273784504, -1048786450, -1208429200,
                                           1642031816, 1404035346, 128670161, -2145233489};

static const char* signature_sha1_codes[] = {
        "C074A141F112791F44D948B30D4364E736DA542A",
        "55B1DF321918B74E75683E9CD92C155476E74D5C", //superb
        "1EB8328D065270ECF1C47AB3789C8821451BFE4B", //what's clone
        "E9B1C446DB550C27B09C5ED28F37F5DF2CDCF4DC", //what's clone 64
        "D13E5C923C246F34801C247EB0279230A193846C", //whatsclone.jks
        "4175FEBFCB45D8794EAC0E9046C63B3FA67A425F", //domultiple.jks
        "CD10F93C52888A9200BF577CBF48C82FB18B1617", //polestar.jks
        "7F964242C609B5857BD1B4665CC62B1D7C3FB009", //polestar-team.jks
        "2CA149BC1AB9F433959B38EB02433EA751059BCE", //domultipe-new.jks
        "308E8326F5FAC9A48B14E0E0FFAF08A1DF4E5AB8", //nova-vpn.jks
};
/**
 * 校验APP 包名和签名是否合法
 *
 * 返回值为1 表示合法
 */
jint check_signature(JNIEnv *env, jobject thiz, jobject context);
jint check_signature_sha1(JNIEnv *env, jobject type, jobject context);

#endif //AESJNIENCRYPT_SIGNACTURECHECK_H
