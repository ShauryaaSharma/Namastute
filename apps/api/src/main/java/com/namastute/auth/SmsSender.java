package com.namastute.auth;

// Anything that can deliver an SMS. Today only LogSmsSender; a real provider comes later.
interface SmsSender {
    void send(String phone, String text);
}