package com.namastute.trips;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

// "I'll be outside terminal X at time T, going to D." One row of trip_requests.
@Entity
@Table(name = "trip_requests")
class TripRequest {

    // Names match the Postgres enum trip_status exactly (lower case), so they can be stored as-is
    enum Status { searching, grouped, cancelled, expired, done }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private String terminalId;

    private String flightNo;

    private Instant exitAt;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    private Point dest;   // exact destination: never shown to other users

    private String destArea;   // what others will see, e.g. "DLF Phase 3, Gurugram"

    private float soloKm;   // estimated road km from the pickup point, used later to split the fare

    private short seats;

    private short bags;

    private boolean womenOnly;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)   // the column is a Postgres enum type (trip_status), not text
    private Status status;

    protected TripRequest() {
        // required by JPA
    }

    TripRequest(UUID userId, String terminalId, String flightNo, Instant exitAt, Point dest, String destArea,
                float soloKm, short seats, short bags, boolean womenOnly) {
        this.userId = userId;
        this.terminalId = terminalId;
        this.flightNo = flightNo;
        this.exitAt = exitAt;
        this.dest = dest;
        this.destArea = destArea;
        this.soloKm = soloKm;
        this.seats = seats;
        this.bags = bags;
        this.womenOnly = womenOnly;
        this.status = Status.searching;
    }

    UUID getId() { return id; }
    UUID getUserId() { return userId; }
    String getTerminalId() { return terminalId; }
    String getFlightNo() { return flightNo; }
    Instant getExitAt() { return exitAt; }
    Point getDest() { return dest; }
    String getDestArea() { return destArea; }
    float getSoloKm() { return soloKm; }
    short getSeats() { return seats; }
    short getBags() { return bags; }
    boolean isWomenOnly() { return womenOnly; }
    Status getStatus() { return status; }
}
