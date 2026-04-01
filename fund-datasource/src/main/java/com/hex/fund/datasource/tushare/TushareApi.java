package com.hex.fund.datasource.tushare;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tushare HTTP 客户端，通过 POST JSON 调用 Tushare Pro 接口。
 */
@Slf4j
@Component
public class TushareApi {

    private static final String API_URL = "https://api.tushare.pro";
    private static final int TIMEOUT = 15_000;

    @Value("${datasource.tushare.token:}")
    private String token;

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    /**
     * 获取基金净值数据。
     */
    public String fetchFundNav(String fundCode, String startDate, String endDate) {
        JSONObject params = JSONUtil.createObj()
                .set("ts_code", fundCode).set("start_date", startDate).set("end_date", endDate);
        return callApi("fund_nav", params, "ts_code,ann_date,nav_date,unit_nav,accum_nav");
    }

    /**
     * 获取基金基本信息。
     */
    public String fetchFundBasic(String fundCode) {
        JSONObject params = JSONUtil.createObj().set("ts_code", fundCode);
        return callApi("fund_basic", params,
                "ts_code,name,management,custodian,fund_type,found_date,due_date,status");
    }

    public boolean ping() {
        try {
            String result = fetchFundBasic("000001.OF");
            return result != null && result.contains("\"code\":0");
        } catch (Exception e) {
            log.warn("Tushare 连通性检测失败: {}", e.getMessage());
            return false;
        }
    }

    private String callApi(String apiName, JSONObject params, String fields) {
        JSONObject body = JSONUtil.createObj()
                .set("api_name", apiName).set("token", token)
                .set("params", params).set("fields", fields);
        return HttpRequest.post(API_URL).body(body.toString())
                .timeout(TIMEOUT).execute().body();
    }
}
