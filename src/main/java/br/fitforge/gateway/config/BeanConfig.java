package br.fitforge.gateway.config;

import br.fitforge.gateway.adapter.out.http.WebClientProgressoAdapter;
import br.fitforge.gateway.adapter.out.http.WebClientTreinosAdapter;
import br.fitforge.gateway.application.port.out.ProgressoClientPort;
import br.fitforge.gateway.application.port.out.TreinosClientPort;
import br.fitforge.gateway.application.service.BffService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/** Composição (Hexagonal): liga o núcleo do BFF aos adaptadores HTTP dos microsserviços. */
@Configuration
public class BeanConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:America/Sao_Paulo}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }

    @Bean
    public TreinosClientPort treinosClient(WebClient.Builder builder,
                                           @Value("${services.treinos.url}") String url,
                                           @Value("${services.timeout-seconds:15}") long timeout) {
        return new WebClientTreinosAdapter(builder.clone().baseUrl(url).build(), url, Duration.ofSeconds(timeout));
    }

    @Bean
    public ProgressoClientPort progressoClient(WebClient.Builder builder,
                                               @Value("${services.progresso.url}") String url,
                                               @Value("${services.timeout-seconds:15}") long timeout) {
        return new WebClientProgressoAdapter(builder.clone().baseUrl(url).build(), url, Duration.ofSeconds(timeout));
    }

    @Bean
    public BffService bffService(TreinosClientPort treinos, ProgressoClientPort progresso, Clock clock) {
        return new BffService(treinos, progresso, clock);
    }
}
