package com.kotlinapp.auth.data

data class ResponseEntity(
        var email: String,
        var username: String,
        //Generated user id
        var id: Int
)