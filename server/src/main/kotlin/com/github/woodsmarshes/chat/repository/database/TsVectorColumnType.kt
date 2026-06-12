package com.github.woodsmarshes.chat.repository.database

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject

private const val TS_VECTOR_SQL_TYPE = "tsvector"

class TsVectorColumnType : ColumnType<String>() {
    override fun sqlType(): String = TS_VECTOR_SQL_TYPE

    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value ?: ""
        is String -> value
        else -> value.toString()
    }

    override fun valueToDB(value: String?): Any? {
        if (value == null) return null
        return PGobject().apply {
            type = sqlType()
            this.value = value
        }
    }

    override fun notNullValueToDB(value: String): Any {
        return PGobject().apply {
            type = sqlType()
            this.value = value
        }
    }

    override fun nonNullValueToString(value: String): String {
        return "'$value'"
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue = valueToDB(value?.toString())
        super.setParameter(stmt, index, parameterValue)
    }
}