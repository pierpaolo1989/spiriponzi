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
@Table(name="user_ads")
public class UserAd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    LocalDate completedAt;

    @ManyToOne
    private User user;

    @ManyToOne
    private Ad ad;

}
