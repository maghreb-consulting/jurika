package fr.maghreb.gje.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaStatusDTO {
    private BigDecimal used;
    private BigDecimal limit;
    private Double percentage;
    private Boolean isNear90Percent;
}
