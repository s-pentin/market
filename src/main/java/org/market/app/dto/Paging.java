package org.market.app.dto;

import lombok.Data;

@Data
public class Paging {
    int pageSize;
    int pageNumber;
    boolean hasPrevious;
    boolean hasNext;
}
