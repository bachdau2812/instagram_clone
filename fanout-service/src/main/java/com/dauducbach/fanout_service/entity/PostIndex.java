package com.dauducbach.fanout_service.entity;

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

@Document(indexName = "post_aggregate")
public class PostIndex {
    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String userId;

    @Field(type = FieldType.Text)
    String content;

    @Field(type = FieldType.Text)
    List<String> tags;

    @Field(type = FieldType.Text)
    List<String> likedId;

    @Field(type = FieldType.Text)
    List<String> commentedId;
    Long createAt;
    Long updateAt;

    List<Float> embedding;
}
