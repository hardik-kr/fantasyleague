package com.cricket.fantasyleague.service.season.league;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.season.UserOverallStats;
import com.cricket.fantasyleague.entity.table.season.league.PrivateLeague;
import com.cricket.fantasyleague.entity.table.season.league.PrivateLeagueMember;
import com.cricket.fantasyleague.exception.ResourceAlreadyExist;
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.exception.league.LeagueFullException;
import com.cricket.fantasyleague.payload.season.league.CreateLeagueRequest;
import com.cricket.fantasyleague.payload.season.league.LeagueDetailResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueLeaderboardEntry;
import com.cricket.fantasyleague.payload.season.league.LeagueLeaderboardPageResponse;
import com.cricket.fantasyleague.payload.season.league.LeaguePreviewResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueSummary;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.repository.season.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.season.league.PrivateLeagueMemberRepository;
import com.cricket.fantasyleague.repository.season.league.PrivateLeagueRepository;

/**
 * Default implementation of {@link LeagueService}.
 *
 * <p>Concurrency contract:
 * <ul>
 *   <li>{@code createLeague} retries up to 5 times on code collision.</li>
 *   <li>{@code joinLeague} uses an atomic
 *       {@code UPDATE private_league SET member_count = member_count + 1
 *       WHERE id = ? AND member_count < max_members}; the resulting row
 *       count is the only source of truth for the cap. The membership
 *       insert and the increment are both inside the same transaction.</li>
 *   <li>{@code leaveLeague} deletes the membership row, decrements the
 *       counter, then deletes the league if the counter hit zero —
 *       all in the same transaction.</li>
 * </ul>
 */
@Service
public class LeagueServiceImpl implements LeagueService {

    private static final Logger log = LoggerFactory.getLogger(LeagueServiceImpl.class);

    private static final int MAX_CODE_RETRIES = 5;
    private static final int MIN_MEMBERS = 2;
    private static final int MAX_MEMBERS = 1000;
    private static final int MIN_NAME_LEN = 3;
    private static final int MAX_NAME_LEN = 80;

    private final PrivateLeagueRepository leagueRepository;
    private final PrivateLeagueMemberRepository memberRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;
    private final UserRepository userRepository;
    private final LeagueCodeGenerator codeGenerator;

    public LeagueServiceImpl(PrivateLeagueRepository leagueRepository,
                             PrivateLeagueMemberRepository memberRepository,
                             UserOverallStatsRepository userOverallStatsRepository,
                             UserRepository userRepository,
                             LeagueCodeGenerator codeGenerator) {
        this.leagueRepository = leagueRepository;
        this.memberRepository = memberRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.userRepository = userRepository;
        this.codeGenerator = codeGenerator;
    }

    @Override
    @Transactional
    public LeagueResponse createLeague(User creator, CreateLeagueRequest request) {
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(request, "request");

        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.length() < MIN_NAME_LEN || name.length() > MAX_NAME_LEN) {
            throw new IllegalArgumentException(
                    "name must be " + MIN_NAME_LEN + ".." + MAX_NAME_LEN + " characters");
        }
        Integer maxMembers = request.maxMembers();
        if (maxMembers == null || maxMembers < MIN_MEMBERS || maxMembers > MAX_MEMBERS) {
            throw new IllegalArgumentException(
                    "maxMembers must be between " + MIN_MEMBERS + " and " + MAX_MEMBERS);
        }

        String code = generateUniqueCode();
        PrivateLeague league = new PrivateLeague(name, code, maxMembers, creator);
        leagueRepository.save(league);

        memberRepository.save(new PrivateLeagueMember(league, creator));

