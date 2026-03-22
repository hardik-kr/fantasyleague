#!/usr/bin/env python3
"""
Fantasy League Player Points Verifier
======================================
Fetches scorecard from cricketapi + player points from fantasyleague,
independently calculates expected points using rules.txt logic,
and compares against the actual service result.

Usage:
    python3 scripts/verify-points.py <matchId>
    python3 scripts/verify-points.py 118919
"""

import sys
import json
import requests

CRICKETAPI_BASE = "http://localhost:9090"
CRICKETAPI_TOKEN = "fantasy-league-service-token"
FANTASY_BASE = "http://localhost:9095"

# ── Point Constants (matching FantasyPointSystem.java) ──

BATTING_RUN         = 1
BATTING_FOUR        = 1
BATTING_SIX         = 2
BATTING_30_BONUS    = 4
BATTING_50_BONUS    = 8
BATTING_100_BONUS   = 16
BATTING_DUCK        = -2

BOWLING_WICKET      = 25
BOWLING_MAIDEN      = 12
BOWLING_LBW_BOWLED  = 8
BOWLING_3W_BONUS    = 4
BOWLING_4W_BONUS    = 8
BOWLING_5W_BONUS    = 16

FIELDING_CATCH          = 8
FIELDING_3_CATCH_BONUS  = 4
FIELDING_STUMPING       = 12
FIELDING_RUNOUT_DIRECT  = 12
FIELDING_RUNOUT         = 6

IN_PLAYING11 = 4.0


def sr_points(sr):
    if sr < 50:   return -6
    if sr < 60:   return -4
    if sr < 70:   return -2
    if sr < 130:  return 0
    if sr < 150:  return 2
    if sr < 170:  return 4
    return 6


def economy_points(eco):
    if eco < 5:   return 6
    if eco < 6:   return 4
    if eco < 7:   return 2
    if eco < 10:  return 0
    if eco < 11:  return -2
    if eco < 12:  return -4
    return -6


ORDINAL_TO_ROLE = {0: "BATTER", 1: "BOWLER", 2: "KEEPER", 3: "ALLROUNDER"}


def is_bowler(role):
    if not role:
        return False
    r = str(role).strip().lower()
    return r == "bowler" or r == "1"


def is_dismissed(dismissal):
    if not dismissal or not dismissal.strip():
        return False
    d = dismissal.strip().lower()
    return d not in ("not out", "batting", "")


def calc_batter(bat, bowler):
    runs  = bat.get("runs", 0) or 0
    balls = bat.get("balls", 0) or 0
    fours = bat.get("fours", 0) or 0
    sixes = bat.get("sixes", 0) or 0
    dismissal = bat.get("dismissal", "")

    pts = 0.0
    breakdown = []

    run_pts = runs * BATTING_RUN
    pts += run_pts
    breakdown.append(f"runs: {runs}×{BATTING_RUN} = {run_pts}")

    four_pts = fours * BATTING_FOUR
    pts += four_pts
    if fours > 0:
        breakdown.append(f"fours: {fours}×{BATTING_FOUR} = {four_pts}")

    six_pts = sixes * BATTING_SIX
    pts += six_pts
    if sixes > 0:
        breakdown.append(f"sixes: {sixes}×{BATTING_SIX} = {six_pts}")

    dismissed = is_dismissed(dismissal)
    if not bowler and dismissed and runs == 0:
        pts += BATTING_DUCK
        breakdown.append(f"duck: {BATTING_DUCK}")

    if runs >= 100:
        pts += BATTING_100_BONUS
        breakdown.append(f"century: +{BATTING_100_BONUS}")
    elif runs >= 50:
        pts += BATTING_50_BONUS
        breakdown.append(f"half-century: +{BATTING_50_BONUS}")
    elif runs >= 30:
        pts += BATTING_30_BONUS
        breakdown.append(f"30-bonus: +{BATTING_30_BONUS}")

    if not bowler and (balls >= 10 or runs >= 20):
        sr = (runs * 100.0 / balls) if balls > 0 else 0.0
        sr_pts = sr_points(sr)
        pts += sr_pts
        breakdown.append(f"SR({sr:.1f}): {sr_pts:+d}")

    return pts, breakdown


