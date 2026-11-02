package com.obms.repository;

import com.obms.domain.Enums;
import com.obms.domain.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    /**
     * Most recent live challenge for a username, purpose and target.
     *
     * <p>Only the newest unconsumed token is considered valid, so issuing a new
     * OTP implicitly supersedes any earlier one for the same operation.</p>
     */
    @Query("""
            select o from OtpToken o
            where o.username = :username
              and o.purpose = :purpose
              and (:targetRef is null or o.targetRef = :targetRef)
              and o.consumedAt is null
            order by o.createdAt desc
            limit 1
            """)
    Optional<OtpToken> findLatestLive(@Param("username") String username,
                                      @Param("purpose") Enums.OtpPurpose purpose,
                                      @Param("targetRef") String targetRef);

    /** Invalidates earlier challenges when a fresh OTP is issued. */
    @Modifying
    @Query("""
            update OtpToken o set o.consumedAt = :now
            where o.username = :username and o.purpose = :purpose and o.consumedAt is null
            """)
    int consumeOutstanding(@Param("username") String username,
                           @Param("purpose") Enums.OtpPurpose purpose,
                           @Param("now") Instant now);

    @Modifying
    @Query("delete from OtpToken o where o.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