        log.info("Private league created: code={} name='{}' creatorId={} maxMembers={}",
                code, name, creator.getId(), maxMembers);
        return toLeagueResponse(league, creator);
    }

    @Override
    @Transactional
    public LeagueResponse joinLeague(User user, String code) {
        Objects.requireNonNull(user, "user");
        PrivateLeague league = requireLeagueByCode(code);

        if (memberRepository.existsByPrivateLeagueAndUser(league, user)) {
            throw new ResourceAlreadyExist(
                    "You are already a member of league " + league.getCode(), "code", league.getCode());
        }

        int updated = leagueRepository.incrementMemberCountIfNotFull(league.getId());
        if (updated == 0) {
            log.warn("Join rejected: league full code={} userId={}", league.getCode(), user.getId());
            throw new LeagueFullException("League is full");
        }

        memberRepository.save(new PrivateLeagueMember(league, user));
        // Refresh in-memory counter so the response reflects the change
        league.setMemberCount(league.getMemberCount() + 1);

        log.info("League joined: code={} userId={} memberCount={}",
                league.getCode(), user.getId(), league.getMemberCount());
        return toLeagueResponse(league, user);
    }

    @Override
    @Transactional
    public void leaveLeague(User user, String code) {
        Objects.requireNonNull(user, "user");
        PrivateLeague league = requireLeagueByCode(code);

        int removed = memberRepository.deleteByPrivateLeagueAndUser(league, user);
        if (removed == 0) {
            // Treat "not a member" as 404 to avoid leaking league existence.
            throw new ResourceNotFoundException("League", "code", code);
        }

        leagueRepository.decrementMemberCount(league.getId());
        int newCount = Math.max(0, league.getMemberCount() - 1);

        if (newCount == 0) {
            log.info("League auto-deleted (last member left): code={}", league.getCode());
            leagueRepository.delete(league);
        } else {
            log.info("League left: code={} userId={} memberCount={}",
                    league.getCode(), user.getId(), newCount);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeagueSummary> getMyLeagues(User user) {
        Objects.requireNonNull(user, "user");
        List<PrivateLeagueMember> memberships =
                memberRepository.findByUserOrderByJoinedAtDesc(user);

        // Resolve current user's points once — same value used to compute rank
        // inside every league the user belongs to.
        UserOverallStats myStats = userOverallStatsRepository.findByUserid(user);
        Double myPoints = myStats != null && myStats.getTotalpoints() != null
                ? myStats.getTotalpoints() : 0.0;

        List<LeagueSummary> out = new ArrayList<>(memberships.size());
        for (PrivateLeagueMember m : memberships) {
            PrivateLeague l = m.getPrivateLeague();
            boolean isCreator = l.getCreatedBy() != null
                    && Objects.equals(l.getCreatedBy().getId(), user.getId());

            Integer myRank = null;
            List<Long> memberIds = memberRepository.findUserIdsByPrivateLeague(l);
            if (!memberIds.isEmpty()) {
                long above = userOverallStatsRepository
                        .countUsersAboveByUserIds(memberIds, myPoints);
                myRank = (int) above + 1;
            }

            out.add(new LeagueSummary(
                    l.getCode(),
                    l.getName(),
                    l.getMaxMembers(),
                    l.getMemberCount(),
                    isCreator,
                    m.getJoinedAt(),
                    myRank,
                    myPoints
            ));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public LeagueDetailResponse getDetail(User user, String code) {
        PrivateLeague league = requireLeagueByCode(code);
        if (!memberRepository.existsByPrivateLeagueAndUser(league, user)) {
            // 404 (not 403) to avoid leaking existence of leagues the user can't see.
            throw new ResourceNotFoundException("League", "code", code);
        }

        List<PrivateLeagueMember> allMembers =
                memberRepository.findByPrivateLeagueOrderByJoinedAtAsc(league);
        List<Long> userIds = new ArrayList<>(allMembers.size());
        for (PrivateLeagueMember m : allMembers) {
            userIds.add(m.getUser().getId());
        }
        Map<Long, User> usersById = loadUsersById(userIds);

        Long creatorId = league.getCreatedBy() != null ? league.getCreatedBy().getId() : null;
        boolean isCreator = creatorId != null && Objects.equals(creatorId, user.getId());

        List<LeagueDetailResponse.Member> members = new ArrayList<>(allMembers.size());
        for (PrivateLeagueMember m : allMembers) {
            User u = usersById.get(m.getUser().getId());
            String uname = u != null ? u.getUsername() : null;
            String fname = u != null ? u.getFirstname() : null;
            members.add(new LeagueDetailResponse.Member(
                    m.getUser().getId(),
                    uname,
                    fname,
                    Objects.equals(m.getUser().getId(), creatorId),
                    m.getJoinedAt()
            ));
        }

        return new LeagueDetailResponse(
                league.getCode(),
                league.getName(),
                league.getMaxMembers(),
                league.getMemberCount(),
                creatorId,
                isCreator,
                league.getCreatedAt(),
                members
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LeaguePreviewResponse getPreview(User user, String code) {
        Objects.requireNonNull(user, "user");
        PrivateLeague league = requireLeagueByCode(code);
        boolean isMember = memberRepository.existsByPrivateLeagueAndUser(league, user);
        boolean full = league.getMemberCount() != null
                && league.getMaxMembers() != null
                && league.getMemberCount() >= league.getMaxMembers();
        return new LeaguePreviewResponse(
                league.getCode(),
                league.getName(),
                league.getMemberCount(),
                league.getMaxMembers(),
                isMember,
                full
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LeagueLeaderboardPageResponse getLeaderboard(User user, String code, int page, int size) {
        PrivateLeague league = requireLeagueByCode(code);
        if (!memberRepository.existsByPrivateLeagueAndUser(league, user)) {
            throw new ResourceNotFoundException("League", "code", code);
        }

        int clampedSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        List<Long> userIds = memberRepository.findUserIdsByPrivateLeague(league);
        if (userIds.isEmpty()) {
            return new LeagueLeaderboardPageResponse(
                    league.getCode(), List.of(), safePage, clampedSize, 0L, 0, null);
        }

        Page<UserOverallStats> statsPage = userOverallStatsRepository
                .findRankedByUserIds(userIds, PageRequest.of(safePage, clampedSize));

        Long creatorId = league.getCreatedBy() != null ? league.getCreatedBy().getId() : null;
        int startRank = safePage * clampedSize + 1;
        List<LeagueLeaderboardEntry> entries = new ArrayList<>(statsPage.getNumberOfElements());
        for (int i = 0; i < statsPage.getContent().size(); i++) {
            UserOverallStats uos = statsPage.getContent().get(i);
            User u = uos.getUserid();
            entries.add(new LeagueLeaderboardEntry(
                    startRank + i,
                    u != null ? u.getId() : null,
                    u != null ? u.getUsername() : null,
                    u != null ? u.getFirstname() : null,
                    uos.getTotalpoints(),
                    u != null && Objects.equals(u.getId(), creatorId)
            ));
        }

        LeagueLeaderboardEntry currentUserEntry = null;
        UserOverallStats myStats = userOverallStatsRepository.findByUserid(user);
        if (myStats != null) {
            double pts = myStats.getTotalpoints() != null ? myStats.getTotalpoints() : 0.0;
            long above = userOverallStatsRepository.countUsersAboveByUserIds(userIds, pts);
            currentUserEntry = new LeagueLeaderboardEntry(
                    (int) above + 1,
                    user.getId(),
                    user.getUsername(),
                    user.getFirstname(),
                    myStats.getTotalpoints(),
                    Objects.equals(user.getId(), creatorId)
            );
        }

        return new LeagueLeaderboardPageResponse(
                league.getCode(),
                entries,
                safePage,
                clampedSize,
                statsPage.getTotalElements(),
                statsPage.getTotalPages(),
                currentUserEntry
        );
    }

    private String generateUniqueCode() {
        for (int attempt = 1; attempt <= MAX_CODE_RETRIES; attempt++) {
            String code = codeGenerator.generate();
            if (!leagueRepository.existsByCode(code)) {
                return code;
            }
            log.debug("League code collision (attempt {}/{}): {}", attempt, MAX_CODE_RETRIES, code);
        }
        throw new IllegalStateException(
                "Could not generate a unique league code after " + MAX_CODE_RETRIES + " attempts");
    }

    private PrivateLeague requireLeagueByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResourceNotFoundException("League", "code", String.valueOf(code));
        }
        Optional<PrivateLeague> league = leagueRepository.findByCode(code.trim().toUpperCase());
        return league.orElseThrow(() -> new ResourceNotFoundException("League", "code", code));
    }

    private Map<Long, User> loadUsersById(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        Map<Long, User> map = new HashMap<>(userIds.size() * 2);
        for (User u : userRepository.findAllById(userIds)) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private LeagueResponse toLeagueResponse(PrivateLeague league, User viewer) {
        Long creatorId = league.getCreatedBy() != null ? league.getCreatedBy().getId() : null;
        boolean isCreator = creatorId != null && viewer != null
                && Objects.equals(creatorId, viewer.getId());
        return new LeagueResponse(
                league.getCode(),
                league.getName(),
                league.getMaxMembers(),
                league.getMemberCount(),
                creatorId,
                isCreator,
                league.getCreatedAt()
        );
    }
}
