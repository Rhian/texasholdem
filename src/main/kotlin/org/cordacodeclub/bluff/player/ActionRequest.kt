package org.cordacodeclub.bluff.player

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import org.cordacodeclub.grom356.Card
import org.cordacodeclub.grom356.CardList

@CordaSerializable
data class ActionRequest(
        val id: Long,
        val player: CordaX500Name,
        val cards: List<Card>,
        val cardHashes: SecureHash,
        val youBet: Long,
        val lastRaise: Long,
        val playerAction: PlayerAction?,
        val addAmount: Long
) {
    constructor(
        id: Long,
        player: String,
        cards: String,
        cardHashes: ByteArray,
        youBet: Long,
        lastRaise: Long,
        playerAction: PlayerAction?,
        addAmount: Long
    ) : this(
        id = id,
        player = CordaX500Name.parse(player),
        cards = CardList.valueOf(cards),
        cardHashes = SecureHash.sha256(cardHashes),
        youBet = youBet,
        lastRaise = lastRaise,
        playerAction = playerAction,
        addAmount = addAmount
    )
}