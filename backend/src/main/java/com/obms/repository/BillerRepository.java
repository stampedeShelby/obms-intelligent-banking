package com.obms.repository;

import com.obms.domain.Biller;
import com.obms.domain.Enums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillerRepository extends JpaRepository<Biller, Long> {

    List<Biller> findByActiveTrueOrderByNameAsc();

    List<Biller> findByCategoryAndActiveTrue(Enums.BillerCategory category);

    Optional<Biller> findByCode(String code);
}
