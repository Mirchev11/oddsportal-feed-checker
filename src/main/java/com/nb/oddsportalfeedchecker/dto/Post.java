package com.nb.oddsportalfeedchecker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class Post {

    @JsonProperty("FeedUID")
    private Long feedUID;
    @JsonProperty("OwnerUID")
    private Long ownerUID;
    @JsonProperty("SubjectUID")
    private Long subjectUID;
    @JsonProperty("Content")
    private String content;
    @JsonProperty("InsertTime")
    private Long insertTime;
    @JsonProperty("IsPublic")
    private Boolean isPublic;
    @JsonIgnore
    private PostDetails postDetails;

}
