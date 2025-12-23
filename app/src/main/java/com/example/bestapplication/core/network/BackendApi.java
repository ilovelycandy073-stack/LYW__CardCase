package com.example.bestapplication.core.network;

import com.example.bestapplication.core.network.dto.IdCardOcrResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface BackendApi {
    @Multipart
    @POST("/v1/ocr/idcard")
    Call<IdCardOcrResponse> idCardOcr(
            @Header("Authorization") String bearerToken,
            @Part MultipartBody.Part front,
            @Part MultipartBody.Part back,
            @Part("options") RequestBody optionsJson
    );
}
