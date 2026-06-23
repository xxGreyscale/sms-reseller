package com.smsreseller.identity.admin;

import com.smsreseller.identity.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin user search service (ADMN-02).
 *
 * <p>Searches users by email or phone substring. No subject scoping — admin sees all users.
 * Returns {@link UserSummaryDto} which excludes the password hash (T-05-08 mitigated).
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    /**
     * Returns a page of {@link UserSummaryDto} matching the given query term.
     *
     * @param q        search term (email or phone substring); null or blank → return all users
     * @param pageable pagination parameters
     * @return paged user summaries
     */
    @Transactional(readOnly = true)
    public Page<UserSummaryDto> search(String q, Pageable pageable) {
        String term = (q == null || q.isBlank()) ? null : q.trim();
        return userRepository.searchByEmailOrPhone(term, pageable)
                .map(UserSummaryDto::from);
    }
}
