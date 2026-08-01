package org.market.app.dto;

import lombok.Builder;
import lombok.Data;
import org.market.app.models.SortType;

import java.util.List;

@Data
@Builder
public class ProductsPage {
    List<List<ItemDto>> items;
    Paging paging;
    String search;
    SortType sort;
}