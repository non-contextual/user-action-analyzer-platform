package com.useranalyzer.backend.entity;

import javax.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "session_aggr_stat")
public class SessionAggrStat {

    @Id
    private Long taskId;

    @Column(name = "session_count")
    private Long sessionCount;

    @Column(name = "visit_length_1s_3s")
    private Long visitLength1s3s;

    @Column(name = "visit_length_4s_6s")
    private Long visitLength4s6s;

    @Column(name = "visit_length_7s_9s")
    private Long visitLength7s9s;

    @Column(name = "visit_length_10s_30s")
    private Long visitLength10s30s;

    @Column(name = "visit_length_30s_60s")
    private Long visitLength30s60s;

    @Column(name = "visit_length_1m_3m")
    private Long visitLength1m3m;

    @Column(name = "visit_length_3m_10m")
    private Long visitLength3m10m;

    @Column(name = "visit_length_10m_30m")
    private Long visitLength10m30m;

    @Column(name = "visit_length_30m")
    private Long visitLength30m;

    @Column(name = "step_length_1_3")
    private Long stepLength1_3;

    @Column(name = "step_length_4_6")
    private Long stepLength4_6;

    @Column(name = "step_length_7_9")
    private Long stepLength7_9;

    @Column(name = "step_length_10_30")
    private Long stepLength10_30;

    @Column(name = "step_length_30_60")
    private Long stepLength30_60;

    @Column(name = "step_length_60")
    private Long stepLength60;
}
