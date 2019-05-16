package org.cordacodeclub.bluff.flow

import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.internal.toMultiMap
import net.corda.core.serialization.CordaSerializable
import org.cordacodeclub.bluff.state.ActivePlayer
import org.cordacodeclub.bluff.state.TokenState
import org.cordacodeclub.grom356.Card

@CordaSerializable
data class CallOrRaiseRequest(val minter: Party, val lastRaise: Long, val yourWager: Long, val yourCards: List<Card>) {
    init {
        requireThat {
            "There can be only 2 cards" using (yourCards.size == CreateGameFlow.GameCreator.PLAYER_CARD_COUNT)
            "Your wager cannot be higher than the last raise" using (yourWager <= lastRaise)
        }
    }
}

@CordaSerializable
class CallOrRaiseResponse {
    val isFold: Boolean
    val moreBets: List<StateAndRef<TokenState>>

    // Fold constructor
    constructor() {
        isFold = true
        moreBets = listOf()
    }

    // Call or raise constructor
    constructor(states: List<StateAndRef<TokenState>>) {
        requireThat {
            "All amounts must be strictly positive" using
                    (states.fold(true) { isPos, state ->
                        isPos && state.state.data.amount > 0
                    })
        }
        isFold = false
        moreBets = states
    }
}

// This object is passed around after each player has acted
class RoundTableAccumulator(
    val minter: Party,
    // Keeps track of which player has folded
    val players: List<ActivePlayer>,
    // The player that has to play now
    val currentPlayerIndex: Int,
    // They come from a previous transaction
    val committedPotSums: Map<Party, Long>,
    // They are being added to after each player
    val newBets: Map<Party, List<StateAndRef<TokenState>>>,
    val lastRaiseIndex: Int,
    // When the previous player raised, this gets reset to 0
    val playerCountSinceLastRaise: Int
) {
    init {
        requireThat {
            "Current player must be active" using (!players[currentPlayerIndex].folded)
            "All bettors must be in the list of players"
                .using(
                    players.map { it.party }.containsAll(committedPotSums.keys.plus(newBets.keys))
                )
            committedPotSums.forEach {
                "Pot sum for ${it.key} must be positive" using (it.value >= 0)
            }
            "We need at least existing pot sums" using (committedPotSums.values.sum() > 0)
            "lastRaiseIndex must be positive, not $lastRaiseIndex" using (lastRaiseIndex >= 0)
            "playerCountSinceLastRaise myst be positive, not $playerCountSinceLastRaise"
                .using(playerCountSinceLastRaise >= 0)
        }
    }

    val currentPlayer = players[currentPlayerIndex].party
    val currentPlayerSum = (newBets[currentPlayer] ?: listOf())
        .map { it.state.data.amount }
        .sum() + (committedPotSums[currentPlayer] ?: 0)
    val currentLevel = newBets.map { entry ->
        entry.key to entry.value.map { it.state.data.amount }.sum()
    }.plus(committedPotSums.toList())
        .toMultiMap()
        .mapValues { it.value.sum() }
        .values
        .max()!!
    val nextActivePlayerIndex = currentPlayerIndex.let {
        var i = it
        do i = (i + 1) % players.size
        while (players[i].folded)
        i
    }
    val activePlayerCount = players.filter { !it.folded }.size
    val isRoundDone = activePlayerCount == 1 || activePlayerCount == playerCountSinceLastRaise

    fun stepForwardWhenCurrentPlayerSent(response: CallOrRaiseResponse): RoundTableAccumulator {
        requireThat {
            "We cannot move forward if the round is done" using (!isRoundDone)
        }
        // We should have received from the expected minter
        val isCorrectMinter = response.moreBets.map { it.state.data.minter }.toSet().let { minters ->
            minters.size <= 1 && minters.singleOrNull().let { it == null || it == minter }
        }
        // We should have received from the expected player
        val isCorrectOwner = response.moreBets.map { it.state.data.owner }.toSet().let { owners ->
            owners.size <= 1 && owners.singleOrNull().let { it == null || it == currentPlayer }
        }
        // We should have received enough, if not folded
        val newSum = currentPlayerSum + response.moreBets.map { it.state.data.amount }.sum()
        val isAtLeastCall = newSum >= currentLevel
        val isRaise = newSum > currentLevel

        // We punish with a fold a player that sent wrong info
        val isFolded = response.isFold || !isCorrectMinter || !isCorrectOwner || !isAtLeastCall
        val updatedPlayers = players.mapIndexed { index, player ->
            if (index != currentPlayerIndex) player
            else player.copy(folded = isFolded)
        }

        val updatedNewBets =
            if (isFolded) newBets
            else newBets.toList().plus(currentPlayer to response.moreBets)
                .toMultiMap()
                .mapValues { it.value.flatten() }

        val updatedLastRaiseIndex = if (isRaise) currentPlayerIndex else lastRaiseIndex
        val updatedPlayerCountSinceLastRaise =
            if (isRaise) 0
            else if (isFolded) playerCountSinceLastRaise
            else playerCountSinceLastRaise + 1

        return RoundTableAccumulator(
            minter = minter,
            players = updatedPlayers,
            currentPlayerIndex = nextActivePlayerIndex,
            committedPotSums = committedPotSums,
            newBets = updatedNewBets,
            lastRaiseIndex = updatedLastRaiseIndex,
            playerCountSinceLastRaise = updatedPlayerCountSinceLastRaise
        )
    }
}