package org.cordacodeclub.bluff.db

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

/**
 * A generic database service superclass for handling for database update.
 * From https://github.com/corda/samples/blob/release-V3/flow-db/src/main/kotlin/com/flowdb/DatabaseService.kt
 *
 * @param services The node's service hub.
 */
@CordaService
open class DatabaseService(private val services: ServiceHub) : SingletonSerializeAsToken() {

    companion object {
        val log = loggerFor<DatabaseService>()
    }

    /**
     * Executes a database update.
     *
     * @param query The query string with blanks for the parameters.
     * @param params The parameters to fill the blanks in the query string.
     */
    protected fun executeUpdate(query: String, params: Map<Int, Any>): Int {
        val preparedStatement = prepareStatement(query, params)

        try {
            return preparedStatement.executeUpdate()
        } catch (e: SQLException) {
            log.error(e.message)
            throw e
        } finally {
            preparedStatement.close()
        }
    }

    /**
     * Executes a database query.
     *
     * @param query The query string with blanks for the parameters.
     * @param params The parameters to fill the blanks in the query string.
     * @param transformer A function for processing the query's ResultSet.
     *
     * @return The list of transformed query results.
     */
    protected fun <T : Any> executeQuery(
            query: String,
            params: Map<Int, Any>,
            transformer: (ResultSet) -> T
    ): List<T> {
        val preparedStatement = prepareStatement(query, params)
        val results = mutableListOf<T>()

        return try {
            val resultSet = preparedStatement.executeQuery()
            while (resultSet.next()) {
                results.add(transformer(resultSet))
            }
            results
        } catch (e: SQLException) {
            log.error(e.message)
            throw e
        } finally {
            preparedStatement.close()
        }
    }

    /**
     * Creates a PreparedStatement - a precompiled SQL statement to be
     * executed against the database.
     *
     * @param query The query string with blanks for the parameters.
     * @param params The parameters to fill the blanks in the query string.
     *
     * @return The query string and params compiled into a PreparedStatement
     */
    private fun prepareStatement(query: String, params: Map<Int, Any>): PreparedStatement {
        val session = services.jdbcSession()
        val preparedStatement = session.prepareStatement(query)

        params.forEach { (key, value) ->
            when (value) {
                is String -> preparedStatement.setString(key, value)
                is Int -> preparedStatement.setInt(key, value)
                is Long -> preparedStatement.setLong(key, value)
                is Boolean -> preparedStatement.setBoolean(key, value)
                is ByteArray -> preparedStatement.setBytes(key, value)
                else -> throw IllegalArgumentException("Unsupported type.")
            }
        }

        return preparedStatement
    }
}