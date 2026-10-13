-- Reference data: the three IGI terminals and their cab pickup points.
-- Coordinates are APPROXIMATE (terminal buildings). The PM confirms the real app-cab pickup spots on site (design 1.3),
-- and the correction ships as a new migration (V4...), never by editing this file.
-- WKT points are POINT(longitude latitude): x before y.
insert into terminals (id, airport, name, pickup, pickup_note) values
  ('DEL-T1', 'DEL', 'Terminal 1', 'SRID=4326;POINT(77.1185 28.5662)', 'Approximate location, to be confirmed on site'),
  ('DEL-T2', 'DEL', 'Terminal 2', 'SRID=4326;POINT(77.0950 28.5550)', 'Approximate location, to be confirmed on site'),
  ('DEL-T3', 'DEL', 'Terminal 3', 'SRID=4326;POINT(77.0865 28.5555)', 'Approximate location, to be confirmed on site');
