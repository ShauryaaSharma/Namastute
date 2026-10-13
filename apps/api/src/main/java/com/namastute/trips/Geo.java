package com.namastute.trips;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

// Small helpers for points on Earth. Watch the order: JTS and PostGIS use (x, y) = (longitude, latitude).
final class Geo {

    // shortcut: straight-line km × 1.4 instead of real road distance; replace with routing data (Proposed Stash idea 1)
    static final double ROAD_FACTOR = 1.4;

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private Geo() {}

    static Point point(double lat, double lng) {
        return WGS84.createPoint(new Coordinate(lng, lat));   // x = longitude, y = latitude
    }

    static double lat(Point p) {
        return p.getY();
    }

    static double lng(Point p) {
        return p.getX();
    }

    // Haversine formula: great-circle distance between two points on a sphere
    static double distanceKm(Point a, Point b) {
        double dLat = Math.toRadians(lat(b) - lat(a));
        double dLng = Math.toRadians(lng(b) - lng(a));
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat(a))) * Math.cos(Math.toRadians(lat(b))) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
