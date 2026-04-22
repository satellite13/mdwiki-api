package com.mdwiki.rag

import com.mdwiki.config.EmbeddingProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class EmbeddingProviderFactory {

    @Bean
    @ConditionalOnMissingBean
    fun webClientBuilder(embeddingProperties: EmbeddingProperties): WebClient.Builder {
        val strategies = ExchangeStrategies.builder()
            .codecs { it.defaultCodecs().maxInMemorySize(embeddingProperties.maxResponseBufferBytes) }
            .build()
        return WebClient.builder().exchangeStrategies(strategies)
    }

    @Bean
    fun switchableEmbeddingProvider(
        properties: EmbeddingProperties,
        embeddingProviderBuilder: EmbeddingProviderBuilder
    ): SwitchableEmbeddingProvider {
        val initialModel = embeddingProviderBuilder.defaultModelFor(properties.provider)
        val initialProvider = embeddingProviderBuilder.create(properties.provider, initialModel)
        return SwitchableEmbeddingProvider(initialProvider)
    }

    @Bean
    fun embeddingProvider(switchableEmbeddingProvider: SwitchableEmbeddingProvider): EmbeddingProvider {
        return switchableEmbeddingProvider
    }
}
