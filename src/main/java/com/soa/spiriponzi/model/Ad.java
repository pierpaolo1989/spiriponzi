package com.soa.spiriponzi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name="ads")
public class Ad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String title;
    @Column
    private double reward;
    @Column(nullable = false, columnDefinition = "double precision default 0")
    private double budget;
    @Column
    private int timer;
    @Column
    private String url;
    @Column
    private boolean active;
    @ManyToOne
    private User proposer;
    @Column(name = "deleted", columnDefinition = "boolean default false")
    private boolean deleted = false;
    @Column(name = "abuse_count", nullable = false, columnDefinition = "integer default 0")
    private int abuseCount;
    @Column
    private LocalDate createdAt;
}
