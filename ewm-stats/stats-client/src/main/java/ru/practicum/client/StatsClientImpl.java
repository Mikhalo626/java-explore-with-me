
package ru.practicum.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.practicum.EndpointHit;
import ru.practicum.ViewStats;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StatsClientImpl implements StatsClient {

    private final RestTemplate restTemplate;

    @Value("${stats-server.url}")
    private String statsServerUrl;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void hit(EndpointHit hit) {
        URI uri = UriComponentsBuilder
                .fromUriString(statsServerUrl)
                .path("/hit")
                .build()
                .toUri();

        restTemplate.postForEntity(uri, hit, Void.class);
    }

    @Override
    public List<ViewStats> getStats(LocalDateTime start,
                                    LocalDateTime end,
                                    List<String> uris,
                                    boolean unique) {

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(statsServerUrl)
                .path("/stats")
                .queryParam("start", start.format(FORMATTER))
                .queryParam("end", end.format(FORMATTER))
                .queryParam("unique", unique);

        if (uris != null && !uris.isEmpty()) {
            for (String uri : uris) {
                builder.queryParam("uris", uri);
            }
        }

        URI requestUri = builder
                .encode()
                .build()
                .toUri();

        ResponseEntity<List<ViewStats>> response = restTemplate.exchange(
                requestUri,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {
                }
        );

        return response.getBody();
    }
}