def calc_bowler(bowl):
    wickets     = bowl.get("wickets", 0) or 0
    balls       = bowl.get("ballbowl", 0) or 0
    runs        = bowl.get("runs", 0) or 0
    maidens     = bowl.get("maidens", 0) or 0

    pts = 0.0
    breakdown = []

    wkt_pts = wickets * BOWLING_WICKET
    pts += wkt_pts
    if wickets > 0:
        breakdown.append(f"wickets: {wickets}×{BOWLING_WICKET} = {wkt_pts}")

    if wickets >= 5:
        pts += BOWLING_5W_BONUS
        breakdown.append(f"5W-haul: +{BOWLING_5W_BONUS}")
    elif wickets >= 4:
        pts += BOWLING_4W_BONUS
        breakdown.append(f"4W-haul: +{BOWLING_4W_BONUS}")
    elif wickets >= 3:
        pts += BOWLING_3W_BONUS
        breakdown.append(f"3W-haul: +{BOWLING_3W_BONUS}")

    if balls >= 12:
        eco = (runs * 6.0) / balls
        eco_pts = economy_points(eco)
        pts += eco_pts
        breakdown.append(f"economy({eco:.2f}): {eco_pts:+d}")

    maiden_pts = maidens * BOWLING_MAIDEN
    pts += maiden_pts
    if maidens > 0:
        breakdown.append(f"maidens: {maidens}×{BOWLING_MAIDEN} = {maiden_pts}")

    return pts, breakdown


def calc_fielder(field):
    catches     = field.get("catches", 0) or 0
    stumpings   = field.get("stumpings", 0) or 0
    ro_direct   = field.get("runouts_direct", 0) or 0
    runouts     = field.get("runouts", 0) or 0
    lbw_bowled  = field.get("lbwbowled", 0) or 0

    pts = 0.0
    breakdown = []

    c_pts = catches * FIELDING_CATCH
    pts += c_pts
    if catches > 0:
        breakdown.append(f"catches: {catches}×{FIELDING_CATCH} = {c_pts}")

    if catches >= 3:
        pts += FIELDING_3_CATCH_BONUS
        breakdown.append(f"3-catch-bonus: +{FIELDING_3_CATCH_BONUS}")

    lbw_pts = lbw_bowled * BOWLING_LBW_BOWLED
    pts += lbw_pts
    if lbw_bowled > 0:
        breakdown.append(f"lbw/bowled: {lbw_bowled}×{BOWLING_LBW_BOWLED} = {lbw_pts}")

    rod_pts = ro_direct * FIELDING_RUNOUT_DIRECT
    pts += rod_pts
    if ro_direct > 0:
        breakdown.append(f"runout-direct: {ro_direct}×{FIELDING_RUNOUT_DIRECT} = {rod_pts}")

    ro_pts = runouts * FIELDING_RUNOUT
    pts += ro_pts
    if runouts > 0:
        breakdown.append(f"runout: {runouts}×{FIELDING_RUNOUT} = {ro_pts}")

    st_pts = stumpings * FIELDING_STUMPING
    pts += st_pts
    if stumpings > 0:
        breakdown.append(f"stumpings: {stumpings}×{FIELDING_STUMPING} = {st_pts}")

    return pts, breakdown


def fetch_scorecard(match_id):
    url = f"{CRICKETAPI_BASE}/scorecard/full/{match_id}"
    headers = {"Authorization": f"Bearer {CRICKETAPI_TOKEN}"}
    resp = requests.get(url, headers=headers, timeout=30)
    resp.raise_for_status()
    return resp.json()


