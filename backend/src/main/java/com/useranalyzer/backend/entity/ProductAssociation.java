package com.useranalyzer.backend.entity;

import javax.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "product_association")
@IdClass(ProductAssociationId.class)
public class ProductAssociation {

    @Id
    @Column(name = "task_id")
    private Long taskId;

    @Id
    @Column(name = "antecedent", length = 500)
    private String antecedent;

    @Id
    @Column(name = "consequent", length = 500)
    private String consequent;

    @Column(name = "support")
    private Double support;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "lift")
    private Double lift;
}

class ProductAssociationId implements java.io.Serializable {
    private Long taskId;
    private String antecedent;
    private String consequent;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getAntecedent() { return antecedent; }
    public void setAntecedent(String antecedent) { this.antecedent = antecedent; }
    public String getConsequent() { return consequent; }
    public void setConsequent(String consequent) { this.consequent = consequent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductAssociationId that = (ProductAssociationId) o;
        if (taskId != null ? !taskId.equals(that.taskId) : that.taskId != null) return false;
        if (antecedent != null ? !antecedent.equals(that.antecedent) : that.antecedent != null) return false;
        return consequent != null ? consequent.equals(that.consequent) : that.consequent == null;
    }

    @Override
    public int hashCode() {
        int result = taskId != null ? taskId.hashCode() : 0;
        result = 31 * result + (antecedent != null ? antecedent.hashCode() : 0);
        result = 31 * result + (consequent != null ? consequent.hashCode() : 0);
        return result;
    }
}
