package com.useranalyzer.spark.session;

import com.useranalyzer.constant.Constants;
import org.apache.spark.util.AccumulatorV2;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 Spark 累加器
 * 用于统计 Session 访问时长分布和访问步长分布
 *
 * 输入：String（统计键名）
 * 输出：Map<String, Long>（各键对应的计数）
 */
public class SessionAggrStatAccumulator extends AccumulatorV2<String, Map<String, Long>> {

    private Map<String, Long> map = new HashMap<>();

    /** 初始化所有统计键为 0 */
    public SessionAggrStatAccumulator() {
        init();
    }

    private void init() {
        map.put(Constants.FIELD_SESSION_COUNT,        0L);
        // 访问时长
        map.put(Constants.FIELD_VISIT_LENGTH_1S_3S,   0L);
        map.put(Constants.FIELD_VISIT_LENGTH_4S_6S,   0L);
        map.put(Constants.FIELD_VISIT_LENGTH_7S_9S,   0L);
        map.put(Constants.FIELD_VISIT_LENGTH_10S_30S, 0L);
        map.put(Constants.FIELD_VISIT_LENGTH_30S_60S, 0L);
        map.put(Constants.FIELD_VISIT_LENGTH_1M_3M,   0L);
        map.put(Constants.FIELD_VISIT_LENGTH_3M_10M,  0L);
        map.put(Constants.FIELD_VISIT_LENGTH_10M_30M, 0L);
        map.put(Constants.FIELD_VISIT_LENGTH_30M,     0L);
        // 访问步长
        map.put(Constants.FIELD_STEP_LENGTH_1_3,   0L);
        map.put(Constants.FIELD_STEP_LENGTH_4_6,   0L);
        map.put(Constants.FIELD_STEP_LENGTH_7_9,   0L);
        map.put(Constants.FIELD_STEP_LENGTH_10_30, 0L);
        map.put(Constants.FIELD_STEP_LENGTH_30_60, 0L);
        map.put(Constants.FIELD_STEP_LENGTH_60,    0L);
    }

    @Override
    public boolean isZero() {
        return map.values().stream().allMatch(v -> v == 0L);
    }

    @Override
    public AccumulatorV2<String, Map<String, Long>> copy() {
        SessionAggrStatAccumulator acc = new SessionAggrStatAccumulator();
        acc.map.putAll(this.map);
        return acc;
    }

    @Override
    public void reset() {
        map.clear();
        init();
    }

    @Override
    public void add(String key) {
        if (map.containsKey(key)) {
            map.merge(key, 1L, Long::sum);
        }
    }

    @Override
    public void merge(AccumulatorV2<String, Map<String, Long>> other) {
        other.value().forEach((k, v) -> map.merge(k, v, Long::sum));
    }

    @Override
    public Map<String, Long> value() {
        return map;
    }
}
