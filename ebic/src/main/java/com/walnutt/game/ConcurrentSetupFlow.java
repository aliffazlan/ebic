package com.walnutt.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import java.util.function.Predicate;

import com.walnutt.data.UnitDefinition;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ConcurrentSetupHandler;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;

/**
 * Lets both players draft and place their army independently, at their own pace -
 * no waiting for the other player each round the way the hotseat-terminal
 * DraftFlow/PlacementFlow require (those are untouched, still used by
 * newFullDraftMatch() for terminal/tests). The whole draft pool is pre-allocated
 * up front (deterministic, single-threaded, before anything concurrent starts), then
 * each player's draft-through-placement sequence runs on its own dedicated thread,
 * touching only that player's own Player/Unit objects until the very end, when both
 * threads join back into one before the match loop begins - the same "only one
 * thread touches GameState" invariant the rest of the engine relies on is restored
 * the moment this method returns.
 *
 * The only genuinely shared mutable object either thread touches is GameMap (both
 * players' final placements land in the same map, even though their zones are
 * disjoint) - the actual moveUnit calls are synchronized on the map instance as a
 * defensive measure against concurrent structural mutation, even though the two
 * players' target tiles never overlap in practice.
 */
public final class ConcurrentSetupFlow {
    private static final int ELITE_ROUNDS = 3;
    private static final int BASIC_COUNT = 10;
    private static final List<String> ROUND_LABELS = List.of("Champion", "Elite 1/3", "Elite 2/3", "Elite 3/3");
    /**
     * How many times a pair of two refused heroes is redealt before falling back to a
     * deterministic swap. Bounded because a self-play match has TWO refusing seats and the
     * pool can hold three refused heroes at once, so a pool that is mostly refused near the
     * end of the draft would otherwise spin forever reshuffling the same two cards.
     */
    private static final int REROLL_ATTEMPTS = 8;

    private ConcurrentSetupFlow() {
    }

    public static void run(GameState state, ConcurrentSetupHandler handler) {
        run(state, handler, Map.of());
    }

