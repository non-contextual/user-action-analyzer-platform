package com.useranalyzer.backend.entity;

import javax.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "page_convert_rate")
@IdClass(PageConvertRateId.class)
public class PageConvertRate {

    @Id
    private Long taskId;

    @Id
    @Column(name = "page_flow")
    private String pageFlow;

    @Column(name = "convert_rate")
    private Double convertRate;
}

class PageConvertRateId implements java.io.Serializable {
    private Long taskId;
    private String pageFlow;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getPageFlow() {
        return pageFlow;
    }

    public void setPageFlow(String pageFlow) {
        this.pageFlow = pageFlow;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PageConvertRateId that = (PageConvertRateId) o;

        if (taskId != null ? !taskId.equals(that.taskId) : that.taskId != null) return false;
        return pageFlow != null ? pageFlow.equals(that.pageFlow) : that.pageFlow == null;
    }

    @Override
    public int hashCode() {
        int result = taskId != null ? taskId.hashCode() : 0;
        result = 31 * result + (pageFlow != null ? pageFlow.hashCode() : 0);
        return result;
    }
}
