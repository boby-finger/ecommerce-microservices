package com.innowise.userservice.spec;

import com.innowise.userservice.model.User;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {
    private UserSpecifications() {};

    public static Specification<User> searchUserByName(String name){
        return ((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("name"), name));
    }

    public static Specification<User> searchUserBySurname(String surname){
        return ((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("surname"), surname));
    }
}
