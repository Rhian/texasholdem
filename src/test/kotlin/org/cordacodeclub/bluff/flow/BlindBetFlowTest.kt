package org.cordacodeclub.bluff.flow

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.toMultiMap
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.cordacodeclub.bluff.state.TokenState
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Add -javaagent:./lib/quasar.jar to VM Options
 */
class BlindBetFlowTest {
    private lateinit var network: MockNetwork
    private lateinit var minterNode: StartedMockNode
    private lateinit var dealerNode: StartedMockNode
    private lateinit var player1Node: StartedMockNode
    private lateinit var player2Node: StartedMockNode
    private lateinit var player3Node: StartedMockNode
    private lateinit var player4Node: StartedMockNode
    private lateinit var minter: Party
    private lateinit var dealer: Party
    private lateinit var player1: Party
    private lateinit var player2: Party
    private lateinit var player3: Party
    private lateinit var player4: Party
    private lateinit var mintTx: SignedTransaction

    @Before
    fun setup() {
        network = MockNetwork(
            listOf(
                "org.cordacodeclub.bluff.contract",
                "org.cordacodeclub.bluff.flow",
                "org.cordacodeclub.bluff.state"
            )
        )
        minterNode = network.createPartyNode(CordaX500Name.parse("O=Minter, L=London, C=GB"))
        dealerNode = network.createPartyNode(CordaX500Name.parse("O=Dealer, L=London, C=GB"))
        player1Node = network.createPartyNode(CordaX500Name.parse("O=Player1, L=London, C=GB"))
        player2Node = network.createPartyNode(CordaX500Name.parse("O=Player2, L=London, C=GB"))
        player3Node = network.createPartyNode(CordaX500Name.parse("O=Player3, L=London, C=GB"))
        player4Node = network.createPartyNode(CordaX500Name.parse("O=Player4, L=London, C=GB"))
        minter = minterNode.info.singleIdentity()
        dealer = dealerNode.info.singleIdentity()
        player1 = player1Node.info.singleIdentity()
        player2 = player2Node.info.singleIdentity()
        player3 = player3Node.info.singleIdentity()
        player4 = player4Node.info.singleIdentity()

        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(minterNode, player1Node, player2Node, player3Node, player4Node).forEach {
            it.registerInitiatedFlow(MintTokenFlow.Recipient::class.java)
            it.registerInitiatedFlow(BlindBetFlow.CollectorAndSigner::class.java)
        }
        val mintFlow = MintTokenFlow.Minter(listOf(player1, player2, player3, player4), 100)
        val future = minterNode.startFlow(mintFlow)
        network.runNetwork()
        mintTx = future.getOrThrow()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `SignedTransaction is signed by 2 blind bet players not the other players`() {
        val flow = BlindBetFlow.Initiator(
            listOf(player1, player2, player3, player4),
            minter,
            4
        )
        // TODO replace minter with dealer when states are properly exchanged
        val future = minterNode.startFlow(flow)
        network.runNetwork()

        println(listOf(minter.owningKey, dealer.owningKey, player1.owningKey, player2.owningKey, player3.owningKey,
            player4.owningKey))

        val signedTx = future.getOrThrow()
        signedTx.sigs.map { it.by }.toSet().also {
            assertTrue(it.contains(player1.owningKey))
            assertTrue(it.contains(player2.owningKey))
            assertFalse(it.contains(player3.owningKey))
            assertFalse(it.contains(player4.owningKey))
        }
    }

    @Test
    fun `SignedTransaction has inputs from first 2 players only`() {
        val flow = BlindBetFlow.Initiator(
            listOf(player1, player2, player3, player4),
            minter,
            4
        )
        // TODO replace minter with dealer when states are properly exchanged
        val future = minterNode.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        val inputs = signedTx.tx.inputs.map {
            minterNode.services.toStateAndRef<TokenState>(it).state.data
        }.map {
            assertEquals(minter, it.minter)
            it.owner to it
        }.toMultiMap()
        assertEquals(setOf(player1, player2), inputs.keys)
        assertEquals(4, inputs[player1]!!.map { it.amount }.sum())
        assertEquals(8, inputs[player2]!!.map { it.amount }.sum())
    }

    @Test
    fun `SignedTransaction is received by all players`() {
        val flow = BlindBetFlow.Initiator(
            listOf(player1, player2, player3, player4),
            minter,
            4
        )
        val future = minterNode.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        for (node in listOf(player1Node, player2Node, player3Node, player4Node)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }
}