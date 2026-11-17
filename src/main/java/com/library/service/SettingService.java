package com.library.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.library.entity.Setting;
import com.library.mapper.SettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 借阅规则与系统设置（可在“系统设置”页覆盖） */
@Service
@RequiredArgsConstructor
public class SettingService {

    public static final String LOAN_DAYS_STUDENT = "loan_days_student";
    public static final String LOAN_DAYS_TEACHER = "loan_days_teacher";
    public static final String LOAN_DAYS_PUBLIC = "loan_days_public";
    public static final String MAX_BORROW_STUDENT = "max_borrow_student";
    public static final String MAX_BORROW_TEACHER = "max_borrow_teacher";
    public static final String MAX_BORROW_PUBLIC = "max_borrow_public";
    public static final String RENEW_LIMIT = "renew_limit";
    public static final String RENEW_DAYS = "renew_days";
    public static final String FINE_PER_DAY = "fine_per_day";
    public static final String MAX_FINE = "max_fine";
    public static final String BLOCK_FINE = "block_fine";

    private static final Map<String, String> DEFAULTS = defaults();

    private static Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(LOAN_DAYS_STUDENT, "30");
        map.put(LOAN_DAYS_TEACHER, "60");
        map.put(LOAN_DAYS_PUBLIC, "15");
        map.put(MAX_BORROW_STUDENT, "5");
        map.put(MAX_BORROW_TEACHER, "10");
        map.put(MAX_BORROW_PUBLIC, "3");
        map.put(RENEW_LIMIT, "1");
        map.put(RENEW_DAYS, "15");
        map.put(FINE_PER_DAY, "0.50");
        map.put(MAX_FINE, "50.00");
        map.put(BLOCK_FINE, "10.00");
        return java.util.Collections.unmodifiableMap(map);
    }

    private final SettingMapper settingMapper;

    public Map<String, String> all() {
        Map<String, String> result = new LinkedHashMap<>(DEFAULTS);
        for (Setting row : settingMapper.selectList(null)) {
            result.put(row.getK(), row.getV());
        }
        return result;
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(all().getOrDefault(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public BigDecimal getDecimal(String key, String fallback) {
        try {
            return new BigDecimal(all().getOrDefault(key, fallback).trim());
        } catch (NumberFormatException e) {
            return new BigDecimal(fallback);
        }
    }

    public int loanDays(String readerType) {
        return switch (readerType == null ? "PUBLIC" : readerType.toUpperCase()) {
            case "STUDENT" -> getInt(LOAN_DAYS_STUDENT, 30);
            case "TEACHER" -> getInt(LOAN_DAYS_TEACHER, 60);
            default -> getInt(LOAN_DAYS_PUBLIC, 15);
        };
    }

    public int maxBorrow(String readerType) {
        return switch (readerType == null ? "PUBLIC" : readerType.toUpperCase()) {
            case "STUDENT" -> getInt(MAX_BORROW_STUDENT, 5);
            case "TEACHER" -> getInt(MAX_BORROW_TEACHER, 10);
            default -> getInt(MAX_BORROW_PUBLIC, 3);
        };
    }

    public Map<String, String> save(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!DEFAULTS.containsKey(entry.getKey())) {
                continue;
            }
            Setting existing = settingMapper.selectOne(Wrappers.<Setting>lambdaQuery()
                    .eq(Setting::getK, entry.getKey()).last("limit 1"));
            if (existing == null) {
                Setting row = new Setting();
                row.setK(entry.getKey());
                row.setV(entry.getValue() == null ? "" : entry.getValue().trim());
                settingMapper.insert(row);
            } else {
                Setting row = new Setting();
                row.setId(existing.getId());
                row.setV(entry.getValue() == null ? "" : entry.getValue().trim());
                settingMapper.updateById(row);
            }
        }
        return all();
    }

    public void ensureDefaults() {
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            if (settingMapper.selectCount(Wrappers.<Setting>lambdaQuery().eq(Setting::getK, entry.getKey())) == 0) {
                Setting row = new Setting();
                row.setK(entry.getKey());
                row.setV(entry.getValue());
                settingMapper.insert(row);
            }
        }
    }
}
