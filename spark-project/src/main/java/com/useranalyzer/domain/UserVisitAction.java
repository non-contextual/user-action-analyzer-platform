package com.useranalyzer.domain;

import java.io.Serializable;

/**
 * 用户访问行为实体
 * CSV: date,user_id,session_id,page_id,action_time,search_keyword,
 *      click_category_id,click_product_id,order_category_ids,order_product_ids,
 *      pay_category_ids,pay_product_ids,city_id
 */
public class UserVisitAction implements Serializable {
    private static final long serialVersionUID = 1L;

    public String date;
    public long   userId;
    public String sessionId;
    public long   pageId;
    public String actionTime;
    public String searchKeyword;
    public long   clickCategoryId;
    public long   clickProductId;
    public String orderCategoryIds;
    public String orderProductIds;
    public String payCategoryIds;
    public String payProductIds;
    public long   cityId;

    public static UserVisitAction fromCSV(String line) {
        String[] fields = line.split(",", -1);
        UserVisitAction a = new UserVisitAction();
        a.date             = fields[0].trim();
        a.userId           = parseLong(fields[1]);
        a.sessionId        = fields[2].trim();
        a.pageId           = parseLong(fields[3]);
        a.actionTime       = fields[4].trim();
        a.searchKeyword    = fields[5].trim();
        a.clickCategoryId  = parseLong(fields[6]);
        a.clickProductId   = parseLong(fields[7]);
        a.orderCategoryIds = fields[8].trim();
        a.orderProductIds  = fields[9].trim();
        a.payCategoryIds   = fields[10].trim();
        a.payProductIds    = fields[11].trim();
        a.cityId           = parseLong(fields[12]);
        return a;
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return -1L; }
    }

    @Override
    public String toString() {
        return "UserVisitAction{sessionId=" + sessionId + ", userId=" + userId
                + ", actionTime=" + actionTime + "}";
    }
}
