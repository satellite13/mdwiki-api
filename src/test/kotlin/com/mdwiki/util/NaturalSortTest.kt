package com.mdwiki.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NaturalSortTest {

    @Test
    fun `orders numbered chapters naturally`() {
        val titles = listOf(
            "Глава 10: Протокол контекста модели (MCP)",
            "Глава 9: Обучение и адаптация",
            "Глава 2: Маршрутизация",
        ).sortedWith(NaturalSort::compare)

        assertTrue(titles.indexOf("Глава 2: Маршрутизация") < titles.indexOf("Глава 9: Обучение и адаптация"))
        assertTrue(titles.indexOf("Глава 9: Обучение и адаптация") < titles.indexOf("Глава 10: Протокол контекста модели (MCP)"))
    }
}
