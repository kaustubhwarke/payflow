package com.payflow.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a Keycloak access token into Spring Security authorities.
 *
 * <p>Keycloak emits realm roles under {@code realm_access.roles} and OAuth2 scopes under
 * {@code scope}. This converter maps realm roles to {@code ROLE_*} authorities and scopes to
 * {@code SCOPE_*} authorities, so controllers can use either {@code hasRole(...)} or
 * {@code hasAuthority('SCOPE_...')}.</p>
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String SCOPE = "scope";

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Stream<String> realmRoles = Stream.empty();
        Object realmAccess = jwt.getClaim(REALM_ACCESS);
        if (realmAccess instanceof Map<?, ?> map && map.get(ROLES) instanceof Collection<?> roles) {
            realmRoles = ((Collection<String>) roles).stream().map(role -> "ROLE_" + role);
        }

        Stream<String> scopes = Stream.empty();
        String scopeClaim = jwt.getClaimAsString(SCOPE);
        if (scopeClaim != null && !scopeClaim.isBlank()) {
            scopes = Stream.of(scopeClaim.split(" ")).map(scope -> "SCOPE_" + scope);
        }

        return Stream.concat(realmRoles, scopes)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /** @return the list of authority strings (test-friendly variant of {@link #convert(Jwt)}). */
    public List<String> toAuthorityStrings(Jwt jwt) {
        return convert(jwt).stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());
    }
}
