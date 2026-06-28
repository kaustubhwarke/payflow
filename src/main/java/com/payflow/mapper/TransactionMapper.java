package com.payflow.mapper;

import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Maps {@link Transaction} entities to their API representation. Registered as a Spring bean
 * via the {@code spring} component model so it can be constructor-injected.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TransactionMapper {

    /**
     * @param transaction the source entity
     * @return the API response representation
     */
    TransactionResponse toResponse(Transaction transaction);
}
