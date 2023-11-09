package org.jeme.bean;

public class OppoUploadFileInfo {

    public String sign;
    public String uploadUrl;

    public OppoUploadFileInfo(String sign, String uploadUrl) {
        this.sign = sign;
        this.uploadUrl = uploadUrl;
    }

    @Override
    public String toString() {
        return "OppoUploadFileInfo{" +
                "sign='" + sign + '\'' +
                ", uploadUrl='" + uploadUrl + '\'' +
                '}';
    }
}