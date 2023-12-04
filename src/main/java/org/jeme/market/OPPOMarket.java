package org.jeme.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jeme.Upload;
import org.jeme.Utils;
import org.jeme.bean.ApkInfo;
import org.jeme.bean.OppoUploadFileInfo;
import org.jeme.config.MarketConfig;
import org.jeme.config.PushConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/***
 * https://open.oppomobile.com/new/developmentDoc/info?id=10998
 */
public class OPPOMarket extends BaseMarket {
    private final String DOMAIN = "https://oop-openapi-cn.heytapmobi.com";
    private String token;
    private OppoUploadFileInfo oppoUploadFileInfo;

    public OPPOMarket(MarketConfig config, PushConfig pushConfig) {
        super(config, pushConfig);
    }

    @Override
    protected boolean pre() {
        token = getToken();
        if (Utils.isEmpty(token)) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean preQuery() {
        token = getToken();
        if (Utils.isEmpty(token)) {
            return false;
        }
        return queryAppInfo();
    }

    @Override
    protected void sync(JsonObject response) {
        if (response == null) {
            return;
        }

        try {
            int errno = response.get("errno").getAsInt();
            if (errno != 0) {
                return;
            }
            JsonObject data = response.get("data").getAsJsonObject();
            JsonObject appInfos = getAppInfos();
            Upload.post(DOMAIN + "/resource/v1/app/upd", getSyncRequestMap(appInfos, data), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 查询普通包详情
     */
    private JsonObject getAppInfos() {
        try {
            ApkInfo apkInfo = readApkInfo();
            Map<String, Object> params = getPublicParams();
            params.put("pkg_name", apkInfo.packageName);
            String sign = sign(config.accessSecret, params);
            params.put("api_sign", sign);
            return Upload.get(DOMAIN + "/resource/v1/app/info", params, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Map<String, Object> getSyncRequestMap(JsonObject response, JsonObject apk) {
        Map<String, Object> params = null;
        try {
            ApkInfo apkInfo = readApkInfo();
            JsonObject data = response.get("data").getAsJsonObject();
            params = getPublicParams();
            params.put("pkg_name", data.get("pkg_name").getAsString());
            params.put("version_code", apkInfo.versionCode);
            //apk 包信息
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("url", apk.get("url").getAsString());
            jsonObject.addProperty("md5", apk.get("md5").getAsString());
            jsonObject.addProperty("cpu_code", 0);
            JsonArray apks = new JsonArray();
            apks.add(jsonObject);
            params.put("apk_url", apks);
            params.put("app_name", data.get("app_name").getAsString());
            params.put("second_category_id", data.get("second_category_id").getAsInt());
            params.put("third_category_id", data.get("third_category_id").getAsInt());
            params.put("summary", data.get("summary").getAsString());
            params.put("detail_desc", data.get("detail_desc").getAsString());
            // 版本说明，不少于 5 个字
            params.put("update_desc", pushConfig.updateDesc);
            params.put("privacy_source_url", data.get("privacy_source_url").getAsString());
            params.put("icon_url", data.get("icon_url").getAsString());
            params.put("pic_url", data.get("pic_url").getAsString());
            //实时上架
            params.put("online_type", 1);
            params.put("test_desc", data.get("test_desc").getAsString());
            params.put("electronic_cert_url", data.get("electronic_cert_url").getAsString());
            params.put("copyright_url", data.get("copyright_url").getAsString());
            params.put("icp_url", data.get("icp_url").getAsString());
            params.put("business_username", data.get("business_username").getAsString());
            params.put("business_email", data.get("business_email").getAsString());
            params.put("business_mobile", data.get("business_mobile").getAsString());
            params.put("age_level", data.get("age_level").getAsInt());
            params.put("adaptive_equipment", data.get("adaptive_equipment").getAsInt());
            // 签名参数
            params.put("api_sign", sign(config.accessSecret, params));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return params;
    }

    private String getToken() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("client_id", config.accessKey);
        requestMap.put("client_secret", config.accessSecret);
        try {
            JsonObject response = Upload.get(DOMAIN + "/developer/v1/token", requestMap, null);
            if (response != null) {
                JsonObject data = response.get("data").getAsJsonObject();
                return data.get("access_token").getAsString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /***
     * 获取公共参数
     */
    private Map<String, Object> getPublicParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("access_token", token);
        params.put("timestamp", System.currentTimeMillis() / 1000);
        return params;
    }

    @Override
    protected String getUploadUrl() {
        oppoUploadFileInfo = getUploadUrl2();
        if (oppoUploadFileInfo == null) {
            return null;
        }
        return oppoUploadFileInfo.uploadUrl;
    }

    @Override
    public String platformName() {
        return "OPPO";
    }

    @Override
    protected Map<String, Object> getPushRequestMap() {
        ApkInfo apkInfo = readApkInfo();
        if (apkInfo == null) {
            System.out.println("读取App信息失败");
            return null;
        }
        try {
            Map<String, Object> requestMap = getPublicParams();
            requestMap.put("type", "apk");
            requestMap.put("sign", oppoUploadFileInfo.sign);
            // 计算签名
            String sign = sign(config.accessSecret, requestMap);
            requestMap.put("api_sign", sign);
            //file 不参与sign计算，需要在sign计算后加入
            requestMap.put("file", new File(getChannelApkPath()));
            return requestMap;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private OppoUploadFileInfo getUploadUrl2() {
        try {
            // 公共参数
            Map<String, Object> requestMap = getPublicParams();
            // 计算签名
            String sign = sign(config.accessSecret, requestMap);
            requestMap.put("api_sign", sign);

            JsonObject response = Upload.get(DOMAIN + "/resource/v1/upload/get-upload-url", requestMap, null);
            if (response != null) {
                JsonObject data = response.get("data").getAsJsonObject();
                return new OppoUploadFileInfo(data.get("sign").getAsString(), data.get("upload_url").getAsString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 对请求参数进行签名
     *
     * @param secret
     * @param paramsMap
     * @return String
     * @throws IOException
     */
    private static String sign(String secret, Map<String, Object> paramsMap) throws IOException {
        List keysList = new ArrayList<>(paramsMap.keySet());
        Collections.sort(keysList);
        StringBuilder sb = new StringBuilder();
        List<String> paramList = new ArrayList<>();
        for (Object key : keysList) {
            Object object = paramsMap.get(key);
            if (object == null) {
                continue;
            }
            String value = key + "=" + object;
            paramList.add(value);
        }
        String signStr = String.join("&", paramList);
        return hmacSHA256(signStr, secret);
    }

    /**
     * HMAC_SHA256 计算签名
     *
     * @param data 需要加密的参数
     * @param key  签名密钥
     * @return String 返回加密后字符串
     */
    public static String hmacSHA256(String data, String key) {
        try {
            byte[] secretByte = key.getBytes(Charset.forName("UTF-8"));
            SecretKeySpec signingKey = new SecretKeySpec(secretByte, "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] dataByte = data.getBytes(Charset.forName("UTF-8"));
            byte[] by = mac.doFinal(dataByte);
            return byteArr2HexStr(by);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 字节数组转换为十六进制
     *
     * @param bytes
     * @return String
     */
    private static String byteArr2HexStr(byte[] bytes) {
        int length = bytes.length;
        // 每个byte用两个字符才能表示，所以字符串的长度是数组长度的两倍
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            // 将得到的字节转16进制
            String strHex = Integer.toHexString(bytes[i] & 0xFF);
            // 每个字节由两个字符表示，位数不够，高位补0
            sb.append((strHex.length() == 1) ? "0" + strHex : strHex);
        }
        return sb.toString();
    }

    private boolean queryAppInfo() {
        JsonObject response = getAppInfos();
        if (response != null) {
            int errno = response.get("errno").getAsInt();
            if (errno == 0) {
                JsonObject result = response.get("data").getAsJsonObject();
                System.out.println("版本号：" + result.get("version_name").getAsString());
                System.out.println("审核状态：" + result.get("audit_status_name").getAsString());
                System.out.println("审核拒绝原因：" + result.get("refuse_reason").getAsString());
                System.out.println("修改建议：" + result.get("refuse_advice").getAsString());
                return true;
            }
        }
        return false;
    }
}
