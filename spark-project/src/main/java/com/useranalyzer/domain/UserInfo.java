package com.useranalyzer.domain;

import java.io.Serializable;

/**
 * 用户基本信息实体
 * CSV: user_id,username,name,age,professional,city,sex
 */
public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public long   userId;
    public String username;
    public String name;
    public int    age;
    public String professional;
    public String city;
    public String sex;

    public static UserInfo fromCSV(String line) {
        String[] fields = line.split(",", -1);
        UserInfo u = new UserInfo();
        u.userId       = parseLong(fields[0]);
        u.username     = fields[1].trim();
        u.name         = fields[2].trim();
        u.age          = parseInt(fields[3]);
        u.professional = fields[4].trim();
        u.city         = fields[5].trim();
        u.sex          = fields[6].trim();
        return u;
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return -1L; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
