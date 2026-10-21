
package com.sptai.backend;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class OfficerAccessPolicy {

    private static final Set<String> AUTHORIZED_EMAILS = Set.of(
        "haranig650@gmail.com",
        "chaitanya@gmail.com"
    );

    public boolean isAuthorized(String email) {
        return email != null
                && AUTHORIZED_EMAILS.contains(
                    email.trim().toLowerCase(java.util.Locale.ROOT)
                );
    }
}