    /**
     * `favourites` maps a team to a hero definition id that player has asked to always be
     * offered. Empty for terminal mode and for every existing test; only the web bridge
     * knows who is sitting in a seat.
     */
    public static void run(GameState state, ConcurrentSetupHandler handler, Map<Team, String> favourites) {
        Map<Team, List<List<UnitDefinition>>> allocation = allocate(state, favourites);

        List<Thread> threads = new ArrayList<>();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        for (Player player : state.getPlayers()) {
            List<List<UnitDefinition>> rounds = allocation.get(player.getTeam());
            List<List<UnitDefinition>> opponentRounds = allocation.get(otherTeam(state, player.getTeam()));
            Thread thread = new Thread(() -> {
                try {
                    runOnePlayerSetup(state, player, rounds, opponentRounds, handler);
                } catch (RuntimeException e) {
                    failures.add(e);
                }
            }, "setup-" + player.getTeam());
            threads.add(thread);
        }

        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for player setup to finish", e);
            }
        }

        if (!failures.isEmpty()) {
            Throwable first = failures.get(0);
            throw new IllegalStateException("Player setup failed: " + first.getMessage(), first);
        }
    }

    /**
     * Shuffles and splits the champion/elite pools into one pre-determined sequence
     * of rounds per team, all before any player thread starts - this is the "pool is
     * determined as the game begins" requirement, and it's what lets each player's
     * thread proceed afterward with zero further contention on the shared RNG/pool.
     */
    private static Map<Team, List<List<UnitDefinition>>> allocate(GameState state, Map<Team, String> favourites) {
        Map<String, UnitDefinition> unitDefs = state.getUnitDefinitions();
        DraftService draftService = new DraftService();
        Random random = state.getRandom();

        List<String> championIds = new ArrayList<>(draftService.getAvailableChampions(unitDefs));
        List<String> eliteIds = new ArrayList<>(draftService.getAvailableElites(unitDefs));
        Map<Team, String> claimed = resolveFavourites(favourites, championIds, eliteIds);

        // Both players naming the same hero means neither may have it, so it leaves the
        // pool entirely and cannot even turn up by chance. A claimed favourite leaves too,
        // because it is dealt to its own player by hand below - which is also what keeps
        // "no hero appears in both rosters" true here.
        List<String> withheld = new ArrayList<>(contestedFavourites(favourites));
        withheld.addAll(claimed.values());
        championIds.removeAll(withheld);
        eliteIds.removeAll(withheld);

        List<UnitDefinition> championPool = shuffled(championIds, unitDefs, random);
        List<UnitDefinition> elitePool = shuffled(eliteIds, unitDefs, random);

        Map<Team, List<List<UnitDefinition>>> allocation = new HashMap<>();
        for (Player player : state.getPlayers()) {
            String favourite = claimed.get(player.getTeam());
            UnitDefinition favouriteDef = favourite == null ? null : unitDefs.get(favourite);
            if (favourite != null && favouriteDef == null) {
                throw new IllegalStateException("Favourite '" + favourite + "' has no unit definition");
            }
            boolean favouriteIsChampion = favouriteDef != null
                && "champion".equalsIgnoreCase(favouriteDef.type());
            // Which elite round hosts an elite favourite is rolled rather than fixed, so
            // the round it appears in isn't itself a tell to an opponent who knows the rule.
            int favouriteEliteRound = favouriteDef != null && !favouriteIsChampion
                ? 1 + random.nextInt(ELITE_ROUNDS)
                : -1;

            // Asked once per seat, before a single pair is dealt: a human refuses nothing, so
            // this is identity for them and the draft is untouched.
            Predicate<UnitDefinition> refused = refusalFor(state, player);

            List<List<UnitDefinition>> rounds = new ArrayList<>();
            rounds.add(favouriteIsChampion
                ? pairWithFavourite(favouriteDef, championPool, random)
                : drawTwoFor(championPool, refused, random));
            for (int i = 1; i <= ELITE_ROUNDS; i++) {
                rounds.add(i == favouriteEliteRound
                    ? pairWithFavourite(favouriteDef, elitePool, random)
                    : drawTwoFor(elitePool, refused, random));
            }
            allocation.put(player.getTeam(), rounds);
        }
        return allocation;
    }

    /** Favourites worth honouring: known to the pool, and not wanted by both players at once. */
    private static Map<Team, String> resolveFavourites(Map<Team, String> favourites, List<String> championIds,
                                                        List<String> eliteIds) {
        Map<Team, String> resolved = new HashMap<>();
        if (favourites == null || favourites.isEmpty()) {
            return resolved;
        }
        List<String> contested = contestedFavourites(favourites);
        for (Map.Entry<Team, String> entry : favourites.entrySet()) {
            String id = entry.getValue();
            if (id == null || contested.contains(id)) {
                continue;
            }
            if (championIds.contains(id) || eliteIds.contains(id)) {
                resolved.put(entry.getKey(), id);
            }
        }
        return resolved;
    }

    /** Ids more than one team asked for - nobody gets these. */
    private static List<String> contestedFavourites(Map<Team, String> favourites) {
        List<String> contested = new ArrayList<>();
        if (favourites == null) {
            return contested;
        }
        List<String> seen = new ArrayList<>();
        for (String id : favourites.values()) {
            if (id == null) {
                continue;
            }
            if (seen.contains(id) && !contested.contains(id)) {
                contested.add(id);
            }
            seen.add(id);
        }
        return contested;
    }

    /**
     * What this seat refuses to be dealt. Read off the state's own InputHandler rather than
     * threaded in as a parameter, because it is exactly the seat that will be answering
     * choosePick a moment later - Game sets the handler before calling run, and a test
     * driving this flow directly may not have set one at all, hence the null.
     */
    private static Predicate<UnitDefinition> refusalFor(GameState state, Player player) {
        InputHandler handler = state.getInputHandler();
        return handler == null ? definition -> false : definition -> handler.refusesToDraft(player, definition);
    }

    /**
     * A pair this seat can actually play. Ordinary {@link #drawTwo} unless BOTH halves are
     * refused, in which case they go back and the pool is redealt - invisibly, since the
     * whole allocation happens before either player's thread starts and nothing has been
     * rendered yet.
     *
     * Rerolling rather than letting the seat pick one anyway is the point: declining a hero
     * only helps if something else is on offer, and with three refused heroes in the pool a
     * round of two of them was reachable well before Shawl joined the list.
     */
    private static List<UnitDefinition> drawTwoFor(List<UnitDefinition> pool,
                                                    Predicate<UnitDefinition> refused, Random random) {
        for (int attempt = 0; attempt < REROLL_ATTEMPTS; attempt++) {
            List<UnitDefinition> pair = drawTwo(pool);
            if (pair.stream().anyMatch(definition -> !refused.test(definition))) {
                return pair;
            }
            pool.addAll(pair);
            Collections.shuffle(pool, random);
        }
        // Every reroll came back refused. Rather than keep shuffling, take a pair and trade
        // one half for the first playable hero left in the pool - which terminates, and is
        // what the rerolling was trying to stumble into anyway. If the pool holds nothing
        // playable at all, the pair stands: the draft must still hand back two heroes.
        List<UnitDefinition> pair = new ArrayList<>(drawTwo(pool));
        for (int i = 0; i < pool.size(); i++) {
            if (!refused.test(pool.get(i))) {
                pool.add(pair.remove(1));
                pair.add(pool.remove(i));
                Collections.shuffle(pair, random);
                break;
            }
        }
        return List.copyOf(pair);
    }

    /** The guaranteed hero plus one ordinary draw, shuffled so the favourite isn't always listed first. */
    private static List<UnitDefinition> pairWithFavourite(UnitDefinition favourite, List<UnitDefinition> pool,
                                                           Random random) {
        if (pool.isEmpty()) {
            throw new IllegalStateException("Not enough units left in the pool to offer a pick");
        }
        List<UnitDefinition> pair = new ArrayList<>(List.of(favourite, pool.remove(0)));
        Collections.shuffle(pair, random);
        return List.copyOf(pair);
    }


    private static void runOnePlayerSetup(GameState state, Player player, List<List<UnitDefinition>> rounds,
                                           List<List<UnitDefinition>> opponentRounds, ConcurrentSetupHandler handler) {
        for (int i = 0; i < rounds.size(); i++) {
            UnitDefinition pick = handler.choosePick(state, player, ROUND_LABELS.get(i), rounds.get(i), opponentRounds.get(i));
            player.addUnit(UnitFactory.createFromDefinition(pick, player.getTeam(), state.getAbilityDefinitions()));
        }
        for (int i = 1; i <= BASIC_COUNT; i++) {
            player.addUnit(UnitFactory.createBasic(player.getName() + " Basic " + i, player.getTeam()));
        }

        Map<Unit, Position> defaultArrangement = DefaultArrangement.compute(state, player);
        Map<Unit, Position> finalArrangement = handler.arrangePlacement(state, player, defaultArrangement);

        synchronized (state.getMap()) {
            for (Map.Entry<Unit, Position> entry : finalArrangement.entrySet()) {
                Tile tile = state.getMap().getTile(entry.getValue());
                if (tile == null) {
                    throw new IllegalStateException("Confirmed placement references an off-map position: " + entry.getValue());
                }
                state.getMap().moveUnit(entry.getKey(), tile);
            }
        }
    }

    private static Team otherTeam(GameState state, Team team) {
        for (Player player : state.getPlayers()) {
            if (player.getTeam() != team) {
                return player.getTeam();
            }
        }
        throw new IllegalStateException("No opponent team found for " + team);
    }

    private static List<UnitDefinition> drawTwo(List<UnitDefinition> pool) {
        if (pool.size() < 2) {
            throw new IllegalStateException("Not enough units left in the pool to offer a pick");
        }
        return List.of(pool.remove(0), pool.remove(0));
    }

    private static List<UnitDefinition> shuffled(List<String> ids, Map<String, UnitDefinition> unitDefs, Random random) {
        List<UnitDefinition> defs = new ArrayList<>(ids.stream().map(unitDefs::get).toList());
        Collections.shuffle(defs, random);
        return defs;
    }
}
