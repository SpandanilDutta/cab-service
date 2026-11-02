package com.cabservice.common.util;

public class AppConstant {

    public enum Role {
        RIDER,
        DRIVER,
        ADMIN
    }

    public enum GovIdType {
        AADHAAR,
        PAN,
        PASSPORT,
        VOTER_ID,
        DRIVING_LICENSE,
        RATION_CARD,
        NREGA_JOB_CARD,
        OTHER
    }

    public enum AccountStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED
    }
}