def fetch_player_roles(scorecard):
    """Build a playerId → role map by querying cricketapi.players via mysql CLI."""
    import subprocess

    all_ids = set()
    for inn_key in ("inningsA", "inningsB"):
        inn = scorecard.get(inn_key)
        if not inn:
            continue
        for b in inn.get("batter", []):
            if b.get("PlayerId"): all_ids.add(b["PlayerId"])
        for b in inn.get("bowler", []):
            if b.get("PlayerId"): all_ids.add(b["PlayerId"])
        for f in inn.get("fielder", []):
            if f.get("PlayerId"): all_ids.add(f["PlayerId"])

    squad = scorecard.get("matchinfo", {}).get("playingSquad", {})
    for lst in (squad.get("playerListA", []), squad.get("playerListB", [])):
        for p in lst:
            pid = p.get("Id") or p.get("id")
            if pid: all_ids.add(pid)

    if not all_ids:
        return {}

    id_csv = ",".join(str(i) for i in all_ids)

    # role column is now an ordinal: 0=BATTER, 1=BOWLER, 2=KEEPER, 3=ALLROUNDER
    query_players = (
        f"SELECT id, COALESCE(name, CAST(id AS CHAR)), COALESCE(role, -1) "
        f"FROM cricketapi.players WHERE id IN ({id_csv})"
    )
    query_config = (
        f"SELECT player_id, type FROM fantasyleague.fantasy_player_config WHERE player_id IN ({id_csv})"
    )

    role_map = {}
    name_map = {}
    try:
        result = subprocess.run(
            ["mysql", "-u", "root", "-ppassword", "--batch", "--skip-column-names", "-e", query_players],
            capture_output=True, text=True, timeout=10)
        for line in result.stdout.strip().split("\n"):
            if not line.strip():
                continue
            parts = line.split("\t")
            if len(parts) >= 3:
                pid = int(parts[0])
                name = parts[1] if parts[1] not in ("NULL", "") else None
                role_ordinal = parts[2].strip() if parts[2] not in ("NULL", "-1", "") else ""
                if name:
                    name_map[pid] = name
                role_map[pid] = ORDINAL_TO_ROLE.get(int(role_ordinal), "") if role_ordinal.isdigit() else role_ordinal
    except Exception as e:
        print(f"  [WARN] MySQL players query failed: {e}")

    missing_role_ids = [pid for pid in all_ids if not role_map.get(pid)]
    if missing_role_ids:
        try:
            result = subprocess.run(
                ["mysql", "-u", "root", "-ppassword", "--batch", "--skip-column-names", "-e", query_config],
                capture_output=True, text=True, timeout=10)
            for line in result.stdout.strip().split("\n"):
                if not line.strip():
                    continue
                parts = line.split("\t")
                if len(parts) >= 2:
                    pid = int(parts[0])
                    type_val = parts[1].strip()
                    if type_val not in ("NULL", "") and pid not in role_map:
                        role_map[pid] = ORDINAL_TO_ROLE.get(int(type_val), "")
        except Exception as e:
            print(f"  [WARN] MySQL config query failed: {e}")

    # Store name_map as attribute for later use
    fetch_player_roles._name_map = name_map
    return role_map


