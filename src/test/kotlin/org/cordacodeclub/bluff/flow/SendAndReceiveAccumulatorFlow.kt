package org.cordacodeclub.bluff.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import org.cordacodeclub.bluff.dealer.CardDeckInfo
import org.cordacodeclub.bluff.state.TokenState

object SendAndReceiveAccumulatorFlow {

    @CordaSerializable
    data class DesiredAction(val action: Action, val raiseBy: Long)

    @InitiatingFlow
    class Initiator(
        val deckInfo: CardDeckInfo,
        val players: List<Party>,
        val accumulator: RoundTableAccumulator,
        val responderActions: Map<Party, List<DesiredAction>> = mapOf()
    ) : FlowLogic<RoundTableAccumulator>() {

        @Suspendable
        override fun call(): RoundTableAccumulator {
            val playerFlows = players.map { player ->
                initiateFlow(player).also {
                    it.send(responderActions[player] ?: listOf(DesiredAction(Action.Fold, 0L)))
                }
            }
            return subFlow(
                RoundTableAccumulatorFlow(
                    deckInfo = deckInfo,
                    playerFlows = playerFlows,
                    accumulator = accumulator
                )
            )
        }
    }

    @InitiatedBy(Initiator::class)
    class FoldResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val desiredActions = otherPartySession.receive<List<DesiredAction>>().unwrap { it }
            desiredActions.forEach { desiredAction ->
                val request = otherPartySession.receive<CallOrRaiseRequest>().unwrap { it }
                val desiredAmount = desiredAction.raiseBy + request.lastRaise - request.yourWager
                val response = when (desiredAction.action) {
                    Action.Fold -> CallOrRaiseResponse()
                    Action.Call, Action.Raise -> desiredAmount.let { amount ->
                        if (amount == 0L) CallOrRaiseResponse(listOf(), serviceHub)
                        else CallOrRaiseResponse(
                            subFlow(
                                TokenStateCollectorFlow(
                                    TokenState(
                                        minter = request.minter,
                                        owner = serviceHub.myInfo.legalIdentities.first(),
                                        amount = amount,
                                        isPot = false
                                    )
                                )
                            ),
                            serviceHub
                        )
                    }
                }
                otherPartySession.send(response)
            }
        }
    }
}