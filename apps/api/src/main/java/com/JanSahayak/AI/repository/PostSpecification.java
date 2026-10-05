package com.JanSahayak.AI.repository;

import com.JanSahayak.AI.model.Post;
import com.JanSahayak.AI.enums.PostStatus;
import com.JanSahayak.AI.model.Role;
import com.JanSahayak.AI.model.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.ArrayList;

public class PostSpecification {

    public static Specification<Post> hasWildcardHashtags(List<String> patterns, String roleName) {
        return (root, query, criteriaBuilder) -> {

            // This list will hold all our individual LIKE conditions
            List<Predicate> predicates = new ArrayList<>();

            // Create a LIKE predicate for each pattern in the list and add it
            for (String pattern : patterns) {
                String sanitized = com.JanSahayak.AI.payload.PostUtility.sanitizeSqlLike(pattern);
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("content")), sanitized.toLowerCase(), '\\'));
            }

            // Combine all the LIKE predicates with OR
            Predicate hashtagsPredicate = criteriaBuilder.or(predicates.toArray(new Predicate[0]));

            // Create predicates for the other fixed conditions
            Predicate statusPredicate = criteriaBuilder.equal(root.get("status"), PostStatus.ACTIVE);
            Predicate rolePredicate = criteriaBuilder.equal(root.get("user").get("role").get("name"), roleName);
            Predicate userActivePredicate = criteriaBuilder.isTrue(root.get("user").get("isActive"));

            // Add explicit ordering by createdAt DESC to show recently posted posts first
            query.orderBy(criteriaBuilder.desc(root.get("createdAt")));

            // Combine all conditions with AND
            return criteriaBuilder.and(statusPredicate, rolePredicate, userActivePredicate, hashtagsPredicate);
        };
    }
    public static Specification<Post> hasWildcardHashtags(List<String> patterns, List<String> roleNames) {
        return (root, query, criteriaBuilder) -> {
            // Join Post -> User -> Role to filter by role name
            Join<Post, User> userJoin = root.join("user");
            Join<User, Role> roleJoin = userJoin.join("role");

            // Create a predicate for the role names using an "IN" clause
            // This will generate a query like: WHERE role.name IN ('ROLE_USER', 'ROLE_DEPARTMENT')
            Predicate rolePredicate = roleJoin.get("name").in(roleNames);

            // Create a list of "LIKE" predicates for each hashtag pattern
            List<Predicate> hashtagPredicates = new ArrayList<>();
            for (String pattern : patterns) {
                // This generates a query like: WHERE post.content LIKE '%#some_pattern%'
                String sanitized = com.JanSahayak.AI.payload.PostUtility.sanitizeSqlLike(pattern);
                hashtagPredicates.add(criteriaBuilder.like(root.get("content"), "%" + sanitized + "%", '\\'));
            }

            // Combine the hashtag predicates with "OR"
            // This means the post must match AT LEAST ONE of the patterns
            Predicate combinedHashtagPredicate = criteriaBuilder.or(hashtagPredicates.toArray(new Predicate[0]));

            // Combine the role predicate and the hashtag predicate with "AND"
            // The post must match the roles AND one of the hashtags
            return criteriaBuilder.and(rolePredicate, combinedHashtagPredicate);
        };
    }

    /**
     * Specification to check if post is a broadcast post
     */
    public static Specification<Post> isBroadcastPost() {
        return (root, query, cb) -> cb.isNotNull(root.get("broadcastScope"));
    }

    /**
     * Specification to check if post has active status
     */
    public static Specification<Post> isActiveStatus() {
        return (root, query, cb) -> cb.equal(root.get("status"), PostStatus.ACTIVE);
    }

    /**
     * Specification to find posts with emergency hashtags
     * Searches in post content for emergency-related hashtags and keywords
     */
    public static Specification<Post> hasEmergencyHashtags(List<String> patterns) {
        return (root, query, cb) -> {
            if (patterns == null || patterns.isEmpty()) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            // Create LIKE conditions for each pattern
            for (String pattern : patterns) {
                String sanitized = com.JanSahayak.AI.payload.PostUtility.sanitizeSqlLike(pattern);
                predicates.add(cb.like(cb.lower(root.get("content")), sanitized.toLowerCase(), '\\'));
            }

            // OR all patterns together
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
