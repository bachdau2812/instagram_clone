package com.dauducbach.storage_service.constant;

import lombok.Getter;

@Getter
public enum MediaVariant {
    IMAGE_AVATAR_PROFILE("w_150,h_150,c_fill,g_faces,r_max,b_transparent"),
    IMAGE_AVATAR_FEED("w_32,h_32,c_fill,g_faces,r_max,b_transparent"),
    IMAGE_PROFILE("w_311,h_413,c_fit,g_center,b_transparent"),
    IMAGE_PROFILE_FOCUS("w_648,h_864,c_fit,g_center,b_transparent"),
    IMAGE_FEED("w_467,h_582,c_fit,g_center,b_transparent"),
    VIDEO_FEED("w_467,h_582,c_fit,ac_aac"),
    VIDEO_PROFILE("w_311,h_413,c_fit,ac_aac"),
    VIDEO_PROFILE_FOCUS("w_648,h_864,c_fit,ac_aac")

    ;

    private final String transformation;

    MediaVariant(String transformation) {
        this.transformation = transformation;
    }
}
