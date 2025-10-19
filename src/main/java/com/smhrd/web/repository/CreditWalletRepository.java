package com.smhrd.web.repository;

import com.smhrd.web.entity.CreditWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditWalletRepository extends JpaRepository<CreditWallet, Long> {

    @Modifying
    @Query("UPDATE CreditWallet c SET c.credit = c.credit + :amount WHERE c.userIdx = :userIdx")
    void addCredit(Long userIdx, Integer amount);

    @Modifying
    @Query("UPDATE CreditWallet c SET c.golden = c.golden + :amount WHERE c.userIdx = :userIdx")
    void addGolden(Long userIdx, Integer amount);
}
