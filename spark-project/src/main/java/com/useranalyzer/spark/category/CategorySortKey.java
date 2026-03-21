package com.useranalyzer.spark.category;

import java.io.Serializable;

/**
 * 品类二次排序 Key
 * 排序规则（降序）: 点击次数 > 下单次数 > 支付次数
 */
public class CategorySortKey implements Comparable<CategorySortKey>, Serializable {
    private static final long serialVersionUID = 1L;

    private final long clickCount;
    private final long orderCount;
    private final long payCount;

    public CategorySortKey(long clickCount, long orderCount, long payCount) {
        this.clickCount = clickCount;
        this.orderCount = orderCount;
        this.payCount   = payCount;
    }

    @Override
    public int compareTo(CategorySortKey other) {
        if (this.clickCount != other.clickCount) {
            return Long.compare(this.clickCount, other.clickCount);  // 升序，takeOrdered 取大
        }
        if (this.orderCount != other.orderCount) {
            return Long.compare(this.orderCount, other.orderCount);
        }
        return Long.compare(this.payCount, other.payCount);
    }

    public long getClickCount() { return clickCount; }
    public long getOrderCount() { return orderCount; }
    public long getPayCount()   { return payCount;   }

    @Override
    public String toString() {
        return "CategorySortKey{click=" + clickCount + ", order=" + orderCount + ", pay=" + payCount + "}";
    }
}
