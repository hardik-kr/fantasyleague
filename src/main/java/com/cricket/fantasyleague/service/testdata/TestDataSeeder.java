package com.cricket.fantasyleague.service.testdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStatsDraft;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.repository.UserMatchStatsDraftRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.match.MatchService;
import com.cricket.fantasyleague.util.AppConstants;

/**
 * Standalone test-data seeder — creates users, overall stats, and draft teams.
 * Does NOT touch any production service; writes directly via repositories.
 */
@Service
public class TestDataSeeder {
    private static final Logger logger = LoggerFactory.getLogger(TestDataSeeder.class);
    private static final String DEFAULT_PASSWORD = "test1234";

    private final UserRepository userRepository;
    private final UserOverallStatsRepository overallStatsRepository;
    private final UserMatchStatsDraftRespository draftRepository;
    private final MatchService matchService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public TestDataSeeder(
            UserRepository userRepository,
            UserOverallStatsRepository overallStatsRepository,
            UserMatchStatsDraftRespository draftRepository,
            MatchService matchService) {
        this.userRepository = userRepository;
        this.overallStatsRepository = overallStatsRepository;
        this.draftRepository = draftRepository;
        this.matchService = matchService;
    }

    // ── Player IDs from testData.txt (MI + PBKS squads for match 118919) ──

    private static final int[] MI_PLAYER_IDS = {
        9311,   // Jasprit Bumrah
        576,    // Rohit Sharma
        9647,   // Hardik Pandya
        7915,   // Suryakumar Yadav
        14504,  // Tilak Varma
        12258,  // Will Jacks
        8117,   // Trent Boult
        8520,   // Quinton de Kock
        10100,  // Mitchell Santner
        8683,   // Shardul Thakur
        7836,   // Deepak Chahar
        13070,  // Ryan Rickelton
        36139,  // Naman Dhir
        13748,  // Sherfane Rutherford
        9576,   // Corbin Bosch
    };

    private static final int[] PBKS_PLAYER_IDS = {
        9428,   // Shreyas Iyer
        13217,  // Arshdeep Singh
        14565,  // Marco Jansen
        8989,   // Marcus Stoinis
        14689,  // Priyansh Arya
        7910,   // Yuzvendra Chahal
        13915,  // Nehal Wadhera
        10919,  // Shashank Singh
        14254,  // Prabhsimran Singh
        14452,  // Harpreet Brar
        10692,  // Lockie Ferguson
        13214,  // Azmatullah Omarzai
        10484,  // Praveen Dubey
        11893,  // Vishnu Vinod
        10486,  // Vijaykumar Vyshak
    };

    private static final int[] ALL_PLAYER_IDS;
    static {
        ALL_PLAYER_IDS = new int[MI_PLAYER_IDS.length + PBKS_PLAYER_IDS.length];
        System.arraycopy(MI_PLAYER_IDS, 0, ALL_PLAYER_IDS, 0, MI_PLAYER_IDS.length);
        System.arraycopy(PBKS_PLAYER_IDS, 0, ALL_PLAYER_IDS, MI_PLAYER_IDS.length, PBKS_PLAYER_IDS.length);
    }

    // ── 10 test user definitions ──

    private static final String[][] TEST_USERS = {
        // username,       firstname,  lastname,   email,                   phone,       favteam
        {"testuser1",     "Rahul",    "Sharma",   "rahul@test.com",        "9000000001", "MI"},
        {"testuser2",     "Priya",    "Patel",    "priya@test.com",        "9000000002", "CSK"},
        {"testuser3",     "Amit",     "Kumar",    "amit@test.com",         "9000000003", "RCB"},
        {"testuser4",     "Sneha",    "Gupta",    "sneha@test.com",        "9000000004", "KKR"},
        {"testuser5",     "Vikram",   "Singh",    "vikram@test.com",       "9000000005", "PBKS"},
        {"testuser6",     "Anjali",   "Reddy",    "anjali@test.com",       "9000000006", "RR"},
        {"testuser7",     "Rohit",    "Mehra",    "rohit.m@test.com",      "9000000007", "DC"},
        {"testuser8",     "Neha",     "Joshi",    "neha@test.com",         "9000000008", "SRH"},
        {"testuser9",     "Karan",    "Verma",    "karan@test.com",        "9000000009", "GT"},
        {"testuser10",    "Divya",    "Nair",     "divya@test.com",        "9000000010", "LSG"},
    };

    // ── Public API ──

    @Transactional
    public Map<String, Object> seedAll(Integer matchId) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        Match match = null;
        if (matchId != null) {
            match = matchService.findMatchById(matchId);
        }

        List<User> users = seedUsers();
        result.put("usersCreated", users.size());

        List<UserOverallStats> overallStats = seedOverallStats(users);
        result.put("overallStatsCreated", overallStats.size());

