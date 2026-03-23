package com.useranalyzer.backend.entity;

import javax.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "top10_category")
@IdClass(Top10CategoryId.class)
public class Top10Category {

    @Id
    private Long taskId;

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "click_count")
    private Long clickCount;

    @Column(name = "order_count")
    private Long orderCount;

    @Column(name = "pay_count")
    private Long payCount;
}

class Top10CategoryId implements java.io.Serializable {
    private Long taskId;
    private Long categoryId;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Top10CategoryId that = (Top10CategoryId) o;

        if (taskId != null ? !taskId.equals(that.taskId) : that.taskId != null) return false;
        return categoryId != null ? categoryId.equals(that.categoryId) : that.categoryId == null;
    }

    @Override
    public int hashCode() {
        int result = taskId != null ? taskId.hashCode() : 0;
        result = 31 * result + (categoryId != null ? categoryId.hashCode() : 0);
        return result;
    }
}
