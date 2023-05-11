package com.nb.oddsportalfeedchecker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostDetails {
    @JsonProperty("sport-name")
    private String sport;
    @JsonProperty("event-name")
    private String eventName;
    @JsonProperty("country-name")
    private String country;
    @JsonProperty("tournament-name")
    private String league;
    @JsonProperty("StartDateText")
    private String startDateText;
}
