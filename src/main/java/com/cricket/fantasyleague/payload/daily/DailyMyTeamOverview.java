package com.cricket.fantasyleague.payload.daily;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Combined "my team" overview for the {@code /daily/my-team} screen — returns
 * everything that screen can show in a single round-trip so the FE doesn't
 * have to chain {@code /me/draft} + {@code POST /team} + a "is there a live
 * match?" probe to populate the page.
 *
 * <p>Daily Challenge can have <b>two</b> teams interesting to the user at the
 * same time: the locked team for a currently in-flight match (live points,
 * polled) and the draft for the next upcoming match (editable preview). The
 * dashboard already surfaces these as separate concepts; this DTO exposes
 * both to the my-team page so it can offer a dropdown to switch between
 * them.
 *
 * <pre>
 *  ┌─────────────────────────┬──────────────────────────────────────────┐
 *  │ live    (nullable)      │ caller's locked DailyTeam for the match  │
 *  │                         │ in {@code IN_PROGRESS}/{@code DELAY}, if │
 *  │                         │ they have one. Null otherwise.           │
 *  ├─────────────────────────┼──────────────────────────────────────────┤
 *  │ upcoming (nullable)     │ caller's draft for the next-upcoming     │
 *  │                         │ match (same shape as {@code /me/draft}). │
 *  │                         │ Null if no upcoming match exists.        │
 *  └─────────────────────────┴──────────────────────────────────────────┘
 * </pre>
 *
 * <p>{@link com.fasterxml.jackson.annotation.JsonInclude} is set to
 * {@code NON_NULL} so the FE can do a simple {@code overview.live != null}
 * presence check without seeing an explicit {@code null} key.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyMyTeamOverview(
        DailyTeamResponse live,
        DailyDraftResponse upcoming
) {
}
