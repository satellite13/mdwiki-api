package com.mdwiki.service

import com.fasterxml.jackson.databind.JsonNode

/** Converts Jackson tree nodes into plain JSON-compatible values for HTTP responses. */
object PropertyJsonValues {
    fun toWire(node: JsonNode?): Any? = when {
        node == null || node.isNull || node.isMissingNode -> null
        node.isBoolean -> node.booleanValue()
        node.isNumber -> if (node.isIntegralNumber) node.longValue() else node.decimalValue()
        node.isTextual -> node.asText()
        node.isArray -> node.map { toWire(it) }
        node.isObject -> node.fields().asSequence().associate { it.key to toWire(it.value) }
        else -> node.asText()
    }
}
