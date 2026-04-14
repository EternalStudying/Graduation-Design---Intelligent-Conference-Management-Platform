package com.llf.auth;

public class AuthContext {
    private static final ThreadLocal<AuthUser> TL = new ThreadLocal<>();

    public static void set(AuthUser u) { TL.set(u); }
    public static AuthUser get() { return TL.get(); }
    public static void clear() { TL.remove(); }
}