package com.useranalyzer.backend.entity;

import javax.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "user_level_stat")
@IdClass(UserLevelStatId.class)
public class UserLevelStat {

    @Id
    @Column(name = "task_id")
    private Long taskId;

    @Id
    @Column(name = "user_level")
    private String userLevel;

    @Column(name = "user_count")
    private Long userCount;

    @Column(name = "avg_spend")
    private Double avgSpend;
}

class UserLevelStatId implements java.io.Serializable {
    private Long taskId;
    private String userLevel;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getUserLevel() { return userLevel; }
    public void setUserLevel(String userLevel) { this.userLevel = userLevel; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserLevelStatId that = (UserLevelStatId) o;
        if (taskId != null ? !taskId.equals(that.taskId) : that.taskId != null) return false;
        return userLevel != null ? userLevel.equals(that.userLevel) : that.userLevel == null;
    }

    @Override
    public int hashCode() {
        int result = taskId != null ? taskId.hashCode() : 0;
        result = 31 * result + (userLevel != null ? userLevel.hashCode() : 0);
        return result;
    }
}
