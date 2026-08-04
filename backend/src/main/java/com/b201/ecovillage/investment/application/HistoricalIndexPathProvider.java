package com.b201.ecovillage.investment.application;

import java.util.Optional;

public interface HistoricalIndexPathProvider {
    Optional<HistoricalIndexPath> load(int requiredSteps);
}
