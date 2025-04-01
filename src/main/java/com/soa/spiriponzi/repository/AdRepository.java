package com.soa.spiriponzi.repository;

import com.soa.spiriponzi.model.Ad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Long> {

	List<Ad> findByActiveTrue();
}
