package com.app.billing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "holidays")
@CompoundIndex(name = "holiday_date_idx", def = "{'holidayDate': 1}", unique = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Holiday extends BaseEntity {

    @Indexed
    private LocalDate holidayDate;

    private String holidayName;

    /** Public Holiday, Company Holiday, Festival, National Holiday */
    private String holidayType;

    private String description;

    private Integer year;

    @Builder.Default
    private Boolean active = true;

    public String getName() {
        return holidayName;
    }

    public void setName(String name) {
        if (name != null) {
            this.holidayName = name;
        }
    }

    public String getType() {
        return holidayType;
    }

    public void setType(String type) {
        if (type != null) {
            this.holidayType = type;
        }
    }
}
