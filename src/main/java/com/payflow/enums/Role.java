package com.payflow.enums;

/**
 * Authorization roles recognised by PayFlow. Roles are sourced from Keycloak realm roles
 * and mapped to Spring Security authorities with the {@code ROLE_} prefix.
 */
public enum Role {

    /** Standard wallet owner: may manage their own profile and initiate transfers. */
    USER,

    /** Privileged operator: may list/search all users and read any transaction history. */
    ADMIN
}
