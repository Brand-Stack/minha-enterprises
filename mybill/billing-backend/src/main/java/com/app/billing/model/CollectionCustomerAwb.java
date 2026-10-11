package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * AWB numbers registered to a {@link CollectionCustomer}. PENDING until linked to a
 * {@link CollectionCenterEntry}, then USED. {@code awbNo} is globally unique across registry + entries.
 */
@Document(collection = "collection_customer_awbs")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CollectionCustomerAwb extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_USED = "USED";

    @Indexed
    private String collectionCustomerId;

    /** Normalized AWB (same rules as collection entry). Unique across the whole registry. */
    @Indexed(unique = true)
    private String awbNo;

    private String status;

    /** Set when status becomes USED. */
    private String collectionCenterEntryId;
}
