package com.dauducbach.fanout_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

@Document(indexName = "profile_aggregate")
public class ProfileIndex {
    @Id
    String id;

    @Field(type = FieldType.Text)
    String displayName;

    @Field(type = FieldType.Text)
    String city;

    @Field(type = FieldType.Text)
    String job;

    @Field(type = FieldType.Keyword)
    List<String> followingList;

    @Field(type = FieldType.Keyword)
    List<String> followerList;

    @Field(type = FieldType.Keyword)
    List<String> followerRequestList;

    @Field(type = FieldType.Keyword)
    List<String> followingRequestList;

    @Field(type = FieldType.Keyword)
    List<String> blockList;

    @Field(type = FieldType.Text)
    String sex;

    @Field(type = FieldType.Text)
    String bio;

    List<Float> embedding;
}
