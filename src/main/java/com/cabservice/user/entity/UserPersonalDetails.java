package com.cabservice.user.entity;

import com.cabservice.common.util.AppConstant;
import com.cabservice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sensitive identity data, kept apart from User so it is never loaded by accident.
 * A row exists only once a person has been verified (required for drivers).
 *
 * govIdHash must be an HMAC (keyed hash) of the ID number, never the raw
 * Aadhaar/PAN. The unique constraint means one government ID can never back
 * two accounts, which is what makes "block by Aadhaar/PAN" effective.
 */
@Entity
@Table(name = "user_personal_details")
@Getter
@Setter
@NoArgsConstructor
public class UserPersonalDetails extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "gov_id_type", nullable = false, length = 10)
    private AppConstant.GovIdType govIdType;

    @Column(name = "gov_id_hash", nullable = false, unique = true, length = 64)
    private String govIdHash;

    // Last 4 digits, for display and support only
    @Column(name = "gov_id_last4", nullable = false, length = 4)
    private String govIdLast4;

}