def fetch_actual_points(match_id):
    url = f"{FANTASY_BASE}/test/playerpoints/{match_id}"
    try:
        resp = requests.post(url, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        raw = data.get("playerPoints", {})
        return {int(k): v for k, v in raw.items()}
    except requests.ConnectionError:
        print("       [WARN] Fantasy service not reachable — will show expected only")
        return None
    except Exception as e:
        print(f"       [WARN] Failed to fetch actual points: {e}")
        return None


def calculate_expected(scorecard, role_map):
    """Return dict { playerId: (total_pts, [breakdown_strings]) }."""
    all_player_ids = set()
    squad = scorecard.get("matchinfo", {}).get("playingSquad", {})
    for lst in (squad.get("playerListA", []), squad.get("playerListB", [])):
        for p in lst:
            pid = p.get("Id") or p.get("id")
            if pid: all_player_ids.add(pid)

    for inn_key in ("inningsA", "inningsB"):
        inn = scorecard.get(inn_key)
        if not inn:
            continue
        for b in inn.get("batter", []):
            if b.get("PlayerId"): all_player_ids.add(b["PlayerId"])
        for b in inn.get("bowler", []):
            if b.get("PlayerId"): all_player_ids.add(b["PlayerId"])
        for f in inn.get("fielder", []):
            if f.get("PlayerId"): all_player_ids.add(f["PlayerId"])

    results = {}
    for pid in all_player_ids:
        results[pid] = {"total": IN_PLAYING11, "breakdown": [f"playing11: +{IN_PLAYING11}"]}

    for inn_key in ("inningsA", "inningsB"):
        inn = scorecard.get(inn_key)
        if not inn:
            continue

        for bat in inn.get("batter", []):
            pid = bat.get("PlayerId")
            if not pid or pid not in results:
                continue
            bowler = is_bowler(role_map.get(pid, ""))
            pts, bd = calc_batter(bat, bowler)
            results[pid]["total"] += pts
            results[pid]["breakdown"].extend([f"[BAT-{inn_key}] {x}" for x in bd])

        for bowl in inn.get("bowler", []):
            pid = bowl.get("PlayerId")
            if not pid or pid not in results:
                continue
            pts, bd = calc_bowler(bowl)
            results[pid]["total"] += pts
            results[pid]["breakdown"].extend([f"[BOWL-{inn_key}] {x}" for x in bd])

        for field in inn.get("fielder", []):
            pid = field.get("PlayerId")
            if not pid or pid not in results:
                continue
            pts, bd = calc_fielder(field)
            results[pid]["total"] += pts
            results[pid]["breakdown"].extend([f"[FIELD-{inn_key}] {x}" for x in bd])

    return {pid: (info["total"], info["breakdown"]) for pid, info in results.items()}


def get_player_name(scorecard, pid):
    db_names = getattr(fetch_player_roles, "_name_map", {})
    if pid in db_names:
        return db_names[pid]

    squad = scorecard.get("matchinfo", {}).get("playingSquad", {})
    for lst in (squad.get("playerListA", []), squad.get("playerListB", [])):
        for p in lst:
            p_id = p.get("Id") or p.get("id")
            if p_id == pid:
                name = p.get("fullName") or p.get("name") or p.get("Name")
                if name:
                    return name

    for inn_key in ("inningsA", "inningsB"):
        inn = scorecard.get(inn_key)
        if not inn:
            continue
        for section in ("batter", "bowler", "fielder"):
            for entry in inn.get(section, []):
                if entry.get("PlayerId") == pid:
                    name = entry.get("PlayerName") or entry.get("playerName") or entry.get("name")
                    if name:
                        return name

    return str(pid)


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/verify-points.py <matchId>")
        sys.exit(1)

    match_id = int(sys.argv[1])
    print(f"{'='*80}")
    print(f"  Fantasy Points Verifier — Match {match_id}")
    print(f"{'='*80}")

    print("\n[1/4] Fetching scorecard from cricketapi...")
    scorecard = fetch_scorecard(match_id)
    team_a = scorecard.get("matchinfo", {}).get("teamA", "?")
    team_b = scorecard.get("matchinfo", {}).get("teamB", "?")
    print(f"       {team_a} vs {team_b}")

    print("[2/4] Fetching player roles...")
    role_map = fetch_player_roles(scorecard)
    print(f"       Resolved roles for {len(role_map)} players")

    print("[3/4] Fetching actual points from fantasy service...")
    actual = fetch_actual_points(match_id)
    if actual is not None:
        print(f"       Got points for {len(actual)} players")
    compare_mode = actual is not None

    print("[4/4] Calculating expected points from scorecard...\n")
    expected = calculate_expected(scorecard, role_map)

    all_pids = sorted(expected.keys())

    passed = 0
    failed = 0
    skipped = 0
    failed_players = []

    if compare_mode:
        print(f"{'ID':>8}  {'Player':<25} {'Role':<18} {'Expected':>10} {'Actual':>10} {'Status':>8}")
        print(f"{'-'*8}  {'-'*25} {'-'*18} {'-'*10} {'-'*10} {'-'*8}")
    else:
        print(f"{'ID':>8}  {'Player':<25} {'Role':<18} {'Expected':>10}")
        print(f"{'-'*8}  {'-'*25} {'-'*18} {'-'*10}")

    for pid in all_pids:
        exp_pts, breakdown = expected.get(pid, (0.0, []))
        name = get_player_name(scorecard, pid)
        role = role_map.get(pid, "?")

        if compare_mode:
            act_pts = actual.get(pid, None)
            act_str = f"{act_pts:.1f}" if act_pts is not None else "MISSING"
            match_ok = act_pts is not None and abs(exp_pts - act_pts) < 0.01

            if act_pts is None:
                marker = "  —"
                skipped += 1
            elif match_ok:
                marker = "  ✓"
                passed += 1
            else:
                marker = "  ✗"
                failed += 1
                failed_players.append((pid, name, role, exp_pts, act_pts, breakdown))

            print(f"{pid:>8}  {name:<25} {role:<18} {exp_pts:>10.1f} {act_str:>10} {marker:>8}")
        else:
            print(f"{pid:>8}  {name:<25} {role:<18} {exp_pts:>10.1f}")

    print(f"\n{'='*80}")
    if compare_mode:
        total = passed + failed + skipped
        print(f"  RESULTS: {passed} passed, {failed} failed, {skipped} skipped out of {total} players")
    else:
        print(f"  EXPECTED POINTS for {len(all_pids)} players (service offline — no comparison)")
    print(f"{'='*80}")

    if failed_players:
        print(f"\n{'─'*80}")
        print("  DETAILED BREAKDOWN FOR FAILED PLAYERS")
        print(f"{'─'*80}")
        for pid, name, role, exp_pts, act_pts, breakdown in failed_players:
            act_str = f"{act_pts:.1f}" if act_pts is not None else "MISSING"
            print(f"\n  ► {name} (id={pid}, role={role})")
            print(f"    Expected: {exp_pts:.1f}  |  Actual: {act_str}  |  Diff: {exp_pts - (act_pts or 0):.1f}")
            for line in breakdown:
                print(f"      {line}")

    if not compare_mode:
        print(f"\n  [INFO] Start fantasy service and re-run to compare actual vs expected.")

    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
