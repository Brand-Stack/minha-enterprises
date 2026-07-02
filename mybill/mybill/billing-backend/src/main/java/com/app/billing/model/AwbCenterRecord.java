package com.app.billing.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "awb_center")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AwbCenterRecord extends BaseEntity {

    public static final String QUEUE_PENDING = "PENDING";
    public static final String QUEUE_COMPLETED = "COMPLETED";

    @Indexed(unique = true)
    private String awbNumber;

    /** PENDING or COMPLETED */
    @Indexed
    private String queueStatus;

    private LocalDateTime completedAt;

    /** CLIENT_ENTRY, COLLECTION_CENTER, CASH_BOOKING, SMALL_CLIENT_ENTRY */
    private String usedInModule;

    private String usedInReferenceId;

    /** When true, AWB is reserved for Collection Center (mapped collection customer). */
    private Boolean collectionCustomerAwb;

    /** Collection customer id when {@link #collectionCustomerAwb} is true. */
    private String collectionCustomerId;

    private String collectionCustomerName;

    /** Courier/shipment type from Company Settings (e.g. DHL, BLUE DART). */
    private String courierType;
}
