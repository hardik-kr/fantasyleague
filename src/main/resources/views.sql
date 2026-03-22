-- Run this in the fantasyleague database to create VIEWs pointing to cricketapi tables.
-- These VIEWs allow fantasyleague JPA entities to resolve cross-DB references via SELECT.

-- Drop old VIEWs if they exist
DROP VIEW IF EXISTS fantasyleague.matches;
DROP VIEW IF EXISTS fantasyleague.team;
DROP VIEW IF EXISTS fantasyleague.player;

-- Create updated VIEWs for new cricketapi schema

CREATE VIEW fantasyleague.matches AS
SELECT id, date, is_match_complete, matchtype, result, time, timezone, toss, venue, league_id, mom_player_id, teama_id, teamb_id
FROM cricketapi.matches;

CREATE VIEW fantasyleague.team AS
SELECT id, name, short_name, league_id
FROM cricketapi.teams;

CREATE VIEW fantasyleague.player AS
SELECT id, name, role
FROM cricketapi.players;

-- New VIEWs for the new tables
CREATE VIEW fantasyleague.leagues AS
SELECT id, name, short_name, format, country
FROM cricketapi.leagues;

CREATE VIEW fantasyleague.player_team AS
SELECT player_id, team_id, is_active
FROM cricketapi.player_team;

INSERT INTO fantasyleague.fantasy_player_config (id, player_id, league_id, credit, type, overseas, uncapped, is_active) VALUES
(100001, 8216,  1, 9.0,  2, 0, 0, 1),   -- TOM LATHAM, KEEPER
(100002, 8530,  1, 7.5,  3, 0, 0, 1),   -- PRENELAN SUBRAYEN, ALLROUNDER
(100003, 8983,  1, 9.5,  3, 1, 0, 1),   -- JAMES NEESHAM, ALLROUNDER
(100004, 9441,  1, 10.0, 1, 1, 0, 1),   -- KYLE JAMIESON, BOWLER
(100005, 9587,  1, 7.0,  0, 0, 0, 1),   -- JASON SMITH, BATTER
(100006, 9720,  1, 8.5,  1, 0, 0, 1),   -- KESHAV MAHARAJ, BOWLER
(100007, 9838,  1, 10.5, 2, 1, 0, 1),   -- DEVON CONWAY, KEEPER
(100008, 10100, 1, 9.5,  3, 1, 0, 1),   -- MITCHELL SANTNER, ALLROUNDER
(100009, 10187, 1, 8.0,  3, 0, 0, 1),   -- GEORGE LINDE, ALLROUNDER
(100010, 10692, 1, 10.0, 1, 1, 0, 1),   -- LOCKIE FERGUSON, BOWLER
(100011, 10697, 1, 7.5,  3, 0, 0, 1),   -- COLE MCCONCHIE, ALLROUNDER
(100012, 10698, 1, 8.0,  2, 0, 0, 1),   -- DANE CLEAVER, KEEPER
(100013, 10717, 1, 8.5,  2, 0, 0, 1),   -- TOM BLUNDELL, KEEPER
(100014, 10729, 1, 7.5,  3, 0, 0, 1),   -- JOSH CLARKSON, ALLROUNDER
(100015, 10745, 1, 7.0,  3, 0, 0, 1),   -- NICK KELLY, ALLROUNDER
(100016, 11170, 1, 8.0,  3, 1, 0, 1),   -- NATHAN SMITH, ALLROUNDER
(100017, 11178, 1, 8.5,  1, 0, 0, 1),   -- BEN SEARS, BOWLER
(100018, 11196, 1, 9.0,  0, 0, 0, 1),   -- TONY DE ZORZI, BATTER
(100019, 11200, 1, 9.0,  3, 0, 0, 1),   -- WIAAN MULDER, ALLROUNDER
(100020, 11208, 1, 8.0,  1, 0, 0, 1),   -- LUTHO SIPAMLA, BOWLER
(100021, 13100, 1, 8.5,  1, 0, 0, 1),   -- OTTNEIL BAARTMAN, BOWLER
(100022, 13320, 1, 9.5,  1, 1, 0, 1),   -- Gerald Coetzee, BOWLER
(100023, 13339, 1, 7.5,  0, 0, 0, 1),   -- KATENE D CLARKE, BATTER
(100024, 14797, 1, 7.0,  2, 0, 0, 1),   -- RUBIN HERMANN, KEEPER
(100025, 15769, 1, 7.5,  1, 0, 0, 1),   -- JAYDEN LENNOX, BOWLER
(100026, 18687, 1, 8.0,  1, 0, 0, 1),   -- ANDILE SIMELANE, BOWLER
(100027, 22081, 1, 7.0,  0, 0, 0, 1),   -- TIM ROBINSON, BATTER
(100028, 24391, 1, 7.5,  3, 0, 0, 1),   -- ZAKARY FOULKES, ALLROUNDER
(100029, 24622, 1, 8.0,  2, 1, 0, 1),   -- CONNOR ESTERHUIZEN, KEEPER
(100030, 50444, 1, 7.5,  3, 0, 0, 1),   -- DIAN FORRESTER, ALLROUNDER
(100031, 52428, 1, 7.0,  3, 0, 0, 1),   -- BEVON JACOBS, ALLROUNDER
(100032, 53408, 1, 8.5,  1, 0, 0, 1);   -- NQOBANI MOKOENA, BOWLER

-- CREATING USER
-- curl -X POST http://localhost:9095/test/seed