        if (match != null) {
            int drafts = seedDraftTeams(users, match);
            result.put("draftTeamsCreated", drafts);
            result.put("matchId", matchId);
        } else {
            result.put("draftTeamsCreated", 0);
            result.put("note", matchId == null
                    ? "No matchId provided — skipped draft team creation"
                    : "Match not found: " + matchId);
        }

        result.put("defaultPassword", DEFAULT_PASSWORD);
        result.put("elapsedMs", System.currentTimeMillis() - start);
        logger.info("Test data seeded: {}", result);
        return result;
    }

    @Transactional
    public Map<String, Object> seedDraftOnly(Integer matchId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            result.put("error", "Match not found: " + matchId);
            return result;
        }

        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.USER && u.getIsActive())
                .toList();
        if (users.isEmpty()) {
            result.put("error", "No active USER-role users found. Run /test/seed/{matchId} first.");
            return result;
        }

        int drafts = seedDraftTeams(users, match);
        result.put("draftTeamsCreated", drafts);
        result.put("matchId", matchId);
        return result;
    }

    // ── Seeding logic ──

    private List<User> seedUsers() {
        String encodedPassword = encoder.encode(DEFAULT_PASSWORD);
        List<User> created = new ArrayList<>();

        for (String[] def : TEST_USERS) {
            if (userRepository.findByEmail(def[3]) != null) {
                logger.debug("User already exists: {}", def[3]);
                User existing = userRepository.findByEmail(def[3]);
                created.add(existing);
                continue;
            }
            User user = new User(def[0], def[1], def[2], def[3], encodedPassword, def[4], def[5], UserRole.USER);
            created.add(userRepository.save(user));
            logger.info("Created user: {} ({})", def[0], def[3]);
        }
        return created;
    }

    private List<UserOverallStats> seedOverallStats(List<User> users) {
        List<UserOverallStats> created = new ArrayList<>();
        for (User user : users) {
            if (overallStatsRepository.findByUserid(user) != null) {
                created.add(overallStatsRepository.findByUserid(user));
                continue;
            }
            UserOverallStats stats = new UserOverallStats(
                    user, 0.0, 0.0,
                    AppConstants.FantasyPoints.TOTAL_BOOSTER,
                    AppConstants.FantasyPoints.TOTAL_TRANSFER);
            created.add(overallStatsRepository.save(stats));
        }
        return created;
    }

    /**
     * For each user, creates a draft team (UserMatchStatsDraft + playing11) for the given match.
     * Each user gets a different random combination of 11 players from the pool,
     * with random captain / vice-captain / booster picks.
     */
    private int seedDraftTeams(List<User> users, Match match) {
        Random rng = new Random(42);
        int count = 0;

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);

            if (draftRepository.findByMatchidAndUserid(match, user) != null) {
                logger.debug("Draft already exists for user={} match={}", user.getEmail(), match.getId());
                continue;
            }

            List<Player> playing11 = pickPlaying11(rng, i);
            Player captain = playing11.get(0);
            Player viceCaptain = playing11.get(1);
            int transfersUsed = 0;//rng.nextInt(3);

            UserMatchStatsDraft draft = new UserMatchStatsDraft(
                    user, match, null, transfersUsed, 0.0,
                    captain, viceCaptain, null, playing11);
            draftRepository.save(draft);
            count++;
            logger.info("Draft team created: user={}, match={}, captain={}, vc={}",
                    user.getEmail(), match.getId(), captain.getId(), viceCaptain.getId());
        }
        return count;
    }

    /**
     * Picks 11 players ensuring mix from both teams.
     * Uses user index to vary the selection across users.
     */
    private List<Player> pickPlaying11(Random rng, int userIndex) {
        List<Integer> miPool = new ArrayList<>();
        for (int id : MI_PLAYER_IDS) miPool.add(id);

        List<Integer> pbksPool = new ArrayList<>();
        for (int id : PBKS_PLAYER_IDS) pbksPool.add(id);

        Collections.shuffle(miPool, new Random(rng.nextLong() + userIndex));
        Collections.shuffle(pbksPool, new Random(rng.nextLong() + userIndex));

        int miCount = 5 + (userIndex % 3);      // 5, 6, or 7 from MI
        int pbksCount = 11 - miCount;            // rest from PBKS

        List<Integer> selectedIds = new ArrayList<>();
        for (int j = 0; j < miCount && j < miPool.size(); j++) {
            selectedIds.add(miPool.get(j));
        }
        for (int j = 0; j < pbksCount && j < pbksPool.size(); j++) {
            selectedIds.add(pbksPool.get(j));
        }

        while (selectedIds.size() < 11) {
            for (int id : ALL_PLAYER_IDS) {
                if (!selectedIds.contains(id)) {
                    selectedIds.add(id);
                    if (selectedIds.size() >= 11) break;
                }
            }
        }

        List<Player> players = new ArrayList<>();
        for (Integer pid : selectedIds) {
            Player p = new Player();
            p.setId(pid);
            players.add(p);
        }
        return players;
    }

}
