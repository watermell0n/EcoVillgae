package com.b201.ecovillage.store.api;

import java.util.List;

public record StoreCatalogResponse(
    List<StoreCatalogProductResponse> products
) {
}
