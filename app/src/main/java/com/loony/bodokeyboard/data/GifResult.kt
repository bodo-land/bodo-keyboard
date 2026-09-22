package com.loony.bodokeyboard.data

import kotlinx.serialization.Serializable

@Serializable
data class GifResult(
    val id: String,
    val url: String,
    val previewUrl: String
)

@Serializable
data class GiphyResponse(
    val data: List<GiphyData>
)

@Serializable
data class GiphyData(
    val id: String,
    val images: GiphyImages
)

@Serializable
data class GiphyImages(
    val fixed_height: GiphyImage,
    val preview_gif: GiphyImage
)

@Serializable
data class GiphyImage(
    val url: String
)
