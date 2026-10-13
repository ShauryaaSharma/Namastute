package com.namastute.trips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

// Plain unit test: no Spring, no database. Runs in milliseconds.
class GeoTest {

    static final Point IGI_T3 = Geo.point(28.5555, 77.0865);
    static final Point CONNAUGHT_PLACE = Geo.point(28.6315, 77.2167);

    @Test
    void pointKeepsLatitudeAndLongitudeApart() {
        assertThat(Geo.lat(IGI_T3)).isEqualTo(28.5555);
        assertThat(Geo.lng(IGI_T3)).isEqualTo(77.0865);
        assertThat(IGI_T3.getX()).as("x is longitude").isEqualTo(77.0865);
    }

    @Test
    void airportToConnaughtPlaceIsAbout15KmStraightLine() {
        assertThat(Geo.distanceKm(IGI_T3, CONNAUGHT_PLACE)).isCloseTo(15.4, within(1.0));
    }

    @Test
    void distanceIsSymmetricAndZeroToItself() {
        assertThat(Geo.distanceKm(IGI_T3, CONNAUGHT_PLACE)).isEqualTo(Geo.distanceKm(CONNAUGHT_PLACE, IGI_T3));
        assertThat(Geo.distanceKm(IGI_T3, IGI_T3)).isZero();
    }
}
