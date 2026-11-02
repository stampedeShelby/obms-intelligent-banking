package com.obms.repository;

import com.obms.domain.Beneficiary;
import com.obms.domain.Enums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByOwnerCustomerIdAndStatusNot(Long customerId, Enums.BeneficiaryStatus excluded);

    Optional<Beneficiary> findByIdAndOwnerCustomerId(Long id, Long customerId);

    Optional<Beneficiary> findByOwnerCustomerIdAndAccountNumber(Long customerId, String accountNumber);

    boolean existsByOwnerCustomerIdAndAccountNumberAndStatusNot(
            Long customerId, String accountNumber, Enums.BeneficiaryStatus excluded);
}
