package com.noobdevs.talentbridge_ats.models;

import com.noobdevs.talentbridge_ats.enums.JobStatus;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Entity
@Data
@Table(name = "job_table")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private String work_mode;

    @Column(nullable = false)
    private String employment_type;

    @Column(nullable = false)
    private String salary_range;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String required_skills;

    @Column(nullable = false)
    private LocalDate closing_date;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id", nullable = true)
    private Recruiter createdBy;

    @OneToMany(mappedBy = "job", cascade = CascadeType.REMOVE)
    private List<Application> applications;
}
