package com.dauducbach.feed_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Document(indexName = "profile_aggregate")
public class ProfileIndex {
    String id;
    String displayName;
    String city;
    String job;
    List<String> followingList;
    List<String> followerList;
    List<String> followerRequestList;
    List<String> followingRequestList;
    List<String> blockList;
    String sex;
    String bio;
}
