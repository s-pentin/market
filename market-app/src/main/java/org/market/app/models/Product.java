package org.market.app.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("product")
public class Product {

    @Id
    private Long id;

    @NotBlank
    @Size(min = 3, max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    private String imgPath;

    @NotNull
    @Positive
    private BigDecimal price;
}
