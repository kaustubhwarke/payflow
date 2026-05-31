package com.payflow.mapper;

import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Maps {@link User} entities to their API representation. MapStruct generates the
 * implementation at compile time (no reflection), keeping mapping logic out of the service
 * layer (Separation of Concerns). Registered as a Spring bean via the {@code spring}
 * component model so it can be constructor-injected.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    /**
     * @param user the source entity (the {@code createdAt} field is inherited from
     *             {@code BaseEntity})
     * @return the API response representation
     */
    UserResponse toResponse(User user);
}
