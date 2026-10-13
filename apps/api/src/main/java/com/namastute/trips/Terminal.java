package com.namastute.trips;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

// An airport terminal and its cab pickup point. Rows come from migration V3; the app only reads them.
@Entity
@Table(name = "terminals")
class Terminal {

    @Id
    private String id;   // e.g. DEL-T3

    private String airport;

    private String name;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)   // a point on the round Earth, not on a flat plane
    private Point pickup;

    private String pickupNote;

    protected Terminal() {
        // required by JPA
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }

    Point getPickup() {
        return pickup;
    }

    String getPickupNote() {
        return pickupNote;
    }
}
