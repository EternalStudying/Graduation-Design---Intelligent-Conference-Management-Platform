package com.llf.enums;

/**
 * Reservation status values stored in DB.
 *
 * ACTIVE   : upcoming / ongoing reservations
 * ENDED    : finished reservations (end_time < now)
 * CANCELLED: cancelled by user/admin
 */
public final class ReservationStatus {
    private ReservationStatus() {}

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";
    public static final String CANCELLED = "CANCELLED";
}