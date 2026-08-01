package org.market.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.market.app.models.SortType;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductsPage {
    List<List<ItemDto>> items;
    Paging paging;
    String search;
    SortType sort;
}