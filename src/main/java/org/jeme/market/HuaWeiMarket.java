package org.jeme.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jeme.Upload;
import org.jeme.Utils;
import org.jeme.bean.HwUploadFileInfo;
import org.jeme.config.MarketConfig;
import org.jeme.config.PushConfig;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/***
 * https://developer.huawei.com/consumer/cn/doc/development/AppGallery-connect-Guides/agcapi-upload_appfile
 */
public class HuaWeiMarket extends BaseMarket {

    //    private final String DOMAIN = "https://www.huawei.com/auth/agc/publish";
    private final String DOMAIN = "https://connect-api.cloud.huawei.com/api/";
    private String token;
    private HwUploadFileInfo hwUploadFileInfo;

    // 创建一个Timer对象
    private Timer timer = new Timer();
    // 设置一个倒计时的时间，单位为秒
    private int countdown = 2 * 60;
    // 创建一个TimerTask对象，用于执行倒计时的任务
    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            // 每秒钟减少1
            countdown--;
            // 计算倒计时的分钟和秒数
            int minute = countdown / 60;
            int second = countdown % 60;
            // 打印输出剩余时间
            System.out.println("剩余时间：" + minute + "分" + second + "秒");
            // 如果倒计时结束，取消定时器
            if (countdown == 0) {
                timer.cancel();
            }
        }
    };

    public HuaWeiMarket(MarketConfig config, PushConfig pushConfig) {
        super(config, pushConfig);
    }

    @Override
    protected boolean pre() {
        token = getToken();
        if (Utils.isEmpty(token)) {
            return false;
        }
        return updateAppInfo();
    }

    @Override
    public String getUploadUrl() {
        hwUploadFileInfo = getUploadUrl2();
        if (hwUploadFileInfo == null) {
            return null;
        }
        return hwUploadFileInfo.uploadUrl;
    }


    @Override
    public String platformName() {
        return "HuaWei";
    }

    @Override
    public Map<String, Object> getPushRequestMap() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("authCode", hwUploadFileInfo.authCode);
        requestMap.put("fileCount", "1");
        requestMap.put("parseType", "1");
        requestMap.put("file", new File(getChannelApkPath()));
        return requestMap;
    }

    @Override
    protected Map<String, Object> getHeader() {
        Map<String, Object> headerMap = new HashMap<>();
        if (!Utils.isEmpty(token)) {
            headerMap.put("Authorization", "Bearer " + token);
        }
        headerMap.put("client_id", config.accessKey);
        headerMap.put("accept", "application/json");
        return headerMap;
    }

    @Override
    protected void sync(JsonObject response) {
        if (response == null) {
            return;
        }

        try {
            JsonObject result = response.get("result").getAsJsonObject();
            if (result == null) {
                return;
            }
            String code = result.get("resultCode").getAsString();
            if (!"0".equals(code)) {
                return;
            }
            if (!updateAppFileInfo(result)) {
                return;
            }

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("appid", config.appId);
            System.out.println("软件包采用异步解析方式，请您在传包后等候2分钟再调用提交发布接口。");
            try {
                // 指定定时器每隔1秒钟执行一次任务
                timer.schedule(task, 0, 1000);
                int millisecond = 2 * 60 * 1000;
                Thread.sleep(millisecond);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            Upload.post(DOMAIN + "/publish/v2/app-submit", requestMap, getHeader());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean updateAppFileInfo(JsonObject response) {
        try {
            JsonObject uploadFileRsp = response.get("UploadFileRsp").getAsJsonObject();
            if (uploadFileRsp == null) {
                return false;
            }
            JsonArray fileInfoList = uploadFileRsp.get("fileInfoList").getAsJsonArray();
            if (fileInfoList == null || fileInfoList.size() == 0) {
                return false;
            }
            JsonObject fileInfo = fileInfoList.get(0).getAsJsonObject();
            Map<String, Object> requestMap = new HashMap<>();
//            requestMap.put("appId", getConfig().appId);
            requestMap.put("fileType", 5);
            Map<String, Object> fileMap = new HashMap<>();
            fileMap.put("fileName", new File(getChannelApkPath()).getName());
            fileMap.put("fileDestUrl", fileInfo.get("fileDestUlr").getAsString());
            requestMap.put("files", fileMap);
            Upload.put(DOMAIN + "/publish/v2/app-file-info?appId=" + config.appId, requestMap, getHeader());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private String getToken() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("client_id", config.accessKey);
        requestMap.put("client_secret", config.accessSecret);
        requestMap.put("grant_type", "client_credentials");
        try {
            JsonObject response = Upload.postBodyJson(DOMAIN + "/oauth2/v1/token", requestMap);
            if (response != null) {
                return response.get("access_token").getAsString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private HwUploadFileInfo getUploadUrl2() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("appId", config.appId);
        requestMap.put("suffix", "apk");

        try {
            JsonObject response = Upload.get(DOMAIN + "/publish/v2/upload-url", requestMap, getHeader());
            if (response != null) {
                return new HwUploadFileInfo(response.get("authCode").getAsString(), response.get("uploadUrl").getAsString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean updateAppInfo() {
        if (Utils.isEmpty(token)) {
            return false;
        }

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("lang", "zh-CN");
        requestMap.put("newFeatures", pushConfig.updateDesc);

        try {
            JsonObject response = Upload.put(DOMAIN + "/publish/v2/app-language-info?appId=" + config.appId, requestMap, getHeader());
            if (response != null) {
                //ret:{"code":retcode, "msg": "description"}
                JsonObject ret = response.get("ret").getAsJsonObject();
                if (ret != null) {
                    int code = ret.get("code").getAsInt();
                    if (code == 0) {
                        return true;
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

